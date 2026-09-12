package com.timeslot.resource.domain;

public record RoomCategory(Long id, String name, boolean approvalRequired, int maxDurationMinutes, int advanceDays) {
}
