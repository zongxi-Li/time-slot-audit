package com.timeslot.meeting.controller;

import com.timeslot.common.api.ApiResponse;
import com.timeslot.meeting.dto.AddAttendeeRequest;
import com.timeslot.meeting.dto.AttendanceView;
import com.timeslot.meeting.dto.AttendeeView;
import com.timeslot.meeting.dto.MeetingExecutionView;
import com.timeslot.meeting.service.MeetingExecutionService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 会议执行域接口。reservationId 为预约ID；
 * 路径语义与任务契约一致：参与人管理、签到/签退、出勤查询、我的会议。
 */
@RestController
@RequestMapping("/api/meetings")
public class MeetingExecutionController {
    private final MeetingExecutionService meetingExecutionService;

    public MeetingExecutionController(MeetingExecutionService meetingExecutionService) {
        this.meetingExecutionService = meetingExecutionService;
    }

    @GetMapping("/{reservationId}/attendees")
    public ApiResponse<List<AttendeeView>> attendees(@PathVariable Long reservationId) {
        return ApiResponse.success(meetingExecutionService.listAttendees(reservationId));
    }

    @PostMapping("/{reservationId}/attendees")
    public ResponseEntity<ApiResponse<AttendeeView>> addAttendee(@PathVariable Long reservationId,
                                                                 @RequestBody(required = false) AddAttendeeRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(meetingExecutionService.addAttendee(reservationId, request)));
    }

    @DeleteMapping("/{reservationId}/attendees/{userId}")
    public ApiResponse<Void> removeAttendee(@PathVariable Long reservationId, @PathVariable Long userId) {
        meetingExecutionService.removeAttendee(reservationId, userId);
        return ApiResponse.success(null);
    }

    @PostMapping("/{reservationId}/check-in")
    public ApiResponse<AttendeeView> checkIn(@PathVariable Long reservationId) {
        return ApiResponse.success(meetingExecutionService.checkIn(reservationId));
    }

    @PostMapping("/{reservationId}/check-out")
    public ApiResponse<AttendeeView> checkOut(@PathVariable Long reservationId) {
        return ApiResponse.success(meetingExecutionService.checkOut(reservationId));
    }

    @GetMapping("/{reservationId}/attendance")
    public ApiResponse<AttendanceView> attendance(@PathVariable Long reservationId) {
        return ApiResponse.success(meetingExecutionService.attendance(reservationId));
    }

    @GetMapping("/my")
    public ApiResponse<List<MeetingExecutionView>> myMeetings() {
        return ApiResponse.success(meetingExecutionService.myMeetings());
    }
}
