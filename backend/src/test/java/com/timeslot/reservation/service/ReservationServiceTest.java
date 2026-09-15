package com.timeslot.reservation.service;

import com.timeslot.common.api.ErrorCode;
import com.timeslot.common.exception.BusinessException;
import com.timeslot.common.security.AuthenticatedUser;
import com.timeslot.common.security.CurrentUserProvider;
import com.timeslot.reservation.domain.Reservation;
import com.timeslot.reservation.domain.ReservationStatus;
import com.timeslot.reservation.dto.CreateReservationRequest;
import com.timeslot.reservation.dto.ReservationResponse;
import com.timeslot.reservation.mapper.ReservationMapper;
import com.timeslot.resource.domain.MeetingRoom;
import com.timeslot.resource.domain.MeetingRoomStatus;
import com.timeslot.resource.domain.RoomCategory;
import com.timeslot.resource.domain.RoomOpenRule;
import com.timeslot.resource.service.ResourceQueryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
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
    @Mock ResourceQueryService resourceQueryService;
    @Mock CurrentUserProvider currentUserProvider;

    private ReservationService service;
    private final Clock clock = Clock.fixed(Instant.parse("2026-09-11T02:00:00Z"), ZoneId.of("Asia/Shanghai"));
    private final MeetingRoom room = new MeetingRoom(1L, 1L, "A301", "教学楼A栋3层", 8,
            MeetingRoomStatus.AVAILABLE, "小型会议室", List.of("投影仪"));
    private final RoomCategory normalCategory = new RoomCategory(1L, "小型会议室", false, 120, 7);
    private final RoomOpenRule openRule = new RoomOpenRule(LocalTime.of(8, 0), LocalTime.of(19, 0), true);

    @BeforeEach
    void setUp() {
        service = new ReservationService(reservationMapper, resourceQueryService, currentUserProvider, clock);
        when(currentUserProvider.getRequired()).thenReturn(new AuthenticatedUser(2L, "zhangsan", "USER"));
        when(reservationMapper.findByUserIdAndRequestId(anyLong(), anyString())).thenReturn(null);
        when(resourceQueryService.lockRoom(1L)).thenReturn(room);
        when(resourceQueryService.getCategory(1L)).thenReturn(normalCategory);
        when(resourceQueryService.getOpenRule(anyLong(), anyInt())).thenReturn(openRule);
        when(reservationMapper.countConflicts(anyLong(), any(), any())).thenReturn(0);
    }

    private CreateReservationRequest request() {
        return new CreateReservationRequest("request-1", 1L, "课程讨论",
                LocalDateTime.of(2026, 9, 12, 10, 0), LocalDateTime.of(2026, 9, 12, 11, 0), 6, "备注");
    }

    @Test
    void createsConfirmedReservationForNormalRoom() {
        ReservationResponse response = service.createReservation(request());

        assertEquals("CONFIRMED", response.status());
        verify(reservationMapper).insert(any(Reservation.class));
        InOrder order = inOrder(resourceQueryService, reservationMapper);
        order.verify(resourceQueryService).lockRoom(1L);
        order.verify(reservationMapper).countConflicts(anyLong(), any(), any());
    }

    @Test
    void approvalRoomCreatesPendingReservation() {
        when(resourceQueryService.getCategory(1L)).thenReturn(new RoomCategory(1L, "大型会议室", true, 240, 14));

        assertEquals("PENDING", service.createReservation(request()).status());
    }

    @Test
    void conflictReturnsConflictCodeAndDoesNotInsert() {
        when(reservationMapper.countConflicts(anyLong(), any(), any())).thenReturn(1);

        BusinessException exception = assertThrows(BusinessException.class, () -> service.createReservation(request()));

        assertEquals(ErrorCode.RESERVATION_TIME_CONFLICT, exception.getCode());
        verify(reservationMapper, never()).insert(any());
    }

    @Test
    void maintenanceRoomIsRejected() {
        when(resourceQueryService.lockRoom(1L)).thenReturn(new MeetingRoom(1L, 1L, "A301", "", 8,
                MeetingRoomStatus.MAINTENANCE, "小型会议室", List.of()));

        BusinessException exception = assertThrows(BusinessException.class, () -> service.createReservation(request()));

        assertEquals(ErrorCode.ROOM_UNAVAILABLE, exception.getCode());
    }

    @Test
    void capacityExceededIsRejected() {
        CreateReservationRequest tooMany = new CreateReservationRequest("request-1", 1L, "课程讨论",
                request().startTime(), request().endTime(), 9, null);

        BusinessException exception = assertThrows(BusinessException.class, () -> service.createReservation(tooMany));

        assertEquals(ErrorCode.ROOM_CAPACITY_EXCEEDED, exception.getCode());
    }

    @Test
    void invalidTimeIsRejected() {
        CreateReservationRequest invalid = new CreateReservationRequest("request-1", 1L, "课程讨论",
                request().endTime(), request().startTime(), 3, null);

        assertEquals(ErrorCode.VALIDATION_ERROR, assertThrows(BusinessException.class,
                () -> service.createReservation(invalid)).getCode());
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
        verify(resourceQueryService, never()).lockRoom(anyLong());
        verify(reservationMapper, never()).insert(any());
    }

    @Test
    void sameRequestIdFromDifferentUsersCreatesIndependently() {
        service.createReservation(request());

        when(currentUserProvider.getRequired()).thenReturn(new AuthenticatedUser(3L, "lisi", "USER"));
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
        verify(resourceQueryService).lockRoom(1L);
        verify(reservationMapper, never()).countConflicts(anyLong(), any(), any());
        verify(reservationMapper, never()).insert(any());
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
        verify(reservationMapper).updateStatus(7L, "CANCELLED", null);
    }

    @Test
    void otherUserCannotCancelReservation() {
        when(reservationMapper.findByIdForUpdate(7L)).thenReturn(ownedReservation(3L));

        BusinessException exception = assertThrows(BusinessException.class, () -> service.cancel(7L, null));

        assertEquals(ErrorCode.FORBIDDEN, exception.getCode());
        verify(reservationMapper, never()).updateStatus(anyLong(), anyString(), any());
    }

    @Test
    void adminCannotCancelOthersReservationViaNormalEndpoint() {
        when(currentUserProvider.getRequired()).thenReturn(new AuthenticatedUser(1L, "admin", "ADMIN"));
        when(reservationMapper.findByIdForUpdate(7L)).thenReturn(ownedReservation(2L));

        BusinessException exception = assertThrows(BusinessException.class, () -> service.cancel(7L, null));

        assertEquals(ErrorCode.FORBIDDEN, exception.getCode());
        verify(reservationMapper, never()).updateStatus(anyLong(), anyString(), any());
    }
}
