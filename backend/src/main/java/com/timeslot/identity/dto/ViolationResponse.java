/**
 * 文件职责：定义 身份与用户 的请求或响应 DTO。
 * 接口：在 Controller、Service 和前端 API 之间传递结构化数据。
 */
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
