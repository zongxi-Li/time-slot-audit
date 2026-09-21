/**
 * 文件职责：验证 ReservationServiceTest 相关业务、接口安全或边界条件。
 * 接口：使用 JUnit、Mockito 或 Spring 测试工具，不属于运行时接口。
 */
package com.timeslot.reservation.service;

import com.timeslot.common.api.ErrorCode;
import com.timeslot.common.bookingwindow.BookingWindowResponse;
import com.timeslot.common.bookingwindow.BookingWindowService;
import com.timeslot.common.exception.BusinessException;
import com.timeslot.common.security.AuthenticatedUser;
import com.timeslot.common.security.CurrentUserProvider;
import com.timeslot.identity.domain.BookingQualification;
import com.timeslot.identity.service.BookingQualificationService;
import com.timeslot.reservation.domain.Reservation;
import com.timeslot.reservation.domain.ReservationStatus;
import com.timeslot.reservation.dto.CreateReservationRequest;
import com.timeslot.reservation.dto.ReservationResponse;
import com.timeslot.reservation.dto.UpdateReservationRequest;
import com.timeslot.reservation.mapper.ReservationMapper;
import com.timeslot.reservation.spi.ReservationNotificationPort;
import com.timeslot.resource.dto.BookableRoomProfile;
import com.timeslot.resource.service.ResourceBookingQueryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.springframework.dao.DuplicateKeyException;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ReservationServiceTest {
    @Mock ReservationMapper reservationMapper;
    @Mock ResourceBookingQueryService resourceBookingQueryService;
    @Mock BookingQualificationService bookingQualificationService;
    @Mock CurrentUserProvider currentUserProvider;
    @Mock BookingWindowService bookingWindowService;
    @Mock ReservationNotificationPort notificationPort;

    private ReservationService service;
    private final Clock clock = Clock.fixed(Instant.parse("2026-09-11T02:00:00Z"), ZoneId.of("Asia/Shanghai"));
    /** Small normal room: no approval required, max 120 minutes, 7 days in advance. */
    private final BookableRoomProfile room = new BookableRoomProfile(1L, "A301", 8, "AVAILABLE",
            false, 120, 7);
    /** 默认全局可预约窗口 08:00 - 次日 08:00（480..1920 分钟）。 */
    private static final BookingWindowResponse DEFAULT_WINDOW = new BookingWindowResponse(480, 1920);

    @BeforeEach
    void setUp() {
        service = new ReservationService(reservationMapper, resourceBookingQueryService, bookingQualificationService,
                currentUserProvider, bookingWindowService, notificationPort, clock);
        when(currentUserProvider.getRequired()).thenReturn(new AuthenticatedUser(2L, "zhangsan", "USER"));
        when(bookingQualificationService.check(2L)).thenReturn(BookingQualification.allow(2L, 100));
        when(reservationMapper.findByUserIdAndRequestId(anyLong(), anyString())).thenReturn(null);
        when(resourceBookingQueryService.lockBookableRoom(1L)).thenReturn(room);
        when(bookingWindowService.get()).thenReturn(DEFAULT_WINDOW);
        when(reservationMapper.findFirstConflict(anyLong(), any(), any(), any())).thenReturn(null);
        // 默认桩：expected-state UPDATE 命中 1 行；模拟并发失配的测试自行改为 0。
        when(reservationMapper.updateScheduleAndStatus(any(), anyString(), anyInt())).thenReturn(1);
        when(reservationMapper.cancelExpected(anyLong(), anyString(), anyString(), any())).thenReturn(1);
    }

    private CreateReservationRequest request() {
        return new CreateReservationRequest("request-1", 1L, "课程讨论",
                LocalDateTime.of(2026, 9, 12, 10, 0), LocalDateTime.of(2026, 9, 12, 11, 0), 6, "备注", null);
    }

    @Test
    void createsConfirmedReservationForNormalRoom() {
        ReservationResponse response = service.createReservation(request());

        assertEquals("CONFIRMED", response.status());
        verify(reservationMapper).insert(any(Reservation.class));
        InOrder order = inOrder(resourceBookingQueryService, reservationMapper);
        order.verify(resourceBookingQueryService).lockBookableRoom(1L);
        order.verify(reservationMapper).findFirstConflict(anyLong(), any(), any(), any());
    }

    @Test
    void approvalRoomCreatesPendingReservation() {
        when(resourceBookingQueryService.lockBookableRoom(1L)).thenReturn(new BookableRoomProfile(1L, "A101", 30,
                "AVAILABLE", true, 240, 14));

        assertEquals("PENDING", service.createReservation(request()).status());
        verify(notificationPort).notifyCreated(eq(2L), any(), any(), any(), any(), eq(ReservationStatus.PENDING));
        verify(notificationPort).notifyPendingApproval(any(), any(), any(), any());
    }

    @Test
    void conflictReturnsConflictCodeAndDoesNotInsert() {
        when(reservationMapper.findFirstConflict(anyLong(), any(), any(), any())).thenReturn(conflictingReservation());

        BusinessException exception = assertThrows(BusinessException.class, () -> service.createReservation(request()));

        assertEquals(ErrorCode.RESERVATION_TIME_CONFLICT, exception.getCode());
        // 任务书要求：冲突提示必须说明冲突会议主题与占用时段。
        assertTrue(exception.getMessage().contains("季度复盘会"));
        assertTrue(exception.getMessage().contains("2026-09-12 10:30"));
        assertTrue(exception.getMessage().contains("2026-09-12 11:30"));
        verify(reservationMapper, never()).insert(any());
    }

    /** 与新建请求（10:00..11:00）后段重叠的冲突单，用于验证提示内容。 */
    private Reservation conflictingReservation() {
        Reservation conflict = new Reservation();
        conflict.setId(99L);
        conflict.setRoomId(1L);
        conflict.setUserId(3L);
        conflict.setRoomName("A301");
        conflict.setUserName("李四");
        conflict.setTitle("季度复盘会");
        conflict.setStartTime(LocalDateTime.of(2026, 9, 12, 10, 30));
        conflict.setEndTime(LocalDateTime.of(2026, 9, 12, 11, 30));
        conflict.setStatus(ReservationStatus.CONFIRMED);
        return conflict;
    }

    @Test
    void unqualifiedUserCannotCreateReservation() {
        when(bookingQualificationService.check(2L)).thenReturn(
                BookingQualification.deny(2L, "账号已被禁用，请联系管理员", 100, null));

        BusinessException exception = assertThrows(BusinessException.class, () -> service.createReservation(request()));

        assertEquals(ErrorCode.FORBIDDEN, exception.getCode());
        verify(reservationMapper, never()).insert(any());
        verify(resourceBookingQueryService, never()).lockBookableRoom(anyLong());
    }

    @Test
    void maintenanceRoomIsRejected() {
        when(resourceBookingQueryService.lockBookableRoom(1L)).thenReturn(new BookableRoomProfile(1L, "A301", 8,
                "MAINTENANCE", false, 120, 7));

        BusinessException exception = assertThrows(BusinessException.class, () -> service.createReservation(request()));

        assertEquals(ErrorCode.ROOM_UNAVAILABLE, exception.getCode());
    }

    @Test
    void capacityExceededIsRejected() {
        CreateReservationRequest tooMany = new CreateReservationRequest("request-1", 1L, "课程讨论",
                request().startTime(), request().endTime(), 9, null, null);

        BusinessException exception = assertThrows(BusinessException.class, () -> service.createReservation(tooMany));

        assertEquals(ErrorCode.ROOM_CAPACITY_EXCEEDED, exception.getCode());
    }

    @Test
    void invalidTimeIsRejected() {
        CreateReservationRequest invalid = new CreateReservationRequest("request-1", 1L, "课程讨论",
                request().endTime(), request().startTime(), 3, null, null);

        assertEquals(ErrorCode.VALIDATION_ERROR, assertThrows(BusinessException.class,
                () -> service.createReservation(invalid)).getCode());
    }

    @Test
    void overnightReservationInsideWindowIsAllowed() {
        // 18:00 至次日 01:00（1500 分钟）仍在默认窗口 480..1920 内；房间时长上限放开以聚焦窗口规则
        when(resourceBookingQueryService.lockBookableRoom(1L)).thenReturn(
                new BookableRoomProfile(1L, "A301", 8, "AVAILABLE", false, 24 * 60, 7));
        CreateReservationRequest overnight = new CreateReservationRequest("request-1", 1L, "通宵研讨",
                LocalDateTime.of(2026, 9, 12, 18, 0), LocalDateTime.of(2026, 9, 13, 1, 0), 4, null, null);

        assertEquals("CONFIRMED", service.createReservation(overnight).status());
        verify(reservationMapper).insert(any(Reservation.class));
    }

    @Test
    void reservationBeyondWindowEndIsRejected() {
        // 结束落到次日 10:00（2040 分钟），超出默认窗口 1920 分钟
        CreateReservationRequest beyond = new CreateReservationRequest("request-1", 1L, "超时段预约",
                LocalDateTime.of(2026, 9, 12, 18, 0), LocalDateTime.of(2026, 9, 13, 10, 0), 4, null, null);

        BusinessException exception = assertThrows(BusinessException.class, () -> service.createReservation(beyond));

        assertEquals(ErrorCode.VALIDATION_ERROR, exception.getCode());
        verify(reservationMapper, never()).insert(any(Reservation.class));
    }

    @Test
    void reservationBeforeWindowStartIsRejected() {
        when(bookingWindowService.get()).thenReturn(new BookingWindowResponse(600, 1080));
        CreateReservationRequest early = new CreateReservationRequest("request-1", 1L, "过早预约",
                LocalDateTime.of(2026, 9, 12, 9, 0), LocalDateTime.of(2026, 9, 12, 10, 0), 4, null, null);

        BusinessException exception = assertThrows(BusinessException.class, () -> service.createReservation(early));

        assertEquals(ErrorCode.VALIDATION_ERROR, exception.getCode());
        verify(reservationMapper, never()).insert(any(Reservation.class));
    }

    @Test
    void sameRequestIdReturnsExistingReservation() {
        Reservation existing = new Reservation();
        existing.setId(42L);
        existing.setRequestId("request-1");
        existing.setReservationNo("RSV42");
        existing.setRoomId(1L);
        existing.setUserId(2L);
        existing.setRoomName("A301");
        existing.setUserName("张三");
        existing.setTitle("已存在");
        existing.setStartTime(request().startTime());
        existing.setEndTime(request().endTime());
        existing.setParticipantCount(3);
        existing.setStatus(ReservationStatus.CONFIRMED);
        when(reservationMapper.findByUserIdAndRequestId(2L, "request-1")).thenReturn(existing);

        assertEquals(42L, service.createReservation(request()).id());
        verify(reservationMapper).findByUserIdAndRequestId(2L, "request-1");
        verify(resourceBookingQueryService, never()).lockBookableRoom(anyLong());
        verify(reservationMapper, never()).insert(any());
    }

    @Test
    void sameRequestIdFromDifferentUsersCreatesIndependently() {
        service.createReservation(request());

        when(currentUserProvider.getRequired()).thenReturn(new AuthenticatedUser(3L, "lisi", "USER"));
        when(bookingQualificationService.check(3L)).thenReturn(BookingQualification.allow(3L, 100));
        service.createReservation(request());

        // each create consults the idempotency key twice: before the room lock and again after it
        verify(reservationMapper, times(2)).findByUserIdAndRequestId(2L, "request-1");
        verify(reservationMapper, times(2)).findByUserIdAndRequestId(3L, "request-1");
        verify(reservationMapper, times(2)).insert(any(Reservation.class));
    }

    @Test
    void duplicateKeyFallbackLooksUpOnlyWithinCurrentUserScope() {
        Reservation duplicate = new Reservation();
        duplicate.setId(44L);
        duplicate.setRequestId("request-1");
        duplicate.setRoomId(1L);
        duplicate.setUserId(2L);
        duplicate.setTitle("已存在");
        duplicate.setStartTime(request().startTime());
        duplicate.setEndTime(request().endTime());
        duplicate.setStatus(ReservationStatus.CONFIRMED);
        when(reservationMapper.insert(any(Reservation.class))).thenThrow(new DuplicateKeyException("uk_reservation_user_request"));
        when(reservationMapper.findByUserIdAndRequestId(2L, "request-1")).thenReturn(duplicate);

        assertEquals(44L, service.createReservation(request()).id());
        verify(reservationMapper, never()).findByUserIdAndRequestId(3L, "request-1");
    }

    @Test
    void concurrentRetryRechecksRequestIdAfterRoomLock() {
        Reservation existing = new Reservation();
        existing.setId(43L);
        existing.setRequestId("request-1");
        existing.setRoomId(1L);
        existing.setUserId(2L);
        existing.setStartTime(request().startTime());
        existing.setEndTime(request().endTime());
        existing.setStatus(ReservationStatus.CONFIRMED);
        when(reservationMapper.findByUserIdAndRequestId(2L, "request-1")).thenReturn(null, existing);

        assertEquals(43L, service.createReservation(request()).id());
        verify(resourceBookingQueryService).lockBookableRoom(1L);
        verify(reservationMapper, never()).findFirstConflict(anyLong(), any(), any(), any());
        verify(reservationMapper, never()).insert(any());
    }

    /* —— 周期性会议（按周重复）—— */

    private CreateReservationRequest recurringRequest(int weeks) {
        return new CreateReservationRequest("request-1", 1L, "周期例会",
                LocalDateTime.of(2026, 9, 12, 10, 0), LocalDateTime.of(2026, 9, 12, 11, 0), 6, null, weeks);
    }

    @Test
    void recurringReservationCreatesWeeklyOccurrences() {
        ReservationResponse response = service.createReservation(recurringRequest(3));

        // 响应返回首次发生的预约；三场按 +7 天展开，幂等键仅首场用原始 requestId。
        assertEquals("CONFIRMED", response.status());
        assertEquals(LocalDateTime.of(2026, 9, 12, 10, 0), response.startTime());
        ArgumentCaptor<Reservation> captor = ArgumentCaptor.forClass(Reservation.class);
        verify(reservationMapper, times(3)).insert(captor.capture());
        assertEquals(LocalDateTime.of(2026, 9, 19, 10, 0), captor.getAllValues().get(1).getStartTime());
        assertEquals(LocalDateTime.of(2026, 9, 26, 11, 0), captor.getAllValues().get(2).getEndTime());
        assertEquals("request-1", captor.getAllValues().get(0).getRequestId());
        assertEquals("request-1~w1", captor.getAllValues().get(1).getRequestId());
        assertEquals("request-1~w2", captor.getAllValues().get(2).getRequestId());
    }

    @Test
    void conflictInAnyWeekRejectsWholeRecurringRequest() {
        // 仅第三周（9/26 同时段）与已有预约冲突：整批不落库。
        when(reservationMapper.findFirstConflict(eq(1L),
                eq(LocalDateTime.of(2026, 9, 26, 10, 0)), eq(LocalDateTime.of(2026, 9, 26, 11, 0)), isNull()))
                .thenReturn(conflictingReservation());

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.createReservation(recurringRequest(3)));

        assertEquals(ErrorCode.RESERVATION_TIME_CONFLICT, exception.getCode());
        verify(reservationMapper, never()).insert(any(Reservation.class));
    }

    @Test
    void recurringLaterWeeksAreNotLimitedByAdvanceDays() {
        // 首次开始 9/12（提前 1 天）；第 4 周落到 10/3，远超 7 天提前量——周期预约的提前量只看首次开始。
        when(resourceBookingQueryService.lockBookableRoom(1L)).thenReturn(
                new BookableRoomProfile(1L, "A301", 8, "AVAILABLE", false, 120, 7));

        assertEquals("CONFIRMED", service.createReservation(recurringRequest(4)).status());
        verify(reservationMapper, times(4)).insert(any(Reservation.class));
    }

    @Test
    void repeatWeeksOutOfRangeIsRejected() {
        assertEquals(ErrorCode.VALIDATION_ERROR, assertThrows(BusinessException.class,
                () -> service.createReservation(recurringRequest(9))).getCode());
        verify(reservationMapper, never()).insert(any(Reservation.class));
    }

    private Reservation ownedReservation(Long ownerId) {
        Reservation reservation = new Reservation();
        reservation.setId(7L);
        reservation.setRequestId("request-7");
        reservation.setReservationNo("RSV7");
        reservation.setRoomId(1L);
        reservation.setUserId(ownerId);
        reservation.setRoomName("A301");
        reservation.setTitle("课程讨论");
        reservation.setStartTime(LocalDateTime.of(2026, 9, 12, 15, 0));
        reservation.setEndTime(LocalDateTime.of(2026, 9, 12, 16, 0));
        reservation.setParticipantCount(4);
        reservation.setStatus(ReservationStatus.CONFIRMED);
        return reservation;
    }

    @Test
    void ownerCancelsOwnReservation() {
        Reservation reservation = ownedReservation(2L);
        when(reservationMapper.findByIdForUpdate(7L)).thenReturn(reservation);

        assertEquals("CANCELLED", service.cancel(7L, null).status());
        verify(reservationMapper).cancelExpected(7L, "CONFIRMED", "CANCELLED", null);
    }

    @Test
    void otherUserCannotCancelReservation() {
        when(reservationMapper.findByIdForUpdate(7L)).thenReturn(ownedReservation(3L));

        BusinessException exception = assertThrows(BusinessException.class, () -> service.cancel(7L, null));

        assertEquals(ErrorCode.FORBIDDEN, exception.getCode());
        verify(reservationMapper, never()).cancelExpected(anyLong(), anyString(), anyString(), any());
    }

    @Test
    void adminCannotCancelOthersReservationViaNormalEndpoint() {
        when(currentUserProvider.getRequired()).thenReturn(new AuthenticatedUser(1L, "admin", "ADMIN"));
        when(reservationMapper.findByIdForUpdate(7L)).thenReturn(ownedReservation(2L));

        BusinessException exception = assertThrows(BusinessException.class, () -> service.cancel(7L, null));

        assertEquals(ErrorCode.FORBIDDEN, exception.getCode());
        verify(reservationMapper, never()).cancelExpected(anyLong(), anyString(), anyString(), any());
    }

    @Test
    void cancelFailsClosedWhenExpectedStateMisses() {
        when(reservationMapper.findByIdForUpdate(7L)).thenReturn(ownedReservation(2L));
        when(reservationMapper.cancelExpected(eq(7L), eq("CONFIRMED"), eq("CANCELLED"), any())).thenReturn(0);

        BusinessException exception = assertThrows(BusinessException.class, () -> service.cancel(7L, null));

        assertEquals(ErrorCode.RESERVATION_INVALID_STATE, exception.getCode());
    }

    /** 任务书点名情形：已开始的预约不能取消（与改期共用守卫，此处为独立断言用例）。 */
    @Test
    void startedReservationCannotBeCancelled() {
        Reservation started = ownedReservation(2L);
        started.setStartTime(LocalDateTime.of(2026, 9, 11, 9, 0));
        started.setEndTime(LocalDateTime.of(2026, 9, 11, 10, 0)); // 固定时钟 now = 2026-09-11 10:00
        when(reservationMapper.findByIdForUpdate(7L)).thenReturn(started);

        BusinessException exception = assertThrows(BusinessException.class, () -> service.cancel(7L, null));

        assertEquals(ErrorCode.RESERVATION_INVALID_STATE, exception.getCode());
        assertTrue(exception.getMessage().contains("预约已开始"));
        verify(reservationMapper, never()).cancelExpected(anyLong(), anyString(), anyString(), any());
    }

    private UpdateReservationRequest updateRequest() {
        return new UpdateReservationRequest(0, 1L, "改期后的讨论", LocalDateTime.of(2026, 9, 12, 16, 0),
                LocalDateTime.of(2026, 9, 12, 17, 0), 4, "改期备注");
    }

    @Test
    void ownerReschedulesAndConflictCheckExcludesSelf() {
        Reservation reservation = ownedReservation(2L);
        when(reservationMapper.findByIdForUpdate(7L)).thenReturn(reservation);

        ReservationResponse response = service.update(7L, updateRequest());

        assertEquals(LocalDateTime.of(2026, 9, 12, 16, 0), response.startTime());
        assertEquals("CONFIRMED", response.status());
        verify(resourceBookingQueryService).lockBookableRoom(1L);
        verify(reservationMapper).findFirstConflict(1L,
                LocalDateTime.of(2026, 9, 12, 16, 0), LocalDateTime.of(2026, 9, 12, 17, 0), 7L);
        // 改期字段与重算后的状态必须同一条 UPDATE 原子写入。
        verify(reservationMapper).updateScheduleAndStatus(reservation, "CONFIRMED", 0);
    }

    @Test
    void rescheduleRejectsStaleVersionBeforeChangingTheReservation() {
        Reservation reservation = ownedReservation(2L);
        reservation.setVersion(2);
        when(reservationMapper.findByIdForUpdate(7L)).thenReturn(reservation);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.update(7L, updateRequest()));

        assertEquals(ErrorCode.RESERVATION_INVALID_STATE, exception.getCode());
        verify(resourceBookingQueryService, never()).lockBookableRoom(anyLong());
        verify(reservationMapper, never()).updateScheduleAndStatus(any(), anyString(), anyInt());
    }

    @Test
    void rescheduleToApprovalRoomMovesConfirmedBackToPending() {
        Reservation reservation = ownedReservation(2L);
        when(reservationMapper.findByIdForUpdate(7L)).thenReturn(reservation);
        when(resourceBookingQueryService.lockBookableRoom(5L)).thenReturn(new BookableRoomProfile(5L, "B502", 60,
                "AVAILABLE", true, 240, 14));

        ReservationResponse response = service.update(7L,
                new UpdateReservationRequest(0, 5L, "大会议室改期", LocalDateTime.of(2026, 9, 12, 16, 0),
                        LocalDateTime.of(2026, 9, 12, 17, 0), 30, null));

        assertEquals("PENDING", response.status());
        verify(reservationMapper).updateScheduleAndStatus(reservation, "CONFIRMED", 0);
    }

    @Test
    void reschedulePendingToNormalRoomBecomesConfirmed() {
        Reservation pending = ownedReservation(2L);
        pending.setStatus(ReservationStatus.PENDING);
        when(reservationMapper.findByIdForUpdate(7L)).thenReturn(pending);

        ReservationResponse response = service.update(7L, updateRequest());

        assertEquals("CONFIRMED", response.status());
        verify(reservationMapper).updateScheduleAndStatus(pending, "PENDING", 0);
    }

    @Test
    void rescheduleFailsClosedWhenExpectedStateMisses() {
        Reservation reservation = ownedReservation(2L);
        when(reservationMapper.findByIdForUpdate(7L)).thenReturn(reservation);
        when(reservationMapper.updateScheduleAndStatus(any(), eq("CONFIRMED"), anyInt())).thenReturn(0);

        BusinessException exception = assertThrows(BusinessException.class, () -> service.update(7L, updateRequest()));

        assertEquals(ErrorCode.RESERVATION_INVALID_STATE, exception.getCode());
    }

    @Test
    void rejectedReservationCannotBeRescheduled() {
        Reservation rejected = ownedReservation(2L);
        rejected.setStatus(ReservationStatus.REJECTED);
        when(reservationMapper.findByIdForUpdate(7L)).thenReturn(rejected);

        BusinessException exception = assertThrows(BusinessException.class, () -> service.update(7L, updateRequest()));

        assertEquals(ErrorCode.RESERVATION_INVALID_STATE, exception.getCode());
        verify(reservationMapper, never()).updateScheduleAndStatus(any(), anyString(), anyInt());
    }

    @Test
    void rescheduleConflictIsRejectedWithoutUpdate() {
        when(reservationMapper.findByIdForUpdate(7L)).thenReturn(ownedReservation(2L));
        when(reservationMapper.findFirstConflict(anyLong(), any(), any(), anyLong())).thenReturn(conflictingReservation());

        BusinessException exception = assertThrows(BusinessException.class, () -> service.update(7L, updateRequest()));

        assertEquals(ErrorCode.RESERVATION_TIME_CONFLICT, exception.getCode());
        assertTrue(exception.getMessage().contains("季度复盘会"));
        verify(reservationMapper, never()).updateScheduleAndStatus(any(), anyString(), anyInt());
    }

    @Test
    void otherUserCannotReschedule() {
        when(reservationMapper.findByIdForUpdate(7L)).thenReturn(ownedReservation(3L));

        BusinessException exception = assertThrows(BusinessException.class, () -> service.update(7L, updateRequest()));

        assertEquals(ErrorCode.FORBIDDEN, exception.getCode());
        verify(reservationMapper, never()).updateScheduleAndStatus(any(), anyString(), anyInt());
    }

    @Test
    void startedReservationCannotBeRescheduled() {
        Reservation started = ownedReservation(2L);
        started.setStartTime(LocalDateTime.of(2026, 9, 11, 9, 0));
        started.setEndTime(LocalDateTime.of(2026, 9, 11, 10, 0));
        when(reservationMapper.findByIdForUpdate(7L)).thenReturn(started);

        BusinessException exception = assertThrows(BusinessException.class, () -> service.update(7L, updateRequest()));

        assertEquals(ErrorCode.RESERVATION_INVALID_STATE, exception.getCode());
        verify(resourceBookingQueryService, never()).lockBookableRoom(anyLong());
    }

    @Test
    void rescheduleBeyondWindowEndIsRejected() {
        when(reservationMapper.findByIdForUpdate(7L)).thenReturn(ownedReservation(2L));

        BusinessException exception = assertThrows(BusinessException.class, () -> service.update(7L,
                new UpdateReservationRequest(0, 1L, "改期超出时段", LocalDateTime.of(2026, 9, 12, 18, 0),
                        LocalDateTime.of(2026, 9, 13, 10, 0), 4, null)));

        assertEquals(ErrorCode.VALIDATION_ERROR, exception.getCode());
        verify(reservationMapper, never()).updateScheduleAndStatus(any(), anyString(), anyInt());
    }

    @Test
    void rescheduleToNextDayInsideWindowIsAllowed() {
        when(reservationMapper.findByIdForUpdate(7L)).thenReturn(ownedReservation(2L));
        when(resourceBookingQueryService.lockBookableRoom(1L)).thenReturn(
                new BookableRoomProfile(1L, "A301", 8, "AVAILABLE", false, 24 * 60, 7));

        ReservationResponse response = service.update(7L,
                new UpdateReservationRequest(0, 1L, "跨日改期", LocalDateTime.of(2026, 9, 12, 18, 0),
                        LocalDateTime.of(2026, 9, 13, 1, 0), 4, null));

        assertEquals(LocalDateTime.of(2026, 9, 13, 1, 0), response.endTime());
        verify(reservationMapper).updateScheduleAndStatus(any(), anyString(), anyInt());
    }

    @Test
    void ownerCanViewDetail() {
        when(reservationMapper.findById(7L)).thenReturn(ownedReservation(2L));

        assertEquals(7L, service.detail(7L).id());
    }

    @Test
    void adminCanViewButOtherUserCannotViewDetail() {
        when(reservationMapper.findById(7L)).thenReturn(ownedReservation(2L));
        when(currentUserProvider.getRequired()).thenReturn(new AuthenticatedUser(1L, "admin", "ADMIN"));
        assertEquals(7L, service.detail(7L).id());

        when(currentUserProvider.getRequired()).thenReturn(new AuthenticatedUser(3L, "lisi", "USER"));
        assertEquals(ErrorCode.FORBIDDEN, assertThrows(BusinessException.class, () -> service.detail(7L)).getCode());
    }

    @Test
    void missingDetailIsNotFound() {
        when(reservationMapper.findById(404L)).thenReturn(null);

        assertEquals(ErrorCode.RESOURCE_NOT_FOUND,
                assertThrows(BusinessException.class, () -> service.detail(404L)).getCode());
    }
}
