/**
 * 文件职责：定义 预约核心 的请求或响应 DTO。
 * 接口：在 Controller、Service 和前端 API 之间传递结构化数据。
 */
package com.timeslot.reservation.dto;

import jakarta.validation.constraints.Size;

public record CancelReservationRequest(@Size(max = 500) String reason) {
}
