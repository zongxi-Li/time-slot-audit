package com.timeslot.identity.dto;

import com.timeslot.identity.domain.User;

public record UserResponse(Long id, String username, String realName, String role) {
    public static UserResponse from(User user) {
        return new UserResponse(user.id(), user.username(), user.realName(), user.role());
    }
}
