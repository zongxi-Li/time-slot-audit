package com.timeslot.reservation.domain;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TimeIntervalTest {
    private static final LocalDateTime TEN = LocalDateTime.of(2026, 9, 15, 10, 0);
    private static final LocalDateTime ELEVEN = LocalDateTime.of(2026, 9, 15, 11, 0);
    private static final LocalDateTime TWELVE = LocalDateTime.of(2026, 9, 15, 12, 0);

    @Test void completelySameIntervalsOverlap() {
        assertTrue(new TimeInterval(TEN, ELEVEN).overlaps(new TimeInterval(TEN, ELEVEN)));
    }

    @Test void leftOverlapIsDetected() {
        assertTrue(new TimeInterval(TEN, ELEVEN).overlaps(new TimeInterval(LocalDateTime.of(2026, 9, 15, 9, 30), LocalDateTime.of(2026, 9, 15, 10, 30))));
    }

    @Test void rightOverlapIsDetected() {
        assertTrue(new TimeInterval(TEN, ELEVEN).overlaps(new TimeInterval(LocalDateTime.of(2026, 9, 15, 10, 30), LocalDateTime.of(2026, 9, 15, 11, 30))));
    }

    @Test void containingIntervalOverlaps() {
        assertTrue(new TimeInterval(TEN, ELEVEN).overlaps(new TimeInterval(LocalDateTime.of(2026, 9, 15, 9, 0), TWELVE)));
    }

    @Test void containedIntervalOverlaps() {
        assertTrue(new TimeInterval(LocalDateTime.of(2026, 9, 15, 9, 0), TWELVE).overlaps(new TimeInterval(TEN, ELEVEN)));
    }

    @Test void touchingIntervalsDoNotOverlap() {
        assertFalse(new TimeInterval(TEN, ELEVEN).overlaps(new TimeInterval(ELEVEN, TWELVE)));
    }

    @Test void separatedIntervalsDoNotOverlap() {
        assertFalse(new TimeInterval(TEN, ELEVEN).overlaps(new TimeInterval(TWELVE, LocalDateTime.of(2026, 9, 15, 13, 0))));
    }
}
