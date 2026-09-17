/**
 * 文件职责：定义 会议室资源 的请求或响应 DTO。
 * 接口：在 Controller、Service 和前端 API 之间传递结构化数据。
 */
package com.timeslot.resource.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record SaveFacilityRequest(
        @NotBlank(message = "设施名称不能为空") String name,
        @NotNull(message = "设施数量不能为空") @Positive(message = "设施数量必须为正整数") Integer quantity,
        String description) {
}
