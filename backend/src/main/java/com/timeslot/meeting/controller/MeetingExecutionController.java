/**
 * 文件职责：会议参与人、签到签退、执行情况和本人会议列表 HTTP 接口。
 * 接口：GET /api/meetings/{reservationId}/attendees；
 *        POST /api/meetings/{reservationId}/attendees；
 *        DELETE /api/meetings/{reservationId}/attendees/{userId}；
 *        POST /api/meetings/{reservationId}/check-in；
 *        POST /api/meetings/{reservationId}/check-out；
 *        GET/PUT /api/meetings/{reservationId}/execution；
 *        …。
 * 方法：attendees 查询参与人；addAttendee/removeAttendee 管理参与人；checkIn/checkOut 登记到场；attendance/execution 查询执行数据；saveExecution 保存实际执行记录；myMeetings 查询本人会议。
*/

package com.timeslot.meeting.controller;

import com.timeslot.common.api.ApiResponse;
import com.timeslot.meeting.dto.AddAttendeeRequest;
import com.timeslot.meeting.dto.AttendanceView;
import com.timeslot.meeting.dto.AttendeeView;
import com.timeslot.meeting.dto.MeetingExecutionView;
import com.timeslot.meeting.dto.MeetingExecutionRecordView;
import com.timeslot.meeting.dto.SaveMeetingExecutionRequest;
import com.timeslot.meeting.service.MeetingExecutionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
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

    @GetMapping("/{reservationId}/execution")
    public ApiResponse<MeetingExecutionRecordView> execution(@PathVariable Long reservationId) {
        return ApiResponse.success(meetingExecutionService.executionRecord(reservationId));
    }

    @PutMapping("/{reservationId}/execution")
    public ApiResponse<MeetingExecutionRecordView> saveExecution(
            @PathVariable Long reservationId, @Valid @RequestBody SaveMeetingExecutionRequest request) {
        return ApiResponse.success(meetingExecutionService.saveExecutionRecord(reservationId, request));
    }

    @GetMapping("/my")
    public ApiResponse<List<MeetingExecutionView>> myMeetings() {
        return ApiResponse.success(meetingExecutionService.myMeetings());
    }
}
