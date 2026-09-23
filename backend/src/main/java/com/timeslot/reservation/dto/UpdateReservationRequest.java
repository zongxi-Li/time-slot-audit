/**
 * 文件职责：修改预约接口的请求 DTO 及字段校验。
 * 接口：在 Controller、Service 和前端 API 之间传递结构化数据。
 * 方法：无显式业务方法；record 组件承载可修改的会议室、标题、时段、人数和备注。
*/

package com.timeslot.reservation.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public record UpdateReservationRequest(
        @NotNull @Min(0) Integer version,
        @NotNull Long roomId,
        @NotBlank @Size(max = 100) String title,
        @NotNull LocalDateTime startTime,
        @NotNull LocalDateTime endTime,
        @NotNull @Min(1) Integer participantCount,
        @Size(max = 500) String remark
) {
}
