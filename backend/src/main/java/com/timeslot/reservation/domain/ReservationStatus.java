/**
 * 1. 预约状态定义
 * 文件职责：定义 预约核心 的实体、值对象或枚举。
 * 接口：供本域 Service、Mapper 和 DTO 转换使用。
 */
package com.timeslot.reservation.domain;

public enum ReservationStatus {
    PENDING,
    CONFIRMED,
    REJECTED,
    CANCELLED
}
