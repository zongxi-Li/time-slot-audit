/**
 * 文件职责：定义 身份与用户 的实体、值对象或枚举。
 * 接口：供本域 Service、Mapper 和 DTO 转换使用。
 */
package com.timeslot.identity.domain;

import java.time.LocalDateTime;

/** 违规/信用记录（读模型）：operator_name 通过 LEFT JOIN 冗余查询。 */
public record UserViolation(Long id, Long userId, String violationType, Integer creditChange, String reason,
                            Long operatorId, LocalDateTime createdAt, String operatorName) {
    public boolean autoTriggered() {
        return operatorId == null || (reason != null && reason.startsWith(ViolationType.AUTO_REASON_PREFIX));
    }
}
