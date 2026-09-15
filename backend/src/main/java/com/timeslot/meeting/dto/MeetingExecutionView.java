package com.timeslot.meeting.dto;

import com.timeslot.meeting.domain.MeetingExecution;

import java.time.LocalDateTime;

/** “我的会议”视图：我作为组织者或参与人的预约执行情况。 */
public record MeetingExecutionView(Long reservationId, String reservationNo, String title, Long roomId,
                                   String roomName, String reservationStatus, LocalDateTime startTime,
                                   LocalDateTime endTime, String myRole, String myAttendanceStatus,
                                   LocalDateTime checkInAt, LocalDateTime checkOutAt) {
    public static MeetingExecutionView from(MeetingExecution execution) {
        return new MeetingExecutionView(execution.getReservationId(), execution.getReservationNo(),
                execution.getTitle(), execution.getRoomId(), execution.getRoomName(),
                execution.getReservationStatus(), execution.getStartTime(), execution.getEndTime(),
                execution.getMyRole() == null ? null : execution.getMyRole().name(),
                execution.getMyStatus() == null ? null : execution.getMyStatus().name(),
                execution.getCheckInAt(), execution.getCheckOutAt());
    }
}
