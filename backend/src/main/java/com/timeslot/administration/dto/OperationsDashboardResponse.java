package com.timeslot.administration.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record OperationsDashboardResponse(
        LocalDateTime start,
        LocalDateTime end,
        Long totalReservations,
        Long cancelledReservations,
        BigDecimal cancellationRate,
        List<RoomUsageResponse> popularRooms,
        List<PeakHourResponse> peakHours) {
}
