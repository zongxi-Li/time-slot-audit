/**
 * 文件职责：定义 会议室资源 的请求或响应 DTO。
 * 接口：在 Controller、Service 和前端 API 之间传递结构化数据。
 */
package com.timeslot.resource.dto;

import java.time.LocalDateTime;

public record RepairTicketResponse(Long id, Long roomId, String roomName, Long facilityId, String facilityName,
                                   String issue, String status, Long reporterId, String reporterName,
                                   LocalDateTime createdAt, LocalDateTime resolvedAt, String resolveRemark) {
}
