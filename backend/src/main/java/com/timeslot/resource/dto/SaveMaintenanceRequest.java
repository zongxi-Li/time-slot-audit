/**
 * 文件职责：定义 会议室资源 的请求或响应 DTO。
 * 接口：在 Controller、Service 和前端 API 之间传递结构化数据。
 */
package com.timeslot.resource.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

public record SaveMaintenanceRequest(
        @NotBlank(message = "维护原因不能为空") String reason,
        @NotNull(message = "维护开始时间不能为空") LocalDateTime startTime,
        @NotNull(message = "维护结束时间不能为空") LocalDateTime endTime) {
}
