/**
 * 文件职责：表示一个会议室的每日预约开放时间窗口。
 * 接口：在 Controller、Service 和前端 API 之间传递结构化数据。
 * 方法：无显式业务方法；record 组件承载开始、结束时间和启用标记。
*/

package com.timeslot.resource.dto;

import java.time.LocalTime;

/** Public booking-facing open window of a room for one weekday (ISO 1..7). */
public record BookingOpenWindow(LocalTime openTime, LocalTime closeTime, boolean enabled) {
}
