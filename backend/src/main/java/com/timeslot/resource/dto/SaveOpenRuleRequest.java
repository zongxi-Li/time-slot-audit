/**
 * 文件职责：定义 会议室资源 的请求或响应 DTO。
 * 接口：在 Controller、Service 和前端 API 之间传递结构化数据。
 */
package com.timeslot.resource.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.LocalTime;

public record SaveOpenRuleRequest(
        @NotNull(message = "星期不能为空") @Min(value = 1, message = "星期必须为1~7") @Max(value = 7, message = "星期必须为1~7") Integer weekday,
        @NotNull(message = "开放时间不能为空") LocalTime openTime,
        @NotNull(message = "关闭时间不能为空") LocalTime closeTime,
        Boolean enabled) {
}
