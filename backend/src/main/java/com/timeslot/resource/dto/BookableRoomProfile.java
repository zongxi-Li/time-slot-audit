package com.timeslot.resource.dto;

/**
 * Public booking-facing view of a meeting room plus its category rules.
 * Exposed to the reservation domain through {@code ResourceBookingQueryService};
 * reservation must not depend on resource entities or mappers.
 */
public record BookableRoomProfile(Long roomId, String roomName, Integer capacity, String status,
                                  boolean approvalRequired, Integer maxDurationMinutes, Integer advanceDays) {
}
