/**
 * 文件职责：定义 身份与用户 的请求或响应 DTO。
 * 接口：在 Controller、Service 和前端 API 之间传递结构化数据。
 */
package com.timeslot.identity.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreditAdjustRequest(
        @NotNull(message = "信用分变化量不能为空") Integer creditChange,
        @NotBlank(message = "调整原因必填") @Size(max = 500) String reason) {
}
