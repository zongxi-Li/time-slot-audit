/**
 * 文件职责：验证 ReservationQueryServiceTest 相关业务、接口安全或边界条件。
 * 接口：使用 JUnit、Mockito 或 Spring 测试工具，不属于运行时接口。
 */
package com.timeslot.reservation.service;

import com.timeslot.common.exception.BusinessException;
import com.timeslot.reservation.domain.Reservation;
import com.timeslot.reservation.domain.ReservationStatus;
import com.timeslot.reservation.mapper.ReservationMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ReservationQueryServiceTest {
    @Mock ReservationMapper reservationMapper;

    private ReservationQueryService service;
    private final LocalDateTime now = LocalDateTime.of(2026, 9, 15, 10, 0);

    @BeforeEach
    void setUp() {
        service = new ReservationQueryService(reservationMapper);
    }

    private Reservation reservation(Long id, ReservationStatus status, LocalDateTime start, LocalDateTime end) {
        Reservation reservation = new Reservation();
        reservation.setId(id);
        reservation.setStatus(status);
        reservation.setStartTime(start);
        reservation.setEndTime(end);
        return reservation;
    }

    @Test
    void requireReservationThrowsWhenMissing() {
        when(reservationMapper.findById(404L)).thenReturn(null);

        assertThrows(BusinessException.class, () -> service.requireReservation(404L));
    }

    @Test
    void requireReservationReturnsExisting() {
        Reservation existing = reservation(1L, ReservationStatus.CONFIRMED, now, now.plusHours(1));
        when(reservationMapper.findById(1L)).thenReturn(existing);

        assertEquals(1L, service.requireReservation(1L).getId());
    }

    @Test
    void endedBeforeKeepsOnlyConfirmedEndedReservations() {
        Reservation confirmedEnded = reservation(1L, ReservationStatus.CONFIRMED,
                now.minusHours(2), now.minusHours(1));
        Reservation stillOngoing = reservation(2L, ReservationStatus.CONFIRMED,
                now.minusMinutes(30), now.plusMinutes(30));
        Reservation pendingEnded = reservation(3L, ReservationStatus.PENDING,
                now.minusHours(2), now.minusHours(1));
        when(reservationMapper.findCalendar(any(LocalDateTime.class), any(LocalDateTime.class), isNull()))
                .thenReturn(List.of(confirmedEnded, stillOngoing, pendingEnded));

        List<Reservation> result = service.findConfirmedEndedBefore(now, 7);

        assertEquals(List.of(1L), result.stream().map(Reservation::getId).toList());
    }

    @Test
    void startingBetweenKeepsOnlyUpcomingConfirmedReservations() {
        Reservation upcoming = reservation(1L, ReservationStatus.CONFIRMED,
                now.plusMinutes(10), now.plusMinutes(70));
        Reservation alreadyStarted = reservation(2L, ReservationStatus.CONFIRMED,
                now.minusMinutes(10), now.plusMinutes(20));
        Reservation pending = reservation(3L, ReservationStatus.PENDING,
                now.plusMinutes(10), now.plusMinutes(70));
        when(reservationMapper.findCalendar(any(LocalDateTime.class), any(LocalDateTime.class), isNull()))
                .thenReturn(List.of(upcoming, alreadyStarted, pending));

        List<Reservation> result = service.findConfirmedStartingBetween(now, now.plusMinutes(30));

        assertEquals(List.of(1L), result.stream().map(Reservation::getId).toList());
    }
}
