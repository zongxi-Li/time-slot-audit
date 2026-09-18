package com.timeslot.common.time;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 业务时间的唯一来源。未设置测试时间时跟随物理时钟；设置后固定在指定时刻并持久化。
 * 认证令牌仍使用物理时间，避免调试时间跳转导致当前登录态失效。
 */
@Service
public class SystemTimeService {
    private final SystemTimeMapper mapper;
    private final Clock wallClock;
    private final ZoneId zone;
    private final AtomicReference<Instant> fixedInstant = new AtomicReference<>();

    @Autowired
    public SystemTimeService(SystemTimeMapper mapper) {
        this(mapper, Clock.systemDefaultZone());
    }

    SystemTimeService(SystemTimeMapper mapper, Clock wallClock) {
        this.mapper = mapper;
        this.wallClock = wallClock;
        this.zone = wallClock.getZone();
    }

    @PostConstruct
    void initialize() {
        mapper.ensureConfigRow();
        apply(mapper.findFixedTime());
    }

    public Instant instant() {
        Instant fixed = fixedInstant.get();
        return fixed == null ? wallClock.instant() : fixed;
    }

    public LocalDateTime now() {
        return LocalDateTime.ofInstant(instant(), zone);
    }

    public boolean isFixed() {
        return fixedInstant.get() != null;
    }

    @Transactional
    public synchronized SystemTimeResponse setFixedTime(LocalDateTime fixedTime) {
        mapper.saveFixedTime(fixedTime);
        apply(fixedTime);
        return snapshot();
    }

    @Transactional
    public synchronized SystemTimeResponse resetToRealtime() {
        mapper.saveFixedTime(null);
        fixedInstant.set(null);
        return snapshot();
    }

    public SystemTimeResponse snapshot() {
        return new SystemTimeResponse(now(), isFixed() ? SystemTimeMode.FIXED : SystemTimeMode.REALTIME);
    }

    private void apply(LocalDateTime fixedTime) {
        fixedInstant.set(fixedTime == null ? null : fixedTime.atZone(zone).toInstant());
    }
}
