/**
 * 文件职责：定义 会议执行 的请求或响应 DTO。
 * 接口：在 Controller、Service 和前端 API 之间传递结构化数据。
 */
package com.timeslot.meeting.dto;

import com.timeslot.meeting.domain.Attendee;

import java.time.LocalDateTime;

/** 参与人/出勤视图。 */
public record AttendeeView(Long id, Long reservationId, Long userId, String username, String realName,
                           String attendeeRole, String attendanceStatus, LocalDateTime checkInAt,
                           LocalDateTime checkOutAt, LocalDateTime joinedAt) {
    public static AttendeeView from(Attendee attendee) {
        return new AttendeeView(attendee.getId(), attendee.getReservationId(), attendee.getUserId(),
                attendee.getUsername(), attendee.getRealName(),
                attendee.getAttendeeRole() == null ? null : attendee.getAttendeeRole().name(),
                attendee.getAttendanceStatus() == null ? null : attendee.getAttendanceStatus().name(),
                attendee.getCheckInAt(), attendee.getCheckOutAt(), attendee.getCreatedAt());
    }
}
