package com.timeslot.reservation.domain;

import java.time.LocalDateTime;

public record TimeInterval(LocalDateTime start, LocalDateTime end) {
    public TimeInterval {
        if (start == null || end == null || !start.isBefore(end)) {
            throw new IllegalArgumentException("start must be before end");
        }
    }

    public boolean overlaps(TimeInterval other) {
        return start.isBefore(other.end()) && end.isAfter(other.start());
    }
}
