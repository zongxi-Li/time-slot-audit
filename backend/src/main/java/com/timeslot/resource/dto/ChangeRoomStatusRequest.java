package com.timeslot.resource.dto;

import jakarta.validation.constraints.NotBlank;

public record ChangeRoomStatusRequest(@NotBlank(message = "会议室状态不能为空") String status) {
}
