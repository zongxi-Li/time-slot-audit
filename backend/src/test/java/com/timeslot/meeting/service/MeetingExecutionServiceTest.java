/**
 * 文件职责：验证 MeetingExecutionServiceTest 相关业务、接口安全或边界条件。
 * 接口：使用 JUnit、Mockito 或 Spring 测试工具，不属于运行时接口。
 */
package com.timeslot.meeting.service;

import com.timeslot.common.api.ErrorCode;
import com.timeslot.common.exception.BusinessException;
import com.timeslot.common.security.AuthenticatedUser;
import com.timeslot.common.security.CurrentUserProvider;
import com.timeslot.meeting.domain.Attendee;
import com.timeslot.meeting.domain.AttendeeRole;
import com.timeslot.meeting.domain.AttendeeStatus;
import com.timeslot.meeting.domain.MeetingExecutionRecord;
import com.timeslot.meeting.domain.NotificationType;
import com.timeslot.meeting.domain.UserRef;
import com.timeslot.meeting.dto.AddAttendeeRequest;
import com.timeslot.meeting.dto.AttendeeView;
import com.timeslot.meeting.dto.SaveMeetingExecutionRequest;
import com.timeslot.meeting.mapper.MeetingExecutionMapper;
import com.timeslot.reservation.domain.Reservation;
import com.timeslot.reservation.domain.ReservationStatus;
import com.timeslot.reservation.service.ReservationQueryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DuplicateKeyException;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MeetingExecutionServiceTest {
    private static final Long RESERVATION_ID = 100L;
    private static final Long ORGANIZER_ID = 2L;
    private static final Long ATTENDEE_ID = 3L;

    @Mock MeetingExecutionMapper attendeeMapper;
    @Mock ReservationQueryService reservationQueryService;
    @Mock NotificationService notificationService;
    @Mock CurrentUserProvider currentUserProvider;

    private MeetingExecutionService service;
    /** 服务器时间固定在会议开始时刻：2026-09-15 10:00。 */
    private final Clock clock = Clock.fixed(Instant.parse("2026-09-15T02:00:00Z"), ZoneId.of("Asia/Shanghai"));
    private final LocalDateTime now = LocalDateTime.now(clock);

    @BeforeEach
    void setUp() {
        service = new MeetingExecutionService(attendeeMapper, reservationQueryService,
                notificationService, currentUserProvider, clock);
        when(currentUserProvider.getRequired()).thenReturn(new AuthenticatedUser(ORGANIZER_ID, "zhangsan", "USER"));
        when(reservationQueryService.requireReservation(RESERVATION_ID))
                .thenReturn(reservation(ReservationStatus.CONFIRMED, now, now.plusHours(1)));
        when(attendeeMapper.findUserById(anyLong())).thenReturn(userRef(ATTENDEE_ID));
        when(attendeeMapper.findRow(anyLong(), anyLong())).thenReturn(null);
    }

    private Reservation reservation(ReservationStatus status, LocalDateTime start, LocalDateTime end) {
        Reservation reservation = new Reservation();
        reservation.setId(RESERVATION_ID);
        reservation.setRequestId("req-100");
        reservation.setReservationNo("RSV100");
        reservation.setRoomId(1L);
        reservation.setUserId(ORGANIZER_ID);
        reservation.setRoomName("A301");
        reservation.setTitle("项目周会");
        reservation.setStartTime(start);
        reservation.setEndTime(end);
        reservation.setParticipantCount(3);
        reservation.setStatus(status);
        return reservation;
    }

    private UserRef userRef(Long userId) {
        UserRef ref = new UserRef();
        ref.setUserId(userId);
        ref.setUsername("lisi");
        ref.setRealName("李四");
        return ref;
    }

    private Attendee row(Long userId, AttendeeRole role, AttendeeStatus status) {
        Attendee attendee = new Attendee();
        attendee.setId(userId * 10);
        attendee.setReservationId(RESERVATION_ID);
        attendee.setUserId(userId);
        attendee.setUsername("user" + userId);
        attendee.setRealName("用户" + userId);
        attendee.setAttendeeRole(role);
        attendee.setAttendanceStatus(status);
        return attendee;
    }

    // -----------------------------------------------------------------
    // 参与人管理
    // -----------------------------------------------------------------

    @Test
    void creatorAddsAttendeeAndNotifies() {
        when(attendeeMapper.findRow(RESERVATION_ID, ATTENDEE_ID))
                .thenReturn(row(ATTENDEE_ID, AttendeeRole.ATTENDEE, AttendeeStatus.EXPECTED));

        AttendeeView view = service.addAttendee(RESERVATION_ID, new AddAttendeeRequest(ATTENDEE_ID, null));

        assertEquals("EXPECTED", view.attendanceStatus());
        verify(attendeeMapper).insertAttendee(RESERVATION_ID, ATTENDEE_ID);
        verify(notificationService).notify(eq(ATTENDEE_ID), eq(NotificationType.ATTENDEE_ADDED),
                anyString(), anyString(), eq(RESERVATION_ID), isNull());
    }

    @Test
    void attendeeResolvedByUsername() {
        when(attendeeMapper.findUserByUsername("lisi")).thenReturn(userRef(ATTENDEE_ID));
        when(attendeeMapper.findRow(RESERVATION_ID, ATTENDEE_ID))
                .thenReturn(row(ATTENDEE_ID, AttendeeRole.ATTENDEE, AttendeeStatus.EXPECTED));

        service.addAttendee(RESERVATION_ID, new AddAttendeeRequest(null, "lisi"));

        verify(attendeeMapper).insertAttendee(RESERVATION_ID, ATTENDEE_ID);
    }

    @Test
    void duplicateAttendeeIsRejected() {
        when(attendeeMapper.insertAttendee(RESERVATION_ID, ATTENDEE_ID))
                .thenThrow(new DuplicateKeyException("uk_attendee_reservation_user"));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.addAttendee(RESERVATION_ID, new AddAttendeeRequest(ATTENDEE_ID, null)));

        assertEquals(ErrorCode.DUPLICATE_REQUEST, exception.getCode());
    }

    @Test
    void addAttendeeRequiresUserIdOrUsername() {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.addAttendee(RESERVATION_ID, new AddAttendeeRequest(null, " ")));

        assertEquals(ErrorCode.VALIDATION_ERROR, exception.getCode());
        verify(attendeeMapper, never()).insertAttendee(anyLong(), anyLong());
    }

    @Test
    void addAttendeeRejectedWhenCapReached() {
        when(attendeeMapper.countByReservationId(RESERVATION_ID)).thenReturn(3);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.addAttendee(RESERVATION_ID, new AddAttendeeRequest(ATTENDEE_ID, null)));

        assertEquals(ErrorCode.VALIDATION_ERROR, exception.getCode());
        verify(attendeeMapper, never()).insertAttendee(anyLong(), anyLong());
    }

    @Test
    void strangerCannotManageAttendees() {
        when(currentUserProvider.getRequired()).thenReturn(new AuthenticatedUser(9L, "wangwu", "USER"));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.addAttendee(RESERVATION_ID, new AddAttendeeRequest(ATTENDEE_ID, null)));

        assertEquals(ErrorCode.FORBIDDEN, exception.getCode());
        verify(attendeeMapper, never()).insertAttendee(anyLong(), anyLong());
    }

    @Test
    void addAttendeeRejectedOnCancelledReservation() {
        when(reservationQueryService.requireReservation(RESERVATION_ID))
                .thenReturn(reservation(ReservationStatus.CANCELLED, now, now.plusHours(1)));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.addAttendee(RESERVATION_ID, new AddAttendeeRequest(ATTENDEE_ID, null)));

        assertEquals(ErrorCode.RESERVATION_INVALID_STATE, exception.getCode());
        verify(attendeeMapper, never()).insertAttendee(anyLong(), anyLong());
    }

    @Test
    void addAttendeeRejectedAfterMeetingEnded() {
        when(reservationQueryService.requireReservation(RESERVATION_ID))
                .thenReturn(reservation(ReservationStatus.CONFIRMED, now.minusHours(2), now.minusHours(1)));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.addAttendee(RESERVATION_ID, new AddAttendeeRequest(ATTENDEE_ID, null)));

        assertEquals(ErrorCode.RESERVATION_INVALID_STATE, exception.getCode());
    }

    @Test
    void organizerCannotBeRemoved() {
        when(attendeeMapper.findRow(RESERVATION_ID, ORGANIZER_ID))
                .thenReturn(row(ORGANIZER_ID, AttendeeRole.ORGANIZER, AttendeeStatus.EXPECTED));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.removeAttendee(RESERVATION_ID, ORGANIZER_ID));

        assertEquals(ErrorCode.VALIDATION_ERROR, exception.getCode());
        verify(attendeeMapper, never()).deleteById(anyLong());
    }

    @Test
    void attendeeWithAttendanceFactCannotBeRemoved() {
        when(attendeeMapper.findRow(RESERVATION_ID, ATTENDEE_ID))
                .thenReturn(row(ATTENDEE_ID, AttendeeRole.ATTENDEE, AttendeeStatus.CHECKED_IN));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.removeAttendee(RESERVATION_ID, ATTENDEE_ID));

        assertEquals(ErrorCode.RESERVATION_INVALID_STATE, exception.getCode());
        verify(attendeeMapper, never()).deleteById(anyLong());
    }

    @Test
    void expectedAttendeeCanBeRemoved() {
        when(attendeeMapper.findRow(RESERVATION_ID, ATTENDEE_ID))
                .thenReturn(row(ATTENDEE_ID, AttendeeRole.ATTENDEE, AttendeeStatus.EXPECTED));

        service.removeAttendee(RESERVATION_ID, ATTENDEE_ID);

        verify(attendeeMapper).deleteById(ATTENDEE_ID * 10);
        verify(notificationService).notify(eq(ATTENDEE_ID), eq(NotificationType.ATTENDEE_REMOVED),
                anyString(), anyString(), eq(RESERVATION_ID), any());
    }

    // -----------------------------------------------------------------
    // 签到 / 签退
    // -----------------------------------------------------------------

    @Test
    void attendeeChecksInWithinWindow() {
        Attendee checkedIn = row(ORGANIZER_ID, AttendeeRole.ORGANIZER, AttendeeStatus.CHECKED_IN);
        checkedIn.setCheckInAt(now);
        when(attendeeMapper.findRow(RESERVATION_ID, ORGANIZER_ID))
                .thenReturn(row(ORGANIZER_ID, AttendeeRole.ORGANIZER, AttendeeStatus.EXPECTED), checkedIn);

        AttendeeView view = service.checkIn(RESERVATION_ID);

        assertEquals("CHECKED_IN", view.attendanceStatus());
        verify(attendeeMapper).markCheckedIn(eq(ORGANIZER_ID * 10), eq(now));
    }

    @Test
    void checkInOverridesNoShowInsideWindow() {
        // NO_SHOW 仅应在会议结束后判定；测试时钟回拨后窗口内会残留 NO_SHOW 行，签到需能翻案
        Attendee checkedIn = row(ORGANIZER_ID, AttendeeRole.ORGANIZER, AttendeeStatus.CHECKED_IN);
        checkedIn.setCheckInAt(now);
        when(attendeeMapper.findRow(RESERVATION_ID, ORGANIZER_ID))
                .thenReturn(row(ORGANIZER_ID, AttendeeRole.ORGANIZER, AttendeeStatus.NO_SHOW), checkedIn);

        AttendeeView view = service.checkIn(RESERVATION_ID);

        assertEquals("CHECKED_IN", view.attendanceStatus());
        verify(attendeeMapper).markCheckedIn(eq(ORGANIZER_ID * 10), eq(now));
    }

    @Test
    void checkInRejectedBeforeWindowOpens() {
        when(reservationQueryService.requireReservation(RESERVATION_ID))
                .thenReturn(reservation(ReservationStatus.CONFIRMED, now.plusMinutes(30), now.plusMinutes(90)));
        when(attendeeMapper.findRow(RESERVATION_ID, ORGANIZER_ID))
                .thenReturn(row(ORGANIZER_ID, AttendeeRole.ORGANIZER, AttendeeStatus.EXPECTED));

        BusinessException exception = assertThrows(BusinessException.class, () -> service.checkIn(RESERVATION_ID));

        assertEquals(ErrorCode.RESERVATION_INVALID_STATE, exception.getCode());
        verify(attendeeMapper, never()).markCheckedIn(anyLong(), any());
    }

    @Test
    void checkInRejectedAfterMeetingEnded() {
        when(reservationQueryService.requireReservation(RESERVATION_ID))
                .thenReturn(reservation(ReservationStatus.CONFIRMED, now.minusHours(2), now.minusHours(1)));
        when(attendeeMapper.findRow(RESERVATION_ID, ORGANIZER_ID))
                .thenReturn(row(ORGANIZER_ID, AttendeeRole.ORGANIZER, AttendeeStatus.EXPECTED));

        BusinessException exception = assertThrows(BusinessException.class, () -> service.checkIn(RESERVATION_ID));

        assertEquals(ErrorCode.RESERVATION_INVALID_STATE, exception.getCode());
        verify(attendeeMapper, never()).markCheckedIn(anyLong(), any());
    }

    @Test
    void nonAttendeeCannotCheckIn() {
        when(attendeeMapper.findRow(RESERVATION_ID, ORGANIZER_ID)).thenReturn(null);

        BusinessException exception = assertThrows(BusinessException.class, () -> service.checkIn(RESERVATION_ID));

        assertEquals(ErrorCode.FORBIDDEN, exception.getCode());
        verify(attendeeMapper, never()).markCheckedIn(anyLong(), any());
    }

    @Test
    void repeatedCheckInIsIdempotent() {
        Attendee checkedIn = row(ORGANIZER_ID, AttendeeRole.ORGANIZER, AttendeeStatus.CHECKED_IN);
        checkedIn.setCheckInAt(now.minusMinutes(5));
        when(attendeeMapper.findRow(RESERVATION_ID, ORGANIZER_ID)).thenReturn(checkedIn);

        AttendeeView view = service.checkIn(RESERVATION_ID);

        assertEquals("CHECKED_IN", view.attendanceStatus());
        verify(attendeeMapper, never()).markCheckedIn(anyLong(), any());
    }

    @Test
    void checkInRejectedOnPendingReservation() {
        when(reservationQueryService.requireReservation(RESERVATION_ID))
                .thenReturn(reservation(ReservationStatus.PENDING, now, now.plusHours(1)));

        BusinessException exception = assertThrows(BusinessException.class, () -> service.checkIn(RESERVATION_ID));

        assertEquals(ErrorCode.RESERVATION_INVALID_STATE, exception.getCode());
    }

    @Test
    void checkInRejectedOnCancelledReservation() {
        when(reservationQueryService.requireReservation(RESERVATION_ID))
                .thenReturn(reservation(ReservationStatus.CANCELLED, now, now.plusHours(1)));

        BusinessException exception = assertThrows(BusinessException.class, () -> service.checkIn(RESERVATION_ID));

        assertEquals(ErrorCode.RESERVATION_INVALID_STATE, exception.getCode());
    }

    @Test
    void attendeeChecksOutAfterCheckIn() {
        Attendee checkedIn = row(ORGANIZER_ID, AttendeeRole.ORGANIZER, AttendeeStatus.CHECKED_IN);
        Attendee checkedOut = row(ORGANIZER_ID, AttendeeRole.ORGANIZER, AttendeeStatus.CHECKED_OUT);
        checkedIn.setCheckInAt(now.minusMinutes(30));
        checkedOut.setCheckInAt(now.minusMinutes(30));
        checkedOut.setCheckOutAt(now);
        when(attendeeMapper.findRow(RESERVATION_ID, ORGANIZER_ID)).thenReturn(checkedIn, checkedOut);

        AttendeeView view = service.checkOut(RESERVATION_ID);

        assertEquals("CHECKED_OUT", view.attendanceStatus());
        verify(attendeeMapper).markCheckedOut(eq(ORGANIZER_ID * 10), eq(now));
    }

    @Test
    void checkOutRejectedWithoutCheckIn() {
        when(attendeeMapper.findRow(RESERVATION_ID, ORGANIZER_ID))
                .thenReturn(row(ORGANIZER_ID, AttendeeRole.ORGANIZER, AttendeeStatus.EXPECTED));

        BusinessException exception = assertThrows(BusinessException.class, () -> service.checkOut(RESERVATION_ID));

        assertEquals(ErrorCode.RESERVATION_INVALID_STATE, exception.getCode());
        verify(attendeeMapper, never()).markCheckedOut(anyLong(), any());
    }

    @Test
    void repeatedCheckOutIsIdempotent() {
        Attendee checkedOut = row(ORGANIZER_ID, AttendeeRole.ORGANIZER, AttendeeStatus.CHECKED_OUT);
        checkedOut.setCheckOutAt(now.minusMinutes(5));
        when(attendeeMapper.findRow(RESERVATION_ID, ORGANIZER_ID)).thenReturn(checkedOut);

        AttendeeView view = service.checkOut(RESERVATION_ID);

        assertEquals("CHECKED_OUT", view.attendanceStatus());
        verify(attendeeMapper, never()).markCheckedOut(anyLong(), any());
    }

    @Test
    void checkOutRejectedAfterGracePeriod() {
        when(reservationQueryService.requireReservation(RESERVATION_ID))
                .thenReturn(reservation(ReservationStatus.CONFIRMED, now.minusHours(3), now.minusHours(2)));
        when(attendeeMapper.findRow(RESERVATION_ID, ORGANIZER_ID))
                .thenReturn(row(ORGANIZER_ID, AttendeeRole.ORGANIZER, AttendeeStatus.CHECKED_IN));

        BusinessException exception = assertThrows(BusinessException.class, () -> service.checkOut(RESERVATION_ID));

        assertEquals(ErrorCode.RESERVATION_INVALID_STATE, exception.getCode());
        verify(attendeeMapper, never()).markCheckedOut(anyLong(), any());
    }

    // -----------------------------------------------------------------
    // No-Show 判定与开始提醒
    // -----------------------------------------------------------------

    @Test
    void endedReservationMarksExpectedAttendeesAsNoShow() {
        Reservation ended = reservation(ReservationStatus.CONFIRMED, now.minusHours(2), now.minusHours(1));
        when(reservationQueryService.findConfirmedEndedBefore(now, MeetingExecutionService.EXECUTION_LOOKBACK_DAYS))
                .thenReturn(List.of(ended));
        when(attendeeMapper.findByReservationId(RESERVATION_ID))
                .thenReturn(List.of(row(ATTENDEE_ID, AttendeeRole.ATTENDEE, AttendeeStatus.EXPECTED),
                        row(4L, AttendeeRole.ATTENDEE, AttendeeStatus.CHECKED_IN)));

        service.processDueExecutions(now);

        verify(attendeeMapper).markNoShowForExpected(RESERVATION_ID);
        ArgumentCaptor<String> dedupKeys = ArgumentCaptor.forClass(String.class);
        verify(notificationService).notify(eq(ATTENDEE_ID), eq(NotificationType.NO_SHOW_MARKED),
                anyString(), anyString(), eq(RESERVATION_ID), dedupKeys.capture());
        assertEquals("NO_SHOW_MARKED:100:3", dedupKeys.getValue());
        verify(notificationService, never()).notify(eq(4L), eq(NotificationType.NO_SHOW_MARKED),
                anyString(), anyString(), anyLong(), anyString());
    }

    @Test
    void repeatedNoShowPassHasNoDuplicateSideEffects() {
        Reservation ended = reservation(ReservationStatus.CONFIRMED, now.minusHours(2), now.minusHours(1));
        when(reservationQueryService.findConfirmedEndedBefore(now, MeetingExecutionService.EXECUTION_LOOKBACK_DAYS))
                .thenReturn(List.of(ended));
        when(attendeeMapper.findByReservationId(RESERVATION_ID))
                .thenReturn(List.of(row(ATTENDEE_ID, AttendeeRole.ATTENDEE, AttendeeStatus.NO_SHOW),
                        row(4L, AttendeeRole.ATTENDEE, AttendeeStatus.CHECKED_OUT)));

        service.processDueExecutions(now);
        service.processDueExecutions(now);

        verify(attendeeMapper, never()).markNoShowForExpected(RESERVATION_ID);
        verify(notificationService, never()).notify(anyLong(), eq(NotificationType.NO_SHOW_MARKED),
                anyString(), anyString(), anyLong(), anyString());
    }

    @Test
    void startReminderNotifiesWithDeterministicDedupKey() {
        Reservation starting = reservation(ReservationStatus.CONFIRMED, now.plusMinutes(10), now.plusMinutes(70));
        when(reservationQueryService.findConfirmedStartingBetween(eq(now), any(LocalDateTime.class)))
                .thenReturn(List.of(starting));
        when(attendeeMapper.findByReservationId(RESERVATION_ID))
                .thenReturn(List.of(row(ORGANIZER_ID, AttendeeRole.ORGANIZER, AttendeeStatus.EXPECTED),
                        row(ATTENDEE_ID, AttendeeRole.ATTENDEE, AttendeeStatus.EXPECTED)));

        service.processDueExecutions(now);
        service.processDueExecutions(now);

        ArgumentCaptor<String> organizerKeys = ArgumentCaptor.forClass(String.class);
        verify(notificationService, times(2)).notify(eq(ORGANIZER_ID),
                eq(NotificationType.MEETING_REMINDER), anyString(), anyString(), eq(RESERVATION_ID),
                organizerKeys.capture());
        assertEquals(organizerKeys.getAllValues().get(0), organizerKeys.getAllValues().get(1));

        ArgumentCaptor<String> attendeeKeys = ArgumentCaptor.forClass(String.class);
        verify(notificationService, times(2)).notify(eq(ATTENDEE_ID),
                eq(NotificationType.MEETING_REMINDER), anyString(), anyString(), eq(RESERVATION_ID),
                attendeeKeys.capture());
        assertEquals(attendeeKeys.getAllValues().get(0), attendeeKeys.getAllValues().get(1));
    }

    // -----------------------------------------------------------------
    // 出勤查询与其他
    // -----------------------------------------------------------------

    @Test
    void participantCanViewAttendanceWithSummary() {
        when(currentUserProvider.getRequired()).thenReturn(new AuthenticatedUser(ATTENDEE_ID, "lisi", "USER"));
        when(attendeeMapper.findByReservationId(RESERVATION_ID))
                .thenReturn(List.of(row(ORGANIZER_ID, AttendeeRole.ORGANIZER, AttendeeStatus.CHECKED_IN),
                        row(ATTENDEE_ID, AttendeeRole.ATTENDEE, AttendeeStatus.NO_SHOW)));

        var view = service.attendance(RESERVATION_ID);

        assertEquals(2, view.totalCount());
        assertEquals(0, view.expectedCount());
        assertEquals(1, view.checkedInCount());
        assertEquals(1, view.noShowCount());
        assertEquals("NO_SHOW", view.mine().attendanceStatus());
    }

    @Test
    void strangerCannotViewAttendance() {
        when(currentUserProvider.getRequired()).thenReturn(new AuthenticatedUser(9L, "wangwu", "USER"));
        when(attendeeMapper.findByReservationId(RESERVATION_ID))
                .thenReturn(List.of(row(ORGANIZER_ID, AttendeeRole.ORGANIZER, AttendeeStatus.EXPECTED)));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.attendance(RESERVATION_ID));

        assertEquals(ErrorCode.FORBIDDEN, exception.getCode());
    }

    @Test
    void organizerCanSaveActualMeetingExecutionRecordIdempotently() {
        LocalDateTime actualStart = now.minusHours(1);
        LocalDateTime actualEnd = now.minusMinutes(10);
        when(reservationQueryService.requireReservation(RESERVATION_ID))
                .thenReturn(reservation(ReservationStatus.CONFIRMED, now.minusHours(2), now.minusMinutes(5)));
        MeetingExecutionRecord saved = new MeetingExecutionRecord();
        saved.setReservationId(RESERVATION_ID);
        saved.setActualStartTime(actualStart);
        saved.setActualEndTime(actualEnd);
        saved.setActualAttendeeCount(2);
        saved.setRecordedBy(ORGANIZER_ID);
        when(attendeeMapper.findExecutionRecord(RESERVATION_ID)).thenReturn(saved);

        var view = service.saveExecutionRecord(RESERVATION_ID,
                new SaveMeetingExecutionRequest(actualStart, actualEnd, 2));

        assertEquals(actualStart, view.actualStartTime());
        assertEquals(actualEnd, view.actualEndTime());
        assertEquals(2, view.actualAttendeeCount());
        verify(attendeeMapper).upsertExecutionRecord(RESERVATION_ID, actualStart, actualEnd, 2, ORGANIZER_ID);
    }

    @Test
    void meetingNotEndedCannotSaveExecutionRecord() {
        when(reservationQueryService.requireReservation(RESERVATION_ID))
                .thenReturn(reservation(ReservationStatus.CONFIRMED, now.minusHours(1), now.plusHours(1)));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.saveExecutionRecord(RESERVATION_ID,
                        new SaveMeetingExecutionRequest(now.minusMinutes(30), now.minusMinutes(5), 1)));

        assertEquals(ErrorCode.RESERVATION_INVALID_STATE, exception.getCode());
        assertEquals("会议尚未结束，不能登记实际使用记录", exception.getMessage());
        verify(attendeeMapper, never()).upsertExecutionRecord(anyLong(), any(LocalDateTime.class),
                any(LocalDateTime.class), any(), anyLong());
    }

    @Test
    void actualExecutionRecordRejectsInvalidPeriod() {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.saveExecutionRecord(RESERVATION_ID,
                        new SaveMeetingExecutionRequest(now, now, 1)));

        assertEquals(ErrorCode.VALIDATION_ERROR, exception.getCode());
        verify(attendeeMapper, never()).upsertExecutionRecord(anyLong(), any(LocalDateTime.class),
                any(LocalDateTime.class), any(), anyLong());
    }

    @Test
    void actualExecutionRecordRejectsNegativeAttendeeCount() {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.saveExecutionRecord(RESERVATION_ID,
                        new SaveMeetingExecutionRequest(now.minusHours(1), now.minusMinutes(1), -1)));

        assertEquals(ErrorCode.VALIDATION_ERROR, exception.getCode());
        verify(attendeeMapper, never()).upsertExecutionRecord(anyLong(), any(LocalDateTime.class),
                any(LocalDateTime.class), any(), anyLong());
    }

    @Test
    void actualExecutionRecordRejectsFutureEndTime() {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.saveExecutionRecord(RESERVATION_ID,
                        new SaveMeetingExecutionRequest(now, now.plusMinutes(30), 1)));

        assertEquals(ErrorCode.RESERVATION_INVALID_STATE, exception.getCode());
        verify(attendeeMapper, never()).upsertExecutionRecord(anyLong(), any(LocalDateTime.class),
                any(LocalDateTime.class), any(), anyLong());
    }

    @Test
    void actualExecutionRecordRequiresOrganizerOrAdmin() {
        when(currentUserProvider.getRequired()).thenReturn(new AuthenticatedUser(ATTENDEE_ID, "lisi", "USER"));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.saveExecutionRecord(RESERVATION_ID,
                        new SaveMeetingExecutionRequest(now.minusHours(1), now.minusMinutes(1), 1)));

        assertEquals(ErrorCode.FORBIDDEN, exception.getCode());
        verify(attendeeMapper, never()).upsertExecutionRecord(anyLong(), any(LocalDateTime.class),
                any(LocalDateTime.class), any(), anyLong());
    }

    @Test
    void reservationMustExistForEveryOperation() {
        when(reservationQueryService.requireReservation(999L))
                .thenThrow(new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "预约不存在"));

        BusinessException exception = assertThrows(BusinessException.class, () -> service.checkIn(999L));

        assertEquals(ErrorCode.RESOURCE_NOT_FOUND, exception.getCode());
    }

    @Test
    void myMeetingsReturnsCurrentUsersExecutions() {
        when(attendeeMapper.findMyMeetings(ORGANIZER_ID)).thenReturn(List.of());

        assertEquals(0, service.myMeetings().size());
        verify(attendeeMapper).findMyMeetings(ORGANIZER_ID);
    }
}
