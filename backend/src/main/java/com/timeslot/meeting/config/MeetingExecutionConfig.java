/**
 * 文件职责：配置会议执行模块所需的 Spring Bean 和定时任务基础设施。
 * 接口：被 Spring Boot 或本域定时任务加载。
 * 方法：无显式业务方法；@EnableScheduling 开启本模块 @Scheduled 定时任务。
 */

package com.timeslot.meeting.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** meeting 域调度开关：启用 Spring @Scheduled，驱动开始提醒与 No-Show 判定。 */
@Configuration
@EnableScheduling
public class MeetingExecutionConfig {
}
