/**
 * 文件职责：会议室预约使用量统计的响应数据。
 * 接口：在 Controller、Service 和前端 API 之间传递结构化数据。
 * 方法：无显式业务方法；record 组件承载会议室及其预约次数/使用时长等统计值。
*/

package com.timeslot.administration.dto;

import java.math.BigDecimal;

public record RoomUsageResponse(
        Long roomId,
        String roomName,
        Long bookingCount,
        BigDecimal usedHours) {
}
