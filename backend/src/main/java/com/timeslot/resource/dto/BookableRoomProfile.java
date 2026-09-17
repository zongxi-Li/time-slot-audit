/**
 * 文件职责：定义 会议室资源 的请求或响应 DTO。
 * 接口：在 Controller、Service 和前端 API 之间传递结构化数据。
 */
package com.timeslot.resource.dto;

/**
 * Public booking-facing view of a meeting room plus its category rules.
 * Exposed to the reservation domain through {@code ResourceBookingQueryService};
 * reservation must not depend on resource entities or mappers.
 */
public record BookableRoomProfile(Long roomId, String roomName, Integer capacity, String status,
                                  boolean approvalRequired, Integer maxDurationMinutes, Integer advanceDays) {
}
