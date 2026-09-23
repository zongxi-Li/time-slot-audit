/*
 * 文件职责：审批超时自动失效的定时触发器。
 * 接口：由 Spring Scheduler 定时调用，业务逻辑全部在 ReservationExpirationService。
 * 方法：execute 在调度时读取业务时钟并调用过期服务处理到期预约。
 */
package com.timeslot.reservation.scheduler;

import com.timeslot.reservation.service.ReservationExpirationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;

/**
 * 审批超时清扫：每分钟把“已结束仍未审批”的 PENDING 预约迁为 REJECTED。
 * 与 MeetingExecutionScheduler 同款约束：幂等可重复执行、单次异常只记日志不中断调度；
 * initialDelay 与会议执行任务错开，避免重启后同一时刻双任务抢行。
 */
@Component
public class ReservationExpirationScheduler {
    private static final Logger log = LoggerFactory.getLogger(ReservationExpirationScheduler.class);

    private final ReservationExpirationService expirationService;
    private final Clock clock;

    public ReservationExpirationScheduler(ReservationExpirationService expirationService, Clock clock) {
        this.expirationService = expirationService;
        this.clock = clock;
    }

    @Scheduled(fixedDelay = 60_000, initialDelay = 45_000)
    public void execute() {
        try {
            expirationService.processDueExpirations(LocalDateTime.now(clock));
        } catch (Exception exception) {
            log.error("Reservation expiration scheduled pass failed", exception);
        }
    }
}
