package com.timeslot.identity.dto;

import com.timeslot.identity.domain.UserViolation;

import java.time.LocalDateTime;

public record ViolationResponse(Long id, Long userId, String violationType, Integer creditChange, String reason,
                                String operatorName, LocalDateTime createdAt) {
    public static ViolationResponse from(UserViolation violation) {
        return new ViolationResponse(violation.id(), violation.userId(), violation.violationType(),
                violation.creditChange(), violation.reason(), violation.operatorName(), violation.createdAt());
    }
}
