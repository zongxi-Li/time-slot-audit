package com.timeslot.identity.dto;

public record LoginResponse(String token, UserResponse user) {
}
