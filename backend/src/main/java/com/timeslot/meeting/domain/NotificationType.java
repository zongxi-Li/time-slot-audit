/**
 * 文件职责：定义系统通知的分类类型。
 * 接口：供本域 Service、Mapper 和 DTO 转换使用。
 * 方法：无显式业务方法；枚举常量用于区分预约及会议相关通知。
*/

package com.timeslot.meeting.domain;

/**
 * 通知类型（meeting 域业务内通知）。
 *
 * 预约生命周期产生的通知由 reservation / administration 域经 ReservationNotificationPort
 * 触发，本枚举是它们的落地类型：
 * - RESERVATION_CREATED：用户创建预约成功（本人）
 * - RESERVATION_PENDING_APPROVAL：有待审批预约（广播给所有活跃 ADMIN）
 * - RESERVATION_APPROVED / RESERVATION_REJECTED：审批结果（预约归属人）
 * - RESERVATION_CANCELLED：预约被取消（预约归属人）
 */
public enum NotificationType {
    /** 被加入会议 */
    ATTENDEE_ADDED,
    /** 被移出会议 */
    ATTENDEE_REMOVED,
    /** 会议即将开始提醒（调度触发，确定性去重） */
    MEETING_REMINDER,
    /** 预约结束后未签到，被判定 NO_SHOW */
    NO_SHOW_MARKED,
    /** 预约创建成功（用户本人） */
    RESERVATION_CREATED,
    /** 有待审批预约（广播管理员） */
    RESERVATION_PENDING_APPROVAL,
    /** 预约审批通过（预约归属人） */
    RESERVATION_APPROVED,
    /** 预约被驳回（预约归属人） */
    RESERVATION_REJECTED,
    /** 预约已取消（预约归属人） */
    RESERVATION_CANCELLED
}
