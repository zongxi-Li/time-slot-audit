package com.timeslot.administration.dto;

import java.time.LocalDateTime;

public record ApprovalRecordResponse(
        Long id,
        Long reservationId,
        Long approverId,
        String approverName,
        String action,
        String remark,
        LocalDateTime createdAt) {
}
