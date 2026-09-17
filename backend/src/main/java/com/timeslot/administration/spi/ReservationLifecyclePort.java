/**
 * 文件职责：定义管理员域调用预约生命周期变更的端口。
 * 接口：供 ReservationLifecycleAdapter 实现。
 */
package com.timeslot.administration.spi;

/**
 * Administration-side port for reservation-owned state changes.
 * An adapter must delegate to the reservation domain's public lifecycle service.
 */
public interface ReservationLifecyclePort {
    void approve(Long reservationId, Long operatorId);

    void reject(Long reservationId, Long operatorId, String reason);

    void forceCancel(Long reservationId, Long operatorId, String reason);
}
