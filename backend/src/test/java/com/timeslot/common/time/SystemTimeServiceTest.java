package com.timeslot.common.time;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SystemTimeServiceTest {
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final Clock WALL_CLOCK = Clock.fixed(Instant.parse("2026-09-18T01:00:00Z"), ZONE);

    @Mock
    private SystemTimeMapper mapper;

    private SystemTimeService service;

    @BeforeEach
    void setUp() {
        when(mapper.findFixedTime()).thenReturn(null);
        service = new SystemTimeService(mapper, WALL_CLOCK);
        service.initialize();
    }

    @Test
    void defaultsToRealtimeWallClock() {
        assertFalse(service.isFixed());
        assertEquals(LocalDateTime.of(2026, 9, 18, 9, 0), service.now());
        assertEquals(SystemTimeMode.REALTIME, service.snapshot().mode());
    }

    @Test
    void fixedTimeDrivesSnapshotAndDelegatingClock() {
        LocalDateTime target = LocalDateTime.of(2026, 9, 20, 10, 30);

        SystemTimeResponse response = service.setFixedTime(target);
        Clock clock = new SystemTimeClock(service, ZONE);

        assertTrue(service.isFixed());
        assertEquals(target, response.currentTime());
        assertEquals(SystemTimeMode.FIXED, response.mode());
        assertEquals(target, LocalDateTime.now(clock));
        verify(mapper).saveFixedTime(target);
    }

    @Test
    void resetReturnsToWallClock() {
        service.setFixedTime(LocalDateTime.of(2026, 9, 20, 10, 30));

        SystemTimeResponse response = service.resetToRealtime();

        assertFalse(service.isFixed());
        assertEquals(LocalDateTime.of(2026, 9, 18, 9, 0), response.currentTime());
        assertEquals(SystemTimeMode.REALTIME, response.mode());
        verify(mapper).saveFixedTime(null);
    }
}
