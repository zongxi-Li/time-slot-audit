/**
 * 文件职责：定义 预约核心 的请求或响应 DTO。
 * 接口：在 Controller、Service 和前端 API 之间传递结构化数据。
 */
package com.timeslot.reservation.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

/**
 * {@code repeatWeeks} 为选填的周期性会议（按周重复）周数：缺省或 1 表示单次会议；
 * 2..8 表示从首次开始时刻起每周同一时段批量创建 N 场预约，逐周做冲突与规则校验。
 */
public record CreateReservationRequest(
        @NotBlank @Size(max = 100) String requestId,
        @NotNull Long roomId,
        @NotBlank @Size(max = 100) String title,
        @NotNull LocalDateTime startTime,
        @NotNull LocalDateTime endTime,
        @NotNull @Min(1) Integer participantCount,
        @Size(max = 500) String remark,
        @Min(1) @Max(8) Integer repeatWeeks
) {
}
