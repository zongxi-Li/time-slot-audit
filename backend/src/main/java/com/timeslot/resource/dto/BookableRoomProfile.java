/**
 * 文件职责：提供预约流程所需的、已锁定并完成可预订校验的会议室摘要。
 * 接口：在 Controller、Service 和前端 API 之间传递结构化数据。
 * 方法：无显式业务方法；record 组件承载房间、分类、容量、状态及审批规则等信息。
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
