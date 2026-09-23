/**
 * 文件职责：meeting domain 包标记，不承载会议业务状态。
 * 接口：供本域 Service、Mapper 和 DTO 转换使用。
 * 方法：无业务方法。
 */

package com.timeslot.meeting.domain;

/** Boundary marker for attendee, notification and meeting-level execution records. */
public final class MeetingDomainMarker {
    private MeetingDomainMarker() {
    }
}
