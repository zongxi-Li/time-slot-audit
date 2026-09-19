/**
 * 文件职责：预约域对外通知出端口（outbound port）。
 * 接口：由 reservation / administration 域在生命周期关键节点调用，实现由 meeting 域提供。
 *
 * 设计动机：meeting 域已单向依赖 reservation（MeetingExecutionService -> ReservationQueryService），
 * 若 reservation 直接注入 meeting 的 NotificationService 会形成模块级循环依赖。改为 reservation
 * 定义本端口（仅接口，本模块自持），由 meeting 提供适配器实现，彻底解耦。
 */
package com.timeslot.reservation.spi;

import com.timeslot.reservation.domain.ReservationStatus;

import java.time.LocalDateTime;

/**
 * 预约生命周期通知合约。所有方法幂等性由 meeting 域 NotificationService 的
 * insert-ignore + dedup_key 策略保证（动作类通知传 null 由调用方每次送达）。
 */
public interface ReservationNotificationPort {

    /** 用户创建预约后，通知本人（status 决定文案：待审批 / 已确认）。 */
    void notifyCreated(Long userId, Long reservationId, String title, String roomName,
                       LocalDateTime start, ReservationStatus status);

    /** 待审批预约产生时，广播给所有活跃 ADMIN。 */
    void notifyPendingApproval(Long reservationId, String title, String roomName, LocalDateTime start);

    /** 审批通过，通知预约归属人。 */
    void notifyApproved(Long userId, Long reservationId, String title);

    /** 审批驳回，通知预约归属人（reason 可能为空）。 */
    void notifyRejected(Long userId, Long reservationId, String title, String reason);

    /** 预约被取消（本人取消或管理员强制取消），通知预约归属人。 */
    void notifyCancelled(Long userId, Long reservationId, String title);
}
