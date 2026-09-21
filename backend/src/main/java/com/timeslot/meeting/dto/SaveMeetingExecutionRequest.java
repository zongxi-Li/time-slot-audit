/**
 * 文件职责：接收会议实际使用记录的保存请求。
 * 接口：由 MeetingExecutionController 接收并交给 Service 校验。
 */
package com.timeslot.meeting.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

/** 会议结束后由组织者或管理员确认的实际执行结果。 */
public record SaveMeetingExecutionRequest(
        @NotNull(message = "实际开始时间不能为空") LocalDateTime actualStartTime,
        @NotNull(message = "实际结束时间不能为空") LocalDateTime actualEndTime,
        @NotNull(message = "实际参会人数不能为空") @Min(value = 0, message = "实际参会人数不能为负数")
        Integer actualAttendeeCount) {
}
