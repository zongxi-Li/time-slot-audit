/**
 * 文件职责：验证预约状态迁移的数据库持久化一致性（Service Response 与 DB 状态必须相等）。
 * 接口：使用 JUnit、Spring 测试工具与本地 MySQL，不属于运行时接口。
 */
package com.timeslot.reservation;

import com.timeslot.common.api.ApiResponse;
import com.timeslot.identity.dto.LoginResponse;
import com.timeslot.reservation.dto.ReservationResponse;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
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
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * MySQL-gated persistence-consistency tests for reservation lifecycle transitions.
 * These tests exist because Mockito unit tests cannot detect "Java object updated
 * but the database was not": every case writes through the real Service + MyBatis
 * stack, then re-SELECTs the row and asserts on the database state itself.
 * Runs only with TIMESLOT_IT_DB=true against the local seeded database.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@EnabledIfEnvironmentVariable(named = "TIMESLOT_IT_DB", matches = "(?i)true|1|yes")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ReservationPersistenceIT {
    private static final String SEED_PASSWORD = "123456";
    private static final String IT_REQUEST_PREFIX = "it-persist-";
    private static final long ROOM_A301 = 1L;
    private static final long ROOM_B502 = 5L;

    @Autowired
    TestRestTemplate http;
    @Autowired
    JdbcTemplate jdbc;

    private String zhangsanToken;
    private String adminToken;

    @BeforeAll
    void login() {
        zhangsanToken = doLogin("zhangsan");
        adminToken = doLogin("admin");
    }

    @AfterAll
    void cleanup() {
        jdbc.update("DELETE FROM approval_record WHERE reservation_id IN "
                + "(SELECT id FROM reservation WHERE request_id LIKE ?)", IT_REQUEST_PREFIX + "%");
        jdbc.update("DELETE FROM operation_log WHERE business_type = 'RESERVATION' AND business_id IN "
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

    /** First day within the advance window where the given room is free at hour..hour+1. */
    private LocalDateTime freeSlot(long roomId, int hour) {
        for (int dayOffset = 1; dayOffset <= 6; dayOffset++) {
            LocalDateTime start = LocalDate.now().plusDays(dayOffset).atTime(hour, 0);
            Integer active = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM reservation WHERE room_id = ? AND status IN ('PENDING','CONFIRMED') "
                            + "AND start_time < ? AND end_time > ?",
                    Integer.class, roomId, start.plusHours(1), start);
            if (active != null && active == 0) return start;
        }
        throw new IllegalStateException("一周内找不到满足条件的空闲时段，请检查数据库状态");
    }

    private String requestId(String tag) {
        return IT_REQUEST_PREFIX + tag + "-" + UUID.randomUUID();
    }

    // —— 便捷访问：create / update / cancel / 管理员操作 —— //

    private ResponseEntity<ApiResponse<ReservationResponse>> create(String requestId, long roomId,
                                                                    LocalDateTime start, LocalDateTime end) {
        return http.exchange("/api/reservations", HttpMethod.POST,
                jsonEntity(zhangsanToken, Map.of("requestId", requestId, "roomId", roomId, "title", "持久化一致性测试",
                        "startTime", start.toString(), "endTime", end.toString(),
                        "participantCount", 2, "remark", "ReservationPersistenceIT")),
                new ParameterizedTypeReference<>() {
                });
    }

    private ResponseEntity<ApiResponse<ReservationResponse>> update(long id, long roomId,
                                                                    LocalDateTime start, LocalDateTime end) {
        return http.exchange("/api/reservations/" + id, HttpMethod.PUT,
                jsonEntity(zhangsanToken, Map.of("roomId", roomId, "title", "改期后的会议",
                        "startTime", start.toString(), "endTime", end.toString(),
                        "participantCount", 2, "remark", "ReservationPersistenceIT")),
                new ParameterizedTypeReference<>() {
                });
    }

    private ResponseEntity<ApiResponse<ReservationResponse>> cancel(long id, String reason) {
        return http.exchange("/api/reservations/" + id + "/cancel", HttpMethod.POST,
                jsonEntity(zhangsanToken, reason == null ? null : Map.of("reason", reason)),
                new ParameterizedTypeReference<>() {
                });
    }

    private ResponseEntity<ApiResponse<Object>> adminPost(String path, String body) {
        return http.exchange("/api/admin/reservations" + path, HttpMethod.POST,
                jsonEntity(adminToken, body == null ? null : Map.of("reason", body)),
                new ParameterizedTypeReference<>() {
                });
    }

    // —— 数据库直查：验证的唯一事实来源 —— //

    private String dbStatus(long id) {
        return jdbc.queryForObject("SELECT status FROM reservation WHERE id = ?", String.class, id);
    }

    private String dbCancelReason(long id) {
        return jdbc.queryForObject("SELECT cancel_reason FROM reservation WHERE id = ?", String.class, id);
    }

    private String dbLatestApprovalRemark(long reservationId) {
        return jdbc.queryForObject(
                "SELECT remark FROM approval_record WHERE reservation_id = ? ORDER BY id DESC LIMIT 1",
                String.class, reservationId);
    }

    private static void assertOk(ResponseEntity<? extends ApiResponse<?>> response) {
        assertTrue(response.getStatusCode().is2xxSuccessful() && response.getBody() != null
                        && response.getBody().data() != null,
                "请求应成功: HTTP " + response.getStatusCode().value());
    }

    private static void assertCode(ResponseEntity<? extends ApiResponse<?>> response, String expectedCode) {
        assertTrue(response.getBody() != null, "响应体不应为空");
        assertEquals(expectedCode, response.getBody().code(),
                "HTTP " + response.getStatusCode().value() + " 应返回 " + expectedCode);
    }

    // —— Case 1：CONFIRMED 改到需审批会议室，Response 与数据库都必须变 PENDING —— //

    @Test
    void rescheduleConfirmedToApprovalRoomPersistsPendingInDatabase() {
        LocalDateTime slot = freeSlot(ROOM_A301, 10);
        ResponseEntity<ApiResponse<ReservationResponse>> created = create(requestId("case1"), ROOM_A301, slot, slot.plusHours(1));
        assertOk(created);
        assertEquals("CONFIRMED", created.getBody().data().status());

        ResponseEntity<ApiResponse<ReservationResponse>> updated =
                update(created.getBody().data().id(), ROOM_B502, slot, slot.plusHours(1));

        assertOk(updated);
        assertEquals("PENDING", updated.getBody().data().status());
        assertEquals("PENDING", dbStatus(created.getBody().data().id()),
                "改期到需审批会议室后，数据库 status 必须真实变为 PENDING");
    }

    // —— Case 2：PENDING 改到普通会议室，Response 与数据库都必须变 CONFIRMED —— //

    @Test
    void reschedulePendingToNormalRoomPersistsConfirmedInDatabase() {
        LocalDateTime slot = freeSlot(ROOM_B502, 11);
        ResponseEntity<ApiResponse<ReservationResponse>> created = create(requestId("case2"), ROOM_B502, slot, slot.plusHours(1));
        assertOk(created);
        assertEquals("PENDING", created.getBody().data().status());

        ResponseEntity<ApiResponse<ReservationResponse>> updated =
                update(created.getBody().data().id(), ROOM_A301, slot, slot.plusHours(1));

        assertOk(updated);
        assertEquals("CONFIRMED", updated.getBody().data().status());
        assertEquals("CONFIRMED", dbStatus(created.getBody().data().id()),
                "改期到普通会议室后，数据库 status 必须真实变为 CONFIRMED");
    }

    // —— Case 3：驳回写 REJECTED，理由进 approval_record.remark，cancel_reason 必须保持干净 —— //

    @Test
    void rejectPersistsRejectedAndKeepsCancelReasonClean() {
        LocalDateTime slot = freeSlot(ROOM_B502, 12);
        ResponseEntity<ApiResponse<ReservationResponse>> created = create(requestId("case3"), ROOM_B502, slot, slot.plusHours(1));
        assertOk(created);
        long id = created.getBody().data().id();

        ResponseEntity<ApiResponse<Object>> rejected = adminPost("/" + id + "/reject", "场地需要临时维护");

        assertOk(rejected);
        assertEquals("REJECTED", dbStatus(id));
        assertNull(dbCancelReason(id), "审批驳回理由不得污染 reservation.cancel_reason");
        assertEquals("场地需要临时维护", dbLatestApprovalRemark(id), "驳回理由应保存在 approval_record.remark");
    }

    // —— Case 4：用户取消写 CANCELLED + cancel_reason —— //

    @Test
    void ownerCancelPersistsCancelledWithReason() {
        LocalDateTime slot = freeSlot(ROOM_A301, 13);
        ResponseEntity<ApiResponse<ReservationResponse>> created = create(requestId("case4"), ROOM_A301, slot, slot.plusHours(1));
        assertOk(created);
        long id = created.getBody().data().id();

        ResponseEntity<ApiResponse<ReservationResponse>> cancelled = cancel(id, "临时有事，改约下周");

        assertOk(cancelled);
        assertEquals("CANCELLED", dbStatus(id));
        assertEquals("临时有事，改约下周", dbCancelReason(id));
    }

    // —— Case 4b：管理员强制取消写 CANCELLED + cancel_reason —— //

    @Test
    void forceCancelPersistsCancelledWithReason() {
        LocalDateTime slot = freeSlot(ROOM_A301, 14);
        ResponseEntity<ApiResponse<ReservationResponse>> created = create(requestId("case4b"), ROOM_A301, slot, slot.plusHours(1));
        assertOk(created);
        long id = created.getBody().data().id();

        ResponseEntity<ApiResponse<Object>> cancelled = adminPost("/" + id + "/force-cancel", "设备检修，紧急腾退");

        assertOk(cancelled);
        assertEquals("CANCELLED", dbStatus(id));
        assertEquals("设备检修，紧急腾退", dbCancelReason(id));
    }

    // —— Case 5：终态不可逆——REJECTED/CANCELLED 上的所有生命周期操作必须失败 —— //

    @Test
    void terminalStatesRejectFurtherLifecycleOperations() {
        LocalDateTime rejectedSlot = freeSlot(ROOM_B502, 15);
        ResponseEntity<ApiResponse<ReservationResponse>> rejectedSeed =
                create(requestId("case5r"), ROOM_B502, rejectedSlot, rejectedSlot.plusHours(1));
        assertOk(rejectedSeed);
        long rejectedId = rejectedSeed.getBody().data().id();
        assertOk(adminPost("/" + rejectedId + "/reject", "终态测试-驳回"));
        assertEquals("REJECTED", dbStatus(rejectedId));

        assertCode(adminPost("/" + rejectedId + "/approve", null), "RESERVATION_INVALID_STATE");
        assertCode(adminPost("/" + rejectedId + "/force-cancel", "终态测试-强删"), "RESERVATION_INVALID_STATE");
        assertCode(cancel(rejectedId, "终态测试-取消"), "RESERVATION_INVALID_STATE");
        assertCode(update(rejectedId, ROOM_A301, rejectedSlot, rejectedSlot.plusHours(1)), "RESERVATION_INVALID_STATE");
        assertEquals("REJECTED", dbStatus(rejectedId), "非法操作不得改变终态");

        LocalDateTime cancelledSlot = freeSlot(ROOM_A301, 16);
        ResponseEntity<ApiResponse<ReservationResponse>> cancelledSeed =
                create(requestId("case5c"), ROOM_A301, cancelledSlot, cancelledSlot.plusHours(1));
        assertOk(cancelledSeed);
        long cancelledId = cancelledSeed.getBody().data().id();
        assertOk(cancel(cancelledId, "终态测试-先取消"));
        assertEquals("CANCELLED", dbStatus(cancelledId));

        assertCode(cancel(cancelledId, "终态测试-重复取消"), "RESERVATION_INVALID_STATE");
        assertCode(adminPost("/" + cancelledId + "/force-cancel", "终态测试-强删"), "RESERVATION_INVALID_STATE");
        assertCode(update(cancelledId, ROOM_A301, cancelledSlot, cancelledSlot.plusHours(1)), "RESERVATION_INVALID_STATE");
        assertEquals("CANCELLED", dbStatus(cancelledId), "非法操作不得改变终态");
    }

    // —— 并发防线：expected-state 条件让迟到的迁移 affectedRows == 0，服务层 fail-closed —— //

    @Test
    void expectedStateConditionBlocksLateTransition() {
        LocalDateTime slot = freeSlot(ROOM_B502, 9);
        ResponseEntity<ApiResponse<ReservationResponse>> created = create(requestId("race"), ROOM_B502, slot, slot.plusHours(1));
        assertOk(created);
        long id = created.getBody().data().id();
        assertEquals("PENDING", dbStatus(id));

        // 事务 A：PENDING -> CONFIRMED（成功）。
        assertOk(adminPost("/" + id + "/approve", null));
        assertEquals("CONFIRMED", dbStatus(id));

        // 事务 B：仍按 expected=PENDING 尝试 PENDING -> REJECTED，UPDATE 必须 0 行命中。
        int affectedRows = jdbc.update(
                "UPDATE reservation SET status = 'REJECTED' WHERE id = ? AND status = 'PENDING'", id);
        assertEquals(0, affectedRows, "expected-state 条件必须让迟到的迁移落空");
        assertEquals("CONFIRMED", dbStatus(id), "最后一个 UPDATE 不允许覆盖前一个状态");

        // 服务层对同一迟到操作 fail-closed，返回 RESERVATION_INVALID_STATE。
        assertCode(adminPost("/" + id + "/reject", "迟到的驳回"), "RESERVATION_INVALID_STATE");
        assertEquals("CONFIRMED", dbStatus(id));
    }

    private static HttpEntity<Object> jsonEntity(String token, Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (token != null) headers.setBearerAuth(token);
        return new HttpEntity<>(body, headers);
    }
}
