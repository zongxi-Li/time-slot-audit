package com.timeslot.common.time;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/** Wires the single adjustable business clock outside the web security configuration. */
@Configuration
public class SystemTimeConfiguration {
    @Bean
    Clock clock(SystemTimeService systemTimeService) {
        return new SystemTimeClock(systemTimeService, Clock.systemDefaultZone().getZone());
    }
}
