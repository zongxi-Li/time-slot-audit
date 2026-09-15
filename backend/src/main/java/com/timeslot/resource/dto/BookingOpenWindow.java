package com.timeslot.resource.dto;

import java.time.LocalTime;

/** Public booking-facing open window of a room for one weekday (ISO 1..7). */
public record BookingOpenWindow(LocalTime openTime, LocalTime closeTime, boolean enabled) {
}
