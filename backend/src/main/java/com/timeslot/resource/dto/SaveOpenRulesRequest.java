package com.timeslot.resource.dto;

import jakarta.validation.Valid;

import java.util.List;

public record SaveOpenRulesRequest(List<@Valid SaveOpenRuleRequest> rules) {
}
