package com.timeslot.meeting.domain;

/**
 * 通知类型（meeting 域业务内通知）。
 * 预约取消 / 审批结果由 reservation、administration 域产生，属后续集成点，本版本不实现触发。
 */
public enum NotificationType {
    /** 被加入会议 */
    ATTENDEE_ADDED,
    /** 被移出会议 */
    ATTENDEE_REMOVED,
    /** 会议即将开始提醒（调度触发，确定性去重） */
    MEETING_REMINDER,
    /** 预约结束后未签到，被判定 NO_SHOW */
    NO_SHOW_MARKED
}
