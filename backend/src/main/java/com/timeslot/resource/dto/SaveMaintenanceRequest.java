/**
 * 文件职责：创建会议室维护计划的请求 DTO 及时间/内容校验。
 * 接口：在 Controller、Service 和前端 API 之间传递结构化数据。
 * 方法：无显式业务方法；record 组件承载维护原因和计划起止时间。
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
