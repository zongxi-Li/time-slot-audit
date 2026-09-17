/**
 * 文件职责：定义 管理员运营 的请求或响应 DTO。
 * 接口：在 Controller、Service 和前端 API 之间传递结构化数据。
 */
package com.timeslot.administration.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record OperationsDashboardResponse(
        LocalDateTime start,
        LocalDateTime end,
        Long totalReservations,
        Long cancelledReservations,
        BigDecimal cancellationRate,
        List<RoomUsageResponse> popularRooms,
        List<PeakHourResponse> peakHours) {
}
