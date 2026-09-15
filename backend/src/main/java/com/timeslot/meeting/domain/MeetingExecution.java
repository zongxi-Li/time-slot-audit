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
}
