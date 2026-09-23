/**
 * 文件职责：报修工单查询和处理接口的响应 DTO。
 * 接口：在 Controller、Service 和前端 API 之间传递结构化数据。
 * 方法：无显式业务方法；record 组件承载房间/设施、报修内容、状态和处理结果。
*/

package com.timeslot.resource.dto;

import java.time.LocalDateTime;

public record RepairTicketResponse(Long id, Long roomId, String roomName, Long facilityId, String facilityName,
                                   String issue, String status, Long reporterId, String reporterName,
                                   LocalDateTime createdAt, LocalDateTime resolvedAt, String resolveRemark) {
}
