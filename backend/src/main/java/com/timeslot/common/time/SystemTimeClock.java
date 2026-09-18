package com.timeslot.common.time;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

/** Delegates all business Clock reads to the adjustable system time. */
public final class SystemTimeClock extends Clock {
    private final SystemTimeService systemTimeService;
    private final ZoneId zone;

    public SystemTimeClock(SystemTimeService systemTimeService, ZoneId zone) {
        this.systemTimeService = systemTimeService;
        this.zone = zone;
    }

    @Override
    public ZoneId getZone() {
        return zone;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return this.zone.equals(zone) ? this : new SystemTimeClock(systemTimeService, zone);
    }

    @Override
    public Instant instant() {
        return systemTimeService.instant();
    }
}
