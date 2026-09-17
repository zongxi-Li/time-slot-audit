/**
 * 文件职责：定义 会议执行 的运行配置。
 * 接口：被 Spring Boot 或本域定时任务加载。
 */
package com.timeslot.meeting.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** meeting 域调度开关：启用 Spring @Scheduled，驱动开始提醒与 No-Show 判定。 */
@Configuration
@EnableScheduling
public class MeetingExecutionConfig {
}
