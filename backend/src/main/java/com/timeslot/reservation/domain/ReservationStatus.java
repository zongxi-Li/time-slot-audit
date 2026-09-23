/**
 * 1. 预约状态定义
 * 文件职责：定义预约生命周期状态，例如待审批、已确认、已取消和已结束。
 * 接口：供本域 Service、Mapper 和 DTO 转换使用。
 * 方法：无显式业务方法；状态变更规则由 ReservationStateMachine 统一处理。
*/

package com.timeslot.reservation.domain;

public enum ReservationStatus {
    PENDING,
    CONFIRMED,
    REJECTED,
    CANCELLED
}
