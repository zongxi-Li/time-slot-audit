/**
 * 文件职责：注册业务时钟及系统时间服务所需的 Spring Bean。
 * 方法：clock 注册由 SystemTimeService 驱动的业务 Clock，供预约校验和定时任务使用。
 */
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
