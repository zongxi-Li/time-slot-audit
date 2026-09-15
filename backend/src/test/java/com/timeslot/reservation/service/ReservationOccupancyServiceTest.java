package com.timeslot.reservation.service;

import com.timeslot.common.api.ErrorCode;
import com.timeslot.common.exception.BusinessException;
import com.timeslot.reservation.dto.OccupancyInterval;
import com.timeslot.reservation.mapper.ReservationMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReservationOccupancyServiceTest {
    @Mock ReservationMapper reservationMapper;

    private ReservationOccupancyService service;

    @BeforeEach
    void setUp() {
        service = new ReservationOccupancyService(reservationMapper);
    }

    @Test
    void returnsActiveOccupancyForRoomWindow() {
        LocalDateTime start = LocalDateTime.of(2026, 9, 15, 8, 0);
        LocalDateTime end = LocalDateTime.of(2026, 9, 15, 19, 0);
        when(reservationMapper.findActiveIntervals(start, end, 1L)).thenReturn(List.of(
                new OccupancyInterval(1L, LocalDateTime.of(2026, 9, 15, 10, 0), LocalDateTime.of(2026, 9, 15, 11, 30))));

        List<OccupancyInterval> occupancy = service.findActiveOccupancy(1L, start, end);

        assertEquals(1, occupancy.size());
        assertEquals(LocalDateTime.of(2026, 9, 15, 10, 0), occupancy.get(0).getStartTime());
        verify(reservationMapper).findActiveIntervals(start, end, 1L);
    }

    @Test
    void invalidRangeIsRejected() {
        LocalDateTime time = LocalDateTime.of(2026, 9, 15, 10, 0);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.findActiveOccupancy(1L, time, time));

        assertEquals(ErrorCode.VALIDATION_ERROR, exception.getCode());
    }

    @Test
    void isOccupiedDelegatesToHalfOpenOverlapCount() {
        when(reservationMapper.countConflicts(1L,
                LocalDateTime.of(2026, 9, 15, 11, 0), LocalDateTime.of(2026, 9, 15, 12, 0))).thenReturn(1);

        assertTrue(service.isOccupied(1L, LocalDateTime.of(2026, 9, 15, 11, 0), LocalDateTime.of(2026, 9, 15, 12, 0)));
        assertFalse(service.isOccupied(1L, LocalDateTime.of(2026, 9, 15, 13, 0), LocalDateTime.of(2026, 9, 15, 14, 0)));
    }
}
