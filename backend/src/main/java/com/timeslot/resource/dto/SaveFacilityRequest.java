package com.timeslot.resource.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record SaveFacilityRequest(
        @NotBlank(message = "设施名称不能为空") String name,
        @NotNull(message = "设施数量不能为空") @Positive(message = "设施数量必须为正整数") Integer quantity,
        String description) {
}
