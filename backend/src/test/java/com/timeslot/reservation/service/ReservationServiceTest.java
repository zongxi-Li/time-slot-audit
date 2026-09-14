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
import com.timeslot.resource.dto.BookableRoomProfile;
import com.timeslot.resource.dto.BookingOpenWindow;
import com.timeslot.resource.service.ResourceBookingQueryService;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ReservationServiceTest {
    @Mock ReservationMapper reservationMapper;
    @Mock ResourceBookingQueryService resourceBookingQueryService;
    @Mock CurrentUserProvider currentUserProvider;

    private ReservationService service;
    private final Clock clock = Clock.fixed(Instant.parse("2026-09-11T02:00:00Z"), ZoneId.of("Asia/Shanghai"));
    /** Small normal room: no approval required, max 120 minutes, 7 days in advance, open 08:00-19:00. */
    private final BookableRoomProfile room = new BookableRoomProfile(1L, "A301", 8, "AVAILABLE",
            false, 120, 7);
    private final BookingOpenWindow openWindow = new BookingOpenWindow(LocalTime.of(8, 0), LocalTime.of(19, 0), true);

    @BeforeEach
    void setUp() {
        service = new ReservationService(reservationMapper, resourceBookingQueryService, currentUserProvider, clock);
        when(currentUserProvider.getRequired()).thenReturn(new AuthenticatedUser(2L, "zhangsan", "USER"));
        when(reservationMapper.findByRequestId(anyString())).thenReturn(null);
        when(resourceBookingQueryService.lockBookableRoom(1L)).thenReturn(room);
        when(resourceBookingQueryService.getOpenWindow(anyLong(), anyInt())).thenReturn(openWindow);
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
        InOrder order = inOrder(resourceBookingQueryService, reservationMapper);
        order.verify(resourceBookingQueryService).lockBookableRoom(1L);
        order.verify(reservationMapper).countConflicts(anyLong(), any(), any());
    }

    @Test
    void approvalRoomCreatesPendingReservation() {
        when(resourceBookingQueryService.lockBookableRoom(1L)).thenReturn(new BookableRoomProfile(1L, "A101", 30,
                "AVAILABLE", true, 240, 14));

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
        when(resourceBookingQueryService.lockBookableRoom(1L)).thenReturn(new BookableRoomProfile(1L, "A301", 8,
                "MAINTENANCE", false, 120, 7));

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
    void closedOpenWindowIsRejected() {
        when(resourceBookingQueryService.getOpenWindow(anyLong(), anyInt()))
                .thenReturn(new BookingOpenWindow(LocalTime.of(8, 0), LocalTime.of(19, 0), false));

        assertEquals(ErrorCode.VALIDATION_ERROR, assertThrows(BusinessException.class,
                () -> service.createReservation(request())).getCode());
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
        when(reservationMapper.findByRequestId("request-1")).thenReturn(existing);

        assertEquals(42L, service.createReservation(request()).id());
        verify(resourceBookingQueryService, never()).lockBookableRoom(anyLong());
        verify(reservationMapper, never()).insert(any());
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
        when(reservationMapper.findByRequestId("request-1")).thenReturn(null, existing);

        assertEquals(43L, service.createReservation(request()).id());
        verify(resourceBookingQueryService).lockBookableRoom(1L);
        verify(reservationMapper, never()).countConflicts(anyLong(), any(), any());
        verify(reservationMapper, never()).insert(any());
    }
}
