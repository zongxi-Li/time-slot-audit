package com.timeslot.resource.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

public record SaveMaintenanceRequest(
        @NotBlank(message = "维护原因不能为空") String reason,
        @NotNull(message = "维护开始时间不能为空") LocalDateTime startTime,
        @NotNull(message = "维护结束时间不能为空") LocalDateTime endTime) {
}
