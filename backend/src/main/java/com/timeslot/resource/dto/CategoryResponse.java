/**
 * 文件职责：定义 会议室资源 的请求或响应 DTO。
 * 接口：在 Controller、Service 和前端 API 之间传递结构化数据。
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
