package com.timeslot.resource.domain;

public record RoomCategory(Long id, String name, Integer minCapacity, Integer maxCapacity, boolean approvalRequired,
                           Integer maxDurationMinutes, Integer advanceDays, String description) {
}
