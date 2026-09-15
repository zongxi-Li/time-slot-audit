package com.timeslot.reservation.service;

import com.timeslot.common.api.ErrorCode;
import com.timeslot.common.exception.BusinessException;
import com.timeslot.reservation.dto.OccupancyInterval;
import com.timeslot.reservation.mapper.ReservationMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Public read side of reservation occupancy for cross-domain composition.
 * The resource domain must combine this with its own room availability rules
 * (open rules, status) to derive free slots / available rooms; it must not query
 * the reservation table directly.
 */
@Service
public class ReservationOccupancyService {
    private final ReservationMapper reservationMapper;

    public ReservationOccupancyService(ReservationMapper reservationMapper) {
        this.reservationMapper = reservationMapper;
    }

    /**
     * Active (PENDING/CONFIRMED) occupancy intervals overlapping the half-open
     * query window {@code [start, end)}; pass {@code roomId = null} for all rooms.
     */
    public List<OccupancyInterval> findActiveOccupancy(Long roomId, LocalDateTime start, LocalDateTime end) {
        requireRange(start, end);
        return reservationMapper.findActiveIntervals(start, end, roomId);
    }

    /** Half-open overlap against the active occupancy set: newStart < oldEnd AND newEnd > oldStart. */
    public boolean isOccupied(Long roomId, LocalDateTime start, LocalDateTime end) {
        requireRange(start, end);
        return reservationMapper.countConflicts(roomId, start, end) > 0;
    }

    private void requireRange(LocalDateTime start, LocalDateTime end) {
        if (start == null || end == null || !start.isBefore(end)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, HttpStatus.BAD_REQUEST, "时间范围无效");
        }
    }
}
