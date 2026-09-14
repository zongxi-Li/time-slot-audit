package com.timeslot.resource.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record SaveCategoryRequest(
        @NotBlank(message = "分类名称不能为空") String name,
        @NotNull(message = "分类容量下限不能为空") @Positive(message = "分类容量下限必须为正整数") Integer minCapacity,
        @NotNull(message = "分类容量上限不能为空") @Positive(message = "分类容量上限必须为正整数") Integer maxCapacity,
        @NotNull(message = "是否需要审批不能为空") Boolean approvalRequired,
        @NotNull(message = "最大预约时长不能为空") @Positive(message = "最大预约时长必须为正整数") Integer maxDurationMinutes,
        @NotNull(message = "提前预约天数不能为空") @Min(value = 0, message = "提前预约天数不能为负") Integer advanceDays,
        String description) {
}
