/**
 * 文件职责：表示会议室分类、适用容量范围和审批要求。
 * 接口：供本域 Service、Mapper 和 DTO 转换使用。
 * 方法：无显式业务方法；record 组件定义分类规则数据。
*/

package com.timeslot.resource.domain;

public record RoomCategory(Long id, String name, Integer minCapacity, Integer maxCapacity, boolean approvalRequired,
                           Integer maxDurationMinutes, Integer advanceDays, String description) {
}
