package com.timeslot.reservation;

import com.timeslot.common.api.ApiResponse;
import com.timeslot.identity.dto.LoginResponse;
import com.timeslot.reservation.dto.ReservationResponse;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.IntFunction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * MySQL-gated integration tests for the reservation concurrency guarantees.
 * Runs only with TIMESLOT_IT_DB=true against the local seeded database
 * (sql/schema.sql + sql/data.sql + V1_2 migration); skipped elsewhere so
 * `mvn test` stays green without a database.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@EnabledIfEnvironmentVariable(named = "TIMESLOT_IT_DB", matches = "(?i)true|1|yes")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ReservationConcurrencyIT {
    private static final String SEED_PASSWORD = "123456";
    private static final String IT_REQUEST_PREFIX = "it-";
    private static final long ROOM_A301 = 1L;
    private static final long ROOM_A302 = 2L;
    private static final long ROOM_B502 = 5L;

    @Autowired
    TestRestTemplate http;
    @Autowired
    JdbcTemplate jdbc;

    private String zhangsanToken;
    private String lisiToken;

    @BeforeAll
    void login() {
        zhangsanToken = doLogin("zhangsan");
        lisiToken = doLogin("lisi");
    }

    @AfterAll
    void cleanup() {
        jdbc.update("DELETE FROM approval_record WHERE reservation_id IN "
                + "(SELECT id FROM reservation WHERE request_id LIKE ?)", IT_REQUEST_PREFIX + "%");
        jdbc.update("DELETE FROM reservation WHERE request_id LIKE ?", IT_REQUEST_PREFIX + "%");
    }

    private String doLogin(String username) {
        ResponseEntity<ApiResponse<LoginResponse>> response = http.exchange("/api/auth/login", HttpMethod.POST,
                jsonEntity(null, Map.of("username", username, "password", SEED_PASSWORD)),
                new ParameterizedTypeReference<>() {
                });
        assumeTrue(response.getStatusCode().is2xxSuccessful() && response.getBody() != null
                        && response.getBody().data() != null,
                "无法登录测试账号 " + username + "，本地种子数据缺失，跳过集成测试");
        return response.getBody().data().token();
    }

    /** First day within the advance window where every given room is free at hour..hour+1. */
    private LocalDateTime freeSlotForRoomsAtHour(List<Long> roomIds, int hour) {
        for (int dayOffset = 1; dayOffset <= 6; dayOffset++) {
            LocalDateTime start = LocalDate.now().plusDays(dayOffset).atTime(hour, 0);
            boolean allFree = roomIds.stream().noneMatch(roomId -> roomHasActiveBooking(roomId, start, start.plusHours(1)));
            if (allFree) return start;
        }
        throw new IllegalStateException("一周内找不到满足条件的空闲时段，请检查数据库状态");
    }

    private boolean roomHasActiveBooking(long roomId, LocalDateTime start, LocalDateTime end) {
        ResponseEntity<ApiResponse<List<ReservationResponse>>> calendar = http.exchange(
                "/api/reservations/calendar?start=" + start + "&end=" + end + "&roomId=" + roomId,
                HttpMethod.GET, jsonEntity(zhangsanToken, null),
                new ParameterizedTypeReference<>() {
                });
        assumeTrue(calendar.getStatusCode().is2xxSuccessful() && calendar.getBody() != null
                        && calendar.getBody().data() != null,
                "日历查询不可用: HTTP " + calendar.getStatusCode().value());
        return calendar.getBody().data().stream()
                .anyMatch(r -> "PENDING".equals(r.status()) || "CONFIRMED".equals(r.status()));
    }

    @Test
    @Order(1)
    void tenConcurrentRequestsOnSameRoomSameSlotYieldExactlyOneSuccess() throws Exception {
        LocalDateTime start = freeSlotForRoomsAtHour(List.of(ROOM_A301), 10);

        List<Result> results = submitConcurrently(10, i ->
                create(zhangsanToken, IT_REQUEST_PREFIX + "conc-" + UUID.randomUUID(), ROOM_A301, start, start.plusHours(1)));

        long created = results.stream().filter(Result::created).count();
        long conflicts = results.stream().filter(r -> "RESERVATION_TIME_CONFLICT".equals(r.code)).count();
        assertEquals(1, created, "10 个并发请求最多 1 个成功，实际 " + created + " 个: " + results);
        assertEquals(9, conflicts, "其余 9 个应全部返回 RESERVATION_TIME_CONFLICT: " + results);
    }

    @Test
    @Order(2)
    void sameRequestIdIsIsolatedPerUser() {
        LocalDateTime start = freeSlotForRoomsAtHour(List.of(ROOM_A301, ROOM_A302), 10);
        String requestId = IT_REQUEST_PREFIX + "idem-" + UUID.randomUUID();

        Result first = create(zhangsanToken, requestId, ROOM_A301, start, start.plusHours(1));
        Result secondSameUser = create(zhangsanToken, requestId, ROOM_A301, start, start.plusHours(1));
        Result otherUserSameRequestId = create(lisiToken, requestId, ROOM_A302, start, start.plusHours(1));

        assertTrue(first.created(), "首次创建应成功: " + first);
        assertEquals(first.id, secondSameUser.id, "同用户同 requestId 应幂等命中同一预约");
        assertTrue(otherUserSameRequestId.created(), "不同用户同 requestId 应可独立创建: " + otherUserSameRequestId);
        assertNotEquals(first.id, otherUserSameRequestId.id, "不同用户的预约不能串数据");
    }

    @Test
    @Order(3)
    void pendingReservationOccupiesTheTimeSlot() {
        LocalDateTime start = freeSlotForRoomsAtHour(List.of(ROOM_B502), 14);

        Result pending = create(zhangsanToken, IT_REQUEST_PREFIX + "pending-" + UUID.randomUUID(),
                ROOM_B502, start, start.plusHours(1));
        Result competing = create(lisiToken, IT_REQUEST_PREFIX + "pending-rival-" + UUID.randomUUID(),
                ROOM_B502, start, start.plusHours(1));

        assertEquals("PENDING", pending.status, "受控会议室应创建 PENDING 预约: " + pending);
        assertEquals("RESERVATION_TIME_CONFLICT", competing.code, "PENDING 必须参与冲突判定: " + competing);
    }

    @Test
    @Order(4)
    void rescheduleIsConflictSafe() {
        LocalDateTime slotA = freeSlotForRoomsAtHour(List.of(ROOM_A301), 10);
        LocalDateTime slotB = freeSlotForRoomsAtHour(List.of(ROOM_A301), 14);

        Result reservationA = create(zhangsanToken, IT_REQUEST_PREFIX + "rsvA-" + UUID.randomUUID(),
                ROOM_A301, slotA, slotA.plusHours(1));
        Result reservationB = create(zhangsanToken, IT_REQUEST_PREFIX + "rsvB-" + UUID.randomUUID(),
                ROOM_A301, slotB, slotB.plusHours(1));
        assertTrue(reservationA.created() && reservationB.created(), "两条预约都应创建成功");

        Result overlappingMove = update(zhangsanToken, reservationB.id, ROOM_A301,
                slotA.plusMinutes(30), slotA.plusMinutes(90), "改入冲突时段");
        assertEquals("RESERVATION_TIME_CONFLICT", overlappingMove.code, "改期撞上他人预约应被拒绝: " + overlappingMove);

        Result sameSlotMove = update(zhangsanToken, reservationA.id, ROOM_A301, slotA, slotA.plusHours(1), "原地改期");
        assertTrue(sameSlotMove.created(), "排除自身 id 后原地改期应成功: " + sameSlotMove);
    }

    @Test
    @Order(5)
    void concurrentRequestsOnDifferentRoomsAreIndependent() throws Exception {
        LocalDateTime start = freeSlotForRoomsAtHour(List.of(ROOM_A301, ROOM_A302), 16);

        List<Result> results = submitConcurrently(10, i -> {
            long roomId = i % 2 == 0 ? ROOM_A301 : ROOM_A302;
            return create(zhangsanToken, IT_REQUEST_PREFIX + "rooms-" + UUID.randomUUID(),
                    roomId, start, start.plusHours(1));
        });

        long room1Created = results.stream().filter(r -> r.created() && r.roomId == ROOM_A301).count();
        long room2Created = results.stream().filter(r -> r.created() && r.roomId == ROOM_A302).count();
        assertEquals(1, room1Created, "A301 应恰好 1 个成功: " + results);
        assertEquals(1, room2Created, "A302 不受 A301 行锁影响，应恰好 1 个成功: " + results);
    }

    private List<Result> submitConcurrently(int threads, IntFunction<Result> task) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch gate = new CountDownLatch(1);
        try {
            List<Future<Result>> futures = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                int index = i;
                futures.add(pool.submit((Callable<Result>) () -> {
                    gate.await();
                    return task.apply(index);
                }));
            }
            gate.countDown();
            List<Result> results = new ArrayList<>();
            for (Future<Result> future : futures) {
                results.add(future.get(30, TimeUnit.SECONDS));
            }
            return results;
        } finally {
            pool.shutdownNow();
        }
    }

    private Result create(String token, String requestId, long roomId, LocalDateTime start, LocalDateTime end) {
        ResponseEntity<ApiResponse<ReservationResponse>> response = http.exchange("/api/reservations", HttpMethod.POST,
                jsonEntity(token, Map.of("requestId", requestId, "roomId", roomId, "title", "并发集成测试",
                        "startTime", start.toString(), "endTime", end.toString(),
                        "participantCount", 2, "remark", "ReservationConcurrencyIT")),
                new ParameterizedTypeReference<>() {
                });
        return Result.of(response, roomId);
    }

    private Result update(String token, long id, long roomId, LocalDateTime start, LocalDateTime end, String title) {
        ResponseEntity<ApiResponse<ReservationResponse>> response = http.exchange("/api/reservations/" + id, HttpMethod.PUT,
                jsonEntity(token, Map.of("roomId", roomId, "title", title,
                        "startTime", start.toString(), "endTime", end.toString(),
                        "participantCount", 2, "remark", "ReservationConcurrencyIT")),
                new ParameterizedTypeReference<>() {
                });
        return Result.of(response, roomId);
    }

    private static HttpEntity<Object> jsonEntity(String token, Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (token != null) headers.setBearerAuth(token);
        return new HttpEntity<>(body, headers);
    }

    private record Result(Long id, long roomId, String status, String code, boolean created) {
        static Result of(ResponseEntity<ApiResponse<ReservationResponse>> response, long roomId) {
            ApiResponse<ReservationResponse> body = response.getBody();
            boolean created = response.getStatusCode().is2xxSuccessful() && body != null && body.data() != null;
            String code = created ? "SUCCESS"
                    : (body != null && body.code() != null ? body.code() : "HTTP_" + response.getStatusCode().value());
            return new Result(created ? body.data().id() : null, roomId,
                    created ? body.data().status() : null, code, created);
        }

        @Override
        public String toString() {
            return "Result{id=" + id + ", room=" + roomId + ", status=" + status + ", code=" + code + "}";
        }
    }
}
