/**
 * 文件职责：定义 会议执行 的实体、值对象或枚举。
 * 接口：供本域 Service、Mapper 和 DTO 转换使用。
 */
package com.timeslot.meeting.domain;

/**
 * 出勤状态机（持久化于 reservation_attendee.attendance_status）：
 *
 * <pre>
 * EXPECTED    -> CHECKED_IN -> CHECKED_OUT   正常签到生命周期
 * EXPECTED    -> NO_SHOW                    预约结束后由调度判定（幂等，可重复执行）
 * </pre>
 *
 * NO_SHOW 为终态，不允许补签到；出勤事实一旦产生（CHECKED_IN 及之后）不可移除参与人。
 */
public enum AttendeeStatus {
    EXPECTED,
    CHECKED_IN,
    CHECKED_OUT,
    NO_SHOW
}
