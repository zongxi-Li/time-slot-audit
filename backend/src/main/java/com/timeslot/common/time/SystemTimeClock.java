/**
 * 文件职责：把 SystemTimeService 适配为 java.time.Clock，供依赖注入的业务使用。
 * 方法：instant 返回业务时钟时刻；getZone 返回时区；withZone 创建指定时区的时钟。
 */
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
