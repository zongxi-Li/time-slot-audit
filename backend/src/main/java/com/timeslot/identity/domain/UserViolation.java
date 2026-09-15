package com.timeslot.identity.domain;

import java.time.LocalDateTime;

/** 违规/信用记录（读模型）：operator_name 通过 LEFT JOIN 冗余查询。 */
public record UserViolation(Long id, Long userId, String violationType, Integer creditChange, String reason,
                            Long operatorId, LocalDateTime createdAt, String operatorName) {
    public boolean autoTriggered() {
        return operatorId == null || (reason != null && reason.startsWith(ViolationType.AUTO_REASON_PREFIX));
    }
}
