/**
 * 文件职责：把管理领域的生命周期端口接到 reservation 领域服务。
 * 接口：实现 ReservationLifecyclePort。
 * 方法：approve、reject、forceCancel 分别转发审批通过、驳回和强制取消。
 */

package com.timeslot.administration.spi;

import com.timeslot.reservation.service.ReservationLifecycleService;
import org.springframework.stereotype.Component;

/**
 * Delegates administration-driven reservation state changes to the reservation
 * domain's public lifecycle service (the only write entry for cross-domain
 * status transitions).
 */
@Component
public class ReservationLifecycleAdapter implements ReservationLifecyclePort {

    private final ReservationLifecycleService lifecycleService;

    public ReservationLifecycleAdapter(ReservationLifecycleService lifecycleService) {
        this.lifecycleService = lifecycleService;
    }

    @Override
    public void approve(Long reservationId, Long operatorId) {
        lifecycleService.approve(reservationId, operatorId);
    }

    @Override
    public void reject(Long reservationId, Long operatorId, String reason) {
        lifecycleService.reject(reservationId, operatorId, reason);
    }

    @Override
    public void forceCancel(Long reservationId, Long operatorId, String reason) {
        lifecycleService.forceCancel(reservationId, operatorId, reason);
    }
}
