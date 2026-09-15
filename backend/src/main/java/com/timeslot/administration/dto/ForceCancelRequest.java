package com.timeslot.administration.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ForceCancelRequest(
        @NotBlank(message = "强制取消原因不能为空")
        @Size(max = 500, message = "强制取消原因不能超过500字")
        String reason) {
}
