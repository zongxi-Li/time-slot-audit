package com.timeslot.reservation.service;

import com.timeslot.common.api.ErrorCode;
import com.timeslot.common.exception.BusinessException;
import com.timeslot.reservation.domain.Reservation;
import com.timeslot.reservation.domain.ReservationStatus;
import com.timeslot.reservation.dto.ReservationResponse;
import com.timeslot.reservation.mapper.ReservationMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ReservationLifecycleServiceTest {
    @Mock ReservationMapper reservationMapper;

    private ReservationLifecycleService service;
    private final Clock clock = Clock.fixed(Instant.parse("2026-09-11T02:00:00Z"), ZoneId.of("Asia/Shanghai"));

    @BeforeEach
    void setUp() {
        service = new ReservationLifecycleService(reservationMapper, clock);
    }

    private Reservation reservation(ReservationStatus status) {
        Reservation reservation = new Reservation();
        reservation.setId(9L);
        reservation.setRequestId("request-9");
        reservation.setReservationNo("RSV9");
        reservation.setRoomId(5L);
        reservation.setUserId(3L);
        reservation.setRoomName("B502");
        reservation.setTitle("全院月度总结会");
        reservation.setStartTime(LocalDateTime.of(2026, 9, 13, 14, 0));
        reservation.setEndTime(LocalDateTime.of(2026, 9, 13, 16, 0));
        reservation.setParticipantCount(45);
        reservation.setStatus(status);
        return reservation;
    }

    @Test
    void approveTransitionsPendingToConfirmed() {
        when(reservationMapper.findByIdForUpdate(9L)).thenReturn(reservation(ReservationStatus.PENDING));

        ReservationResponse response = service.approve(9L, 1L);

        assertEquals("CONFIRMED", response.status());
        verify(reservationMapper).updateStatus(9L, "CONFIRMED", null);
    }

    @Test
    void approveRejectsNonPendingReservation() {
        when(reservationMapper.findByIdForUpdate(9L)).thenReturn(reservation(ReservationStatus.CONFIRMED));

        BusinessException exception = assertThrows(BusinessException.class, () -> service.approve(9L, 1L));

        assertEquals(ErrorCode.RESERVATION_INVALID_STATE, exception.getCode());
        verify(reservationMapper, never()).updateStatus(anyLong(), anyString(), any());
    }

    @Test
    void rejectTransitionsPendingToRejectedWithReason() {
        when(reservationMapper.findByIdForUpdate(9L)).thenReturn(reservation(ReservationStatus.PENDING));

        ReservationResponse response = service.reject(9L, 1L, "该时段需预留场地维护");

        assertEquals("REJECTED", response.status());
        verify(reservationMapper).updateStatus(9L, "REJECTED", "该时段需预留场地维护");
    }

    @Test
    void rejectRequiresReason() {
        when(reservationMapper.findByIdForUpdate(9L)).thenReturn(reservation(ReservationStatus.PENDING));

        BusinessException exception = assertThrows(BusinessException.class, () -> service.reject(9L, 1L, "  "));

        assertEquals(ErrorCode.VALIDATION_ERROR, exception.getCode());
        verify(reservationMapper, never()).updateStatus(anyLong(), anyString(), any());
    }

    @Test
    void forceCancelTransitionsConfirmedToCancelledWithReason() {
        when(reservationMapper.findByIdForUpdate(9L)).thenReturn(reservation(ReservationStatus.CONFIRMED));

        assertEquals("CANCELLED", service.forceCancel(9L, 1L, "设备检修").status());
        verify(reservationMapper).updateStatus(eq(9L), eq("CANCELLED"), eq("设备检修"));
    }

    @Test
    void forceCancelAlsoAppliesToPending() {
        when(reservationMapper.findByIdForUpdate(9L)).thenReturn(reservation(ReservationStatus.PENDING));

        assertEquals("CANCELLED", service.forceCancel(9L, 1L, "活动取消").status());
    }

    @Test
    void forceCancelRequiresReason() {
        when(reservationMapper.findByIdForUpdate(9L)).thenReturn(reservation(ReservationStatus.CONFIRMED));

        BusinessException exception = assertThrows(BusinessException.class, () -> service.forceCancel(9L, 1L, null));

        assertEquals(ErrorCode.VALIDATION_ERROR, exception.getCode());
        verify(reservationMapper, never()).updateStatus(anyLong(), anyString(), any());
    }

    @Test
    void forceCancelRejectsTerminalStates() {
        when(reservationMapper.findByIdForUpdate(9L)).thenReturn(reservation(ReservationStatus.CANCELLED));

        BusinessException exception = assertThrows(BusinessException.class, () -> service.forceCancel(9L, 1L, "重复取消"));

        assertEquals(ErrorCode.RESERVATION_INVALID_STATE, exception.getCode());
    }

    @Test
    void missingReservationIsNotFound() {
        when(reservationMapper.findByIdForUpdate(404L)).thenReturn(null);

        BusinessException exception = assertThrows(BusinessException.class, () -> service.approve(404L, 1L));

        assertEquals(ErrorCode.RESOURCE_NOT_FOUND, exception.getCode());
    }
}
