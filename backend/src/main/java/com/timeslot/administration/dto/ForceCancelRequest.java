/**
 * 文件职责：定义 管理员运营 的请求或响应 DTO。
 * 接口：在 Controller、Service 和前端 API 之间传递结构化数据。
 */
package com.timeslot.administration.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ForceCancelRequest(
        @NotBlank(message = "强制取消原因不能为空")
        @Size(max = 500, message = "强制取消原因不能超过500字")
        String reason) {
}
