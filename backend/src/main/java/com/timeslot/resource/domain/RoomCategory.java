/**
 * 文件职责：定义 会议室资源 的实体、值对象或枚举。
 * 接口：供本域 Service、Mapper 和 DTO 转换使用。
 */
package com.timeslot.resource.domain;

public record RoomCategory(Long id, String name, Integer minCapacity, Integer maxCapacity, boolean approvalRequired,
                           Integer maxDurationMinutes, Integer advanceDays, String description) {
}
