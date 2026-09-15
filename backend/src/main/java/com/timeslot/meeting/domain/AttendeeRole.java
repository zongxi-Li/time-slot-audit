package com.timeslot.meeting.domain;

/** 参会角色：组织者即预约创建人，自动以一行 ORGANIZER 存在，不可移除。 */
public enum AttendeeRole {
    ORGANIZER,
    ATTENDEE
}
