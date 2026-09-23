/**
 * 文件职责：管理领域调用预约生命周期能力的接口边界。
 * 接口：供 ReservationLifecycleAdapter 实现。
 * 方法：approve/reject/forceCancel 将管理员动作交给 reservation 领域处理。
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
