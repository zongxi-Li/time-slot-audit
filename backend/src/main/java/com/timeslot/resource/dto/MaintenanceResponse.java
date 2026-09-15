package com.timeslot.resource.dto;

import java.time.LocalDateTime;

public record MaintenanceResponse(Long id, Long roomId, String reason, LocalDateTime startTime,
                                  LocalDateTime endTime, String status, Long createdBy, LocalDateTime createdAt) {
}
