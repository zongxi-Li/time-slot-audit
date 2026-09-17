/**
 * 文件职责：定义 会议执行 的实体、值对象或枚举。
 * 接口：供本域 Service、Mapper 和 DTO 转换使用。
 */
package com.timeslot.meeting.domain;

import java.time.LocalDateTime;

/**
 * reservation_attendee 行模型，JOIN sys_user 带出展示用账号信息。
 * 对 sys_user 仅为只读 JOIN（与 ReservationMapper 的既有先例一致），写操作只发生在 meeting 域表。
 */
public class Attendee {
    private Long id;
    private Long reservationId;
    private Long userId;
    private String username;
    private String realName;
    private AttendeeRole attendeeRole;
    private AttendeeStatus attendanceStatus;
    private LocalDateTime checkInAt;
    private LocalDateTime checkOutAt;
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getReservationId() { return reservationId; }
    public void setReservationId(Long reservationId) { this.reservationId = reservationId; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getRealName() { return realName; }
    public void setRealName(String realName) { this.realName = realName; }
    public AttendeeRole getAttendeeRole() { return attendeeRole; }
    public void setAttendeeRole(AttendeeRole attendeeRole) { this.attendeeRole = attendeeRole; }
    public AttendeeStatus getAttendanceStatus() { return attendanceStatus; }
    public void setAttendanceStatus(AttendeeStatus attendanceStatus) { this.attendanceStatus = attendanceStatus; }
    public LocalDateTime getCheckInAt() { return checkInAt; }
    public void setCheckInAt(LocalDateTime checkInAt) { this.checkInAt = checkInAt; }
    public LocalDateTime getCheckOutAt() { return checkOutAt; }
    public void setCheckOutAt(LocalDateTime checkOutAt) { this.checkOutAt = checkOutAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
