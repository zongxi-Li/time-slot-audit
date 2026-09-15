package com.timeslot.identity.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateUserStatusRequest(
        @NotNull(message = "状态不能为空") @Min(value = 0, message = "状态仅支持 0-禁用 / 1-启用") @Max(value = 1, message = "状态仅支持 0-禁用 / 1-启用") Integer status,
        @Size(max = 500, message = "原因最长 500 字符") String reason) {
}
