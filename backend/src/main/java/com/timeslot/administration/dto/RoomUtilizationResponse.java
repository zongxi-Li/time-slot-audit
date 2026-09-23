/**
 * 文件职责：会议室利用率统计的响应数据。
 * 接口：在 Controller、Service 和前端 API 之间传递结构化数据。
 * 方法：of 根据会议室及统计值创建规范化响应；record 访问器由 Java 自动生成。
 */

package com.timeslot.administration.dto;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 单间会议室在统计周期内的使用率明细：
 * usedHours 为已确认预约与区间的重叠小时数；avgDailyHours = usedHours / 统计天数；
 * utilizationRate = usedHours / 开放总时长 × 100（开放总时长由全局可预约窗口 × 天数得出）。
 */
public record RoomUtilizationResponse(
        Long roomId,
        String roomName,
        Long confirmedCount,
        BigDecimal usedHours,
        BigDecimal avgDailyHours,
        BigDecimal utilizationRate) {

    public static RoomUtilizationResponse of(Long roomId, String roomName, Long confirmedCount,
                                             BigDecimal usedHours, BigDecimal openHoursTotal, long days) {
        BigDecimal avgDaily = usedHours.divide(BigDecimal.valueOf(days), 2, RoundingMode.HALF_UP);
        BigDecimal rate = openHoursTotal.signum() == 0 ? BigDecimal.ZERO
                : usedHours.multiply(BigDecimal.valueOf(100)).divide(openHoursTotal, 2, RoundingMode.HALF_UP);
        return new RoomUtilizationResponse(roomId, roomName, confirmedCount, usedHours, avgDaily, rate);
    }
}
