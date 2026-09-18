/**
 * 文件职责：定义 预约核心 的实体、值对象或枚举。
 * 接口：供本域 Service、Mapper 和 DTO 转换使用。
 */
package com.timeslot.reservation.domain;

import java.time.LocalDateTime;

public class Reservation {
    private Long id;
    private String requestId;
    private String reservationNo;
    private Long roomId;
    private Long userId;
    private String roomName;
    private String userName;
    private String title;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Integer participantCount;
    private ReservationStatus status;
    private String remark;
    /** Optimistic-lock version. Every reservation write increments it. */
    private int version;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
    public String getReservationNo() { return reservationNo; }
    public void setReservationNo(String reservationNo) { this.reservationNo = reservationNo; }
    public Long getRoomId() { return roomId; }
    public void setRoomId(Long roomId) { this.roomId = roomId; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getRoomName() { return roomName; }
    public void setRoomName(String roomName) { this.roomName = roomName; }
    public String getUserName() { return userName; }
    public void setUserName(String userName) { this.userName = userName; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public LocalDateTime getStartTime() { return startTime; }
    public void setStartTime(LocalDateTime startTime) { this.startTime = startTime; }
    public LocalDateTime getEndTime() { return endTime; }
    public void setEndTime(LocalDateTime endTime) { this.endTime = endTime; }
    public Integer getParticipantCount() { return participantCount; }
    public void setParticipantCount(Integer participantCount) { this.participantCount = participantCount; }
    public ReservationStatus getStatus() { return status; }
    public void setStatus(ReservationStatus status) { this.status = status; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
    public int getVersion() { return version; }
    public void setVersion(int version) { this.version = version; }
}
