package com.timeslot.administration.dto;

import java.time.LocalDateTime;

public record AuditLogResponse(
        Long id,
        Long userId,
        String operatorName,
        String operationType,
        String businessType,
        Long businessId,
        String content,
        String ipAddress,
        LocalDateTime createdAt) {
}
