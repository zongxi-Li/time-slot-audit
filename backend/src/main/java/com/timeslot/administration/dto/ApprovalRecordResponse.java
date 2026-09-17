/**
 * 文件职责：定义 管理员运营 的请求或响应 DTO。
 * 接口：在 Controller、Service 和前端 API 之间传递结构化数据。
 */
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
