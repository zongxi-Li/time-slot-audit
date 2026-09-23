/**
 * 文件职责：向前端展示单条用户违规及信用变化记录。
 * 接口：在 Controller、Service 和前端 API 之间传递结构化数据。
 * 方法：from 将 UserViolation 领域对象转换为 API 响应。
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
