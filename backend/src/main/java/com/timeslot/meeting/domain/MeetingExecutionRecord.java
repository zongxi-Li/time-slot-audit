/**
 * 文件职责：保存单场会议的实际执行结果。
 * 接口：供 meeting 域 Service、Mapper 和 DTO 转换使用。
 */
package com.timeslot.meeting.domain;

import java.time.LocalDateTime;

/**
 * 与 reservation 一对一的会议级执行记录；预约时间仍表示计划时间，
 * 本对象只保存实际开始、实际结束和实际参会人数。
 */
public class MeetingExecutionRecord {
    private Long reservationId;
    private LocalDateTime actualStartTime;
    private LocalDateTime actualEndTime;
    private Integer actualAttendeeCount;
    private Long recordedBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getReservationId() { return reservationId; }
    public void setReservationId(Long reservationId) { this.reservationId = reservationId; }
    public LocalDateTime getActualStartTime() { return actualStartTime; }
    public void setActualStartTime(LocalDateTime actualStartTime) { this.actualStartTime = actualStartTime; }
    public LocalDateTime getActualEndTime() { return actualEndTime; }
    public void setActualEndTime(LocalDateTime actualEndTime) { this.actualEndTime = actualEndTime; }
    public Integer getActualAttendeeCount() { return actualAttendeeCount; }
    public void setActualAttendeeCount(Integer actualAttendeeCount) { this.actualAttendeeCount = actualAttendeeCount; }
    public Long getRecordedBy() { return recordedBy; }
    public void setRecordedBy(Long recordedBy) { this.recordedBy = recordedBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
