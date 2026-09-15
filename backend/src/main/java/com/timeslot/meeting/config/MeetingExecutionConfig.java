package com.timeslot.meeting.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** meeting 域调度开关：启用 Spring @Scheduled，驱动开始提醒与 No-Show 判定。 */
@Configuration
@EnableScheduling
public class MeetingExecutionConfig {
}
