/*
 * 文件职责：审批超时自动失效的调度编排——把已结束仍未审批的 PENDING 迁移为 REJECTED 并通知预约人。
 * 接口：由 ReservationExpirationScheduler 每分钟触发，也可在测试中直接驱动。
 * 方法：processDueExpirations 查询并推进已结束或超时预约的生命周期。
 */
package com.timeslot.reservation.service;

import com.timeslot.reservation.domain.Reservation;
import com.timeslot.reservation.spi.ReservationNotificationPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 为什么由系统自动驳回：PENDING 过了时段后已无审批意义（通过/驳回都被
 * “预约已开始，不能审批”拦截，强制取消被“预约已结束”拦截），留在待审批列表只会永久堆积。
 * 复用状态机既有的 REJECTED 终态，前端置灰展示与统计口径零改动。
 *
 * 审计取舍：approval_record.approver_id / operation_log.user_id 都是 NOT NULL 的人工操作字段，
 * 系统动作不伪造操作人，故不写这两张表；预约人收到驳回通知，理由即 {@link #EXPIRE_REASON}。
 */
@Service
public class ReservationExpirationService {
    public static final String EXPIRE_REASON = "审批超时：预约时段已结束仍未审批，系统自动驳回";

    private static final Logger log = LoggerFactory.getLogger(ReservationExpirationService.class);

    private final ReservationQueryService reservationQueryService;
    private final ReservationLifecycleService lifecycleService;
    private final ReservationNotificationPort notificationPort;

    public ReservationExpirationService(ReservationQueryService reservationQueryService,
                                        ReservationLifecycleService lifecycleService,
                                        ReservationNotificationPort notificationPort) {
        this.reservationQueryService = reservationQueryService;
        this.lifecycleService = lifecycleService;
        this.notificationPort = notificationPort;
    }

    /** 单条失败只记日志，不阻断同批其余预约的失效处理。 */
    public void processDueExpirations(LocalDateTime now) {
        List<Reservation> candidates = reservationQueryService.findPendingEndedBefore(now);
        for (Reservation reservation : candidates) {
            try {
                // 守卫未命中（人工已审批/驳回或刚被改期）时返回 false，静默跳过且不通知
                if (lifecycleService.expireIfEnded(reservation.getId(), now)) {
                    notificationPort.notifyRejected(reservation.getUserId(), reservation.getId(),
                            reservation.getTitle(), EXPIRE_REASON);
                }
            } catch (Exception exception) {
                log.error("Auto-expiring pending reservation {} failed", reservation.getId(), exception);
            }
        }
    }
}
