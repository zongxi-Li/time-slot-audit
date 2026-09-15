package com.timeslot.administration.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RejectReservationRequest(
        @NotBlank(message = "驳回原因不能为空")
        @Size(max = 500, message = "驳回原因不能超过500字")
        String reason) {
}
