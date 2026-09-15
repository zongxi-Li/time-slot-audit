package com.timeslot.resource.dto;

import java.time.LocalDateTime;

public record RepairTicketResponse(Long id, Long roomId, String roomName, Long facilityId, String facilityName,
                                   String issue, String status, Long reporterId, String reporterName,
                                   LocalDateTime createdAt, LocalDateTime resolvedAt, String resolveRemark) {
}
