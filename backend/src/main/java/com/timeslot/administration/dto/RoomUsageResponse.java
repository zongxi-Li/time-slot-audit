package com.timeslot.administration.dto;

import java.math.BigDecimal;

public record RoomUsageResponse(
        Long roomId,
        String roomName,
        Long bookingCount,
        BigDecimal usedHours) {
}
