/**
 * 文件职责：向前端返回会议室分类和审批/容量规则。
 * 接口：在 Controller、Service 和前端 API 之间传递结构化数据。
 * 方法：from 将 RoomCategory 领域对象转换为响应。
*/

package com.timeslot.resource.dto;

import com.timeslot.resource.domain.RoomCategory;

public record CategoryResponse(Long id, String name, Integer minCapacity, Integer maxCapacity, boolean approvalRequired,
                               Integer maxDurationMinutes, Integer advanceDays, String description) {
    public static CategoryResponse from(RoomCategory category) {
        return new CategoryResponse(category.id(), category.name(), category.minCapacity(), category.maxCapacity(),
                category.approvalRequired(), category.maxDurationMinutes(), category.advanceDays(), category.description());
    }
}
