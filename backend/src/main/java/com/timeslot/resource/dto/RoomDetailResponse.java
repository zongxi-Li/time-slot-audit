package com.timeslot.resource.dto;

import java.util.List;

public record RoomDetailResponse(Long id, String name, String location, Integer capacity, String status,
                                 Long categoryId, String category, String description,
                                 List<FacilityResponse> facilities, List<OpenRuleResponse> openRules) {
}
