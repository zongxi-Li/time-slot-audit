package com.timeslot.identity.dto;

import com.timeslot.identity.domain.User;

import java.time.LocalDateTime;

public record UserResponse(Long id, String username, String realName, String email, String phone,
                           String role, Integer status, Long departmentId, String departmentName,
                           Integer creditScore, LocalDateTime restrictedUntil) {
    public static UserResponse from(User user) {
        return new UserResponse(user.id(), user.username(), user.realName(), user.email(), user.phone(),
                user.role(), user.status(), user.departmentId(), user.departmentName(),
                user.creditScore(), user.restrictedUntil());
    }
}
