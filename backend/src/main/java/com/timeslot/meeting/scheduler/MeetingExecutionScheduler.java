/**
 * 文件职责：定时扫描预约并推进会议执行状态、提醒和结束后的处理。
 * 接口：由 Spring Scheduler 定时触发。
 */
package com.timeslot.meeting.scheduler;

import com.timeslot.meeting.service.MeetingExecutionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 会议执行定时任务：每分钟扫描一次“即将开始提醒”与“结束后 No-Show 判定”。
 *
 * 判定逻辑全部收敛在 MeetingExecutionService.processDueExecutions(now)：
 * - 幂等（INSERT IGNORE 去重 + SQL 仅命中 EXPECTED 行），可重复执行无重复副作用；
 * - 不使用自旋线程常驻空转，由 Spring 调度器按固定延迟触发；
 * - 单次异常只记录日志，不中断后续调度。
 */
@Component
public class MeetingExecutionScheduler {
    private static final Logger log = LoggerFactory.getLogger(MeetingExecutionScheduler.class);

    private final MeetingExecutionService meetingExecutionService;

    public MeetingExecutionScheduler(MeetingExecutionService meetingExecutionService) {
        this.meetingExecutionService = meetingExecutionService;
    }

    @Scheduled(fixedDelay = 60_000, initialDelay = 30_000)
    public void execute() {
        try {
            meetingExecutionService.processDueExecutions(LocalDateTime.now());
        } catch (Exception exception) {
            log.error("Meeting execution scheduled pass failed", exception);
        }
    }
}
