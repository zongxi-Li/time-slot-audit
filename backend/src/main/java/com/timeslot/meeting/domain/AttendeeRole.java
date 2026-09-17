/**
 * 文件职责：定义 会议执行 的实体、值对象或枚举。
 * 接口：供本域 Service、Mapper 和 DTO 转换使用。
 */
package com.timeslot.meeting.domain;

/** 参会角色：组织者即预约创建人，自动以一行 ORGANIZER 存在，不可移除。 */
public enum AttendeeRole {
    ORGANIZER,
    ATTENDEE
}
