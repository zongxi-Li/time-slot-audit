package com.timeslot.common.time;

import java.time.LocalDateTime;

public record SystemTimeResponse(LocalDateTime currentTime, SystemTimeMode mode) {
}
