/**
 * 文件职责：向前端返回本人会议列表所需的预约及执行状态摘要。
 * 接口：在 Controller、Service 和前端 API 之间传递结构化数据。
 * 方法：from 将预约和执行数据组装为会议视图。
*/

package com.timeslot.meeting.dto;

import com.timeslot.meeting.domain.MeetingExecution;

import java.time.LocalDateTime;

/** “我的会议”视图：我作为组织者或参与人的预约执行情况。 */
public record MeetingExecutionView(Long reservationId, String reservationNo, String title, Long roomId,
                                   String roomName, String reservationStatus, LocalDateTime startTime,
                                   LocalDateTime endTime, String myRole, String myAttendanceStatus,
                                   LocalDateTime checkInAt, LocalDateTime checkOutAt,
                                   LocalDateTime actualStartTime, LocalDateTime actualEndTime,
                                   Integer actualAttendeeCount) {
    public static MeetingExecutionView from(MeetingExecution execution) {
        return new MeetingExecutionView(execution.getReservationId(), execution.getReservationNo(),
                execution.getTitle(), execution.getRoomId(), execution.getRoomName(),
                execution.getReservationStatus(), execution.getStartTime(), execution.getEndTime(),
                execution.getMyRole() == null ? null : execution.getMyRole().name(),
                execution.getMyStatus() == null ? null : execution.getMyStatus().name(),
                execution.getCheckInAt(), execution.getCheckOutAt(), execution.getActualStartTime(),
                execution.getActualEndTime(), execution.getActualAttendeeCount());
    }
}
