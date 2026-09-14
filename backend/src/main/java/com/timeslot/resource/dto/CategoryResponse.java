package com.timeslot.resource.dto;

import com.timeslot.resource.domain.RoomCategory;

public record CategoryResponse(Long id, String name, Integer minCapacity, Integer maxCapacity, boolean approvalRequired,
                               Integer maxDurationMinutes, Integer advanceDays, String description) {
    public static CategoryResponse from(RoomCategory category) {
        return new CategoryResponse(category.id(), category.name(), category.minCapacity(), category.maxCapacity(),
                category.approvalRequired(), category.maxDurationMinutes(), category.advanceDays(), category.description());
    }
}
