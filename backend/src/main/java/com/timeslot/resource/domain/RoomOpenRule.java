package com.timeslot.resource.domain;

import java.time.LocalTime;

public record RoomOpenRule(LocalTime openTime, LocalTime closeTime, boolean enabled) {
}
