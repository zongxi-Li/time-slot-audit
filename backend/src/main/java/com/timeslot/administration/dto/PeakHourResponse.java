/**
 * 文件职责：表示某一小时的预约数量统计。
 * 接口：在 Controller、Service 和前端 API 之间传递结构化数据。
 * 方法：无显式业务方法；record 访问器由 Java 自动生成。
*/

package com.timeslot.administration.dto;

public record PeakHourResponse(Integer hour, Long bookingCount) {
}
