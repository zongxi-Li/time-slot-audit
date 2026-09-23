/**
 * 文件职责：审计日志查询结果的 API 响应数据。
 * 接口：在 Controller、Service 和前端 API 之间传递结构化数据。
 * 方法：无显式业务方法；record 组件承载操作者、业务对象、动作、详情和发生时间。
*/

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
