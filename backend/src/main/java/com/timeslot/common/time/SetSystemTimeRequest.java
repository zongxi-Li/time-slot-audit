package com.timeslot.common.time;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

public record SetSystemTimeRequest(@NotNull LocalDateTime currentTime) {
}
