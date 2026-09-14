package com.timeslot.resource.dto;

import java.time.LocalTime;

public record OpenRuleResponse(Long id, Long roomId, Integer weekday, LocalTime openTime, LocalTime closeTime,
                               boolean enabled) {
}
