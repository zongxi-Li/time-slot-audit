/**
 * 文件职责：用户取消预约接口的请求 DTO，承载可选取消原因及长度校验。
 * 接口：在 Controller、Service 和前端 API 之间传递结构化数据。
 * 方法：无显式业务方法；record 访问器由 Java 自动生成。
 */

package com.timeslot.reservation.dto;

import jakarta.validation.constraints.Size;

public record CancelReservationRequest(@Size(max = 500) String reason) {
}
