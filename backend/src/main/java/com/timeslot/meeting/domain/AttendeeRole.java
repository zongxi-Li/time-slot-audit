/**
 * 文件职责：区分会议参与人的组织者、普通参与人等角色。
 * 接口：供本域 Service、Mapper 和 DTO 转换使用。
 * 方法：无显式业务方法；枚举常量用于权限和参与关系判定。
*/

package com.timeslot.meeting.domain;

/** 参会角色：组织者即预约创建人，自动以一行 ORGANIZER 存在，不可移除。 */
public enum AttendeeRole {
    ORGANIZER,
    ATTENDEE
}
