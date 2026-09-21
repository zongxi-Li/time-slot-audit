/**
 * 文件职责：返回会议级实际使用记录。
 * 接口：由会议执行接口返回给前端和其他只读调用方。
 */
package com.timeslot.meeting.dto;

import com.timeslot.meeting.domain.MeetingExecutionRecord;

import java.time.LocalDateTime;

public record MeetingExecutionRecordView(Long reservationId, LocalDateTime actualStartTime,
                                         LocalDateTime actualEndTime, Integer actualAttendeeCount,
                                         Long recordedBy, LocalDateTime createdAt, LocalDateTime updatedAt) {
    public static MeetingExecutionRecordView from(MeetingExecutionRecord record) {
        if (record == null) return null;
        return new MeetingExecutionRecordView(record.getReservationId(), record.getActualStartTime(),
                record.getActualEndTime(), record.getActualAttendeeCount(), record.getRecordedBy(),
                record.getCreatedAt(), record.getUpdatedAt());
    }
}
