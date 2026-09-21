/**
 * 文件职责：定义 会议执行 的实体、值对象或枚举。
 * 接口：供本域 Service、Mapper 和 DTO 转换使用。
 */
package com.timeslot.meeting.domain;

import java.time.LocalDateTime;

/**
 * “我的会议”行模型：以当前用户在 reservation_attendee 的行为主视角，
 * 只读 JOIN reservation / meeting_room 带出展示字段。
 */
public class MeetingExecution {
    private Long reservationId;
    private String reservationNo;
    private String title;
    private Long roomId;
    private String roomName;
    private String reservationStatus;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private AttendeeRole myRole;
    private AttendeeStatus myStatus;
    private LocalDateTime checkInAt;
    private LocalDateTime checkOutAt;
    private LocalDateTime actualStartTime;
    private LocalDateTime actualEndTime;
    private Integer actualAttendeeCount;

    public Long getReservationId() { return reservationId; }
    public void setReservationId(Long reservationId) { this.reservationId = reservationId; }
    public String getReservationNo() { return reservationNo; }
    public void setReservationNo(String reservationNo) { this.reservationNo = reservationNo; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public Long getRoomId() { return roomId; }
    public void setRoomId(Long roomId) { this.roomId = roomId; }
    public String getRoomName() { return roomName; }
    public void setRoomName(String roomName) { this.roomName = roomName; }
    public String getReservationStatus() { return reservationStatus; }
    public void setReservationStatus(String reservationStatus) { this.reservationStatus = reservationStatus; }
    public LocalDateTime getStartTime() { return startTime; }
    public void setStartTime(LocalDateTime startTime) { this.startTime = startTime; }
    public LocalDateTime getEndTime() { return endTime; }
    public void setEndTime(LocalDateTime endTime) { this.endTime = endTime; }
    public AttendeeRole getMyRole() { return myRole; }
    public void setMyRole(AttendeeRole myRole) { this.myRole = myRole; }
    public AttendeeStatus getMyStatus() { return myStatus; }
    public void setMyStatus(AttendeeStatus myStatus) { this.myStatus = myStatus; }
    public LocalDateTime getCheckInAt() { return checkInAt; }
    public void setCheckInAt(LocalDateTime checkInAt) { this.checkInAt = checkInAt; }
    public LocalDateTime getCheckOutAt() { return checkOutAt; }
    public void setCheckOutAt(LocalDateTime checkOutAt) { this.checkOutAt = checkOutAt; }
    public LocalDateTime getActualStartTime() { return actualStartTime; }
    public void setActualStartTime(LocalDateTime actualStartTime) { this.actualStartTime = actualStartTime; }
    public LocalDateTime getActualEndTime() { return actualEndTime; }
    public void setActualEndTime(LocalDateTime actualEndTime) { this.actualEndTime = actualEndTime; }
    public Integer getActualAttendeeCount() { return actualAttendeeCount; }
    public void setActualAttendeeCount(Integer actualAttendeeCount) { this.actualAttendeeCount = actualAttendeeCount; }
}
