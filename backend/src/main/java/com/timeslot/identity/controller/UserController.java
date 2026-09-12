package com.timeslot.identity.controller;

import com.timeslot.common.api.ApiResponse;
import com.timeslot.common.security.AuthenticatedUser;
import com.timeslot.common.security.CurrentUserProvider;
import com.timeslot.identity.dto.UserResponse;
import com.timeslot.identity.service.AuthService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
public class UserController {
    private final CurrentUserProvider currentUserProvider;
    private final AuthService authService;

    public UserController(CurrentUserProvider currentUserProvider, AuthService authService) {
        this.currentUserProvider = currentUserProvider;
        this.authService = authService;
    }

    @GetMapping("/me")
    public ApiResponse<UserResponse> me() {
        AuthenticatedUser current = currentUserProvider.getRequired();
        return ApiResponse.success(authService.getCurrentUser(current));
    }
}
