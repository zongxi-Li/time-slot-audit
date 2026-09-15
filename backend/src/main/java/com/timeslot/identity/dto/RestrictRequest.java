package com.timeslot.identity.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

/**
 * 设置/解除限制（黑名单）请求。
 * restrictedUntil 为空表示解除限制；非空表示设置限制，必须为未来时间（Service 校验）。
 * 人工操作必须填写原因。
 */
public record RestrictRequest(
        @NotBlank(message = "操作原因必填") @Size(max = 500) String reason,
        LocalDateTime restrictedUntil) {
}
