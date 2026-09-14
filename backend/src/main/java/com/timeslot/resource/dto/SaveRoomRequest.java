package com.timeslot.resource.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record SaveRoomRequest(
        @NotBlank(message = "会议室名称不能为空") String name,
        @NotNull(message = "会议室分类不能为空") Long categoryId,
        String location,
        @NotNull(message = "容纳人数不能为空") @Positive(message = "容纳人数必须为正整数") Integer capacity,
        String description) {
}
