/**
 * 文件职责：保存实际会议执行结果接口的请求 DTO 及输入校验。
 * 接口：由 MeetingExecutionController 接收并交给 Service 校验。
 * 方法：无显式业务方法；record 组件接收实际起止时间、出勤和总结等数据。
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
