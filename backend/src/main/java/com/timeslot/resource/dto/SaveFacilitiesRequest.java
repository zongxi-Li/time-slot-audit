package com.timeslot.resource.dto;

import jakarta.validation.Valid;

import java.util.List;

public record SaveFacilitiesRequest(List<@Valid SaveFacilityRequest> facilities) {
}
