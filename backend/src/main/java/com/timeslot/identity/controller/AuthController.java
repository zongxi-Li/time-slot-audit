/**
 * 文件职责：提供 身份与用户 HTTP 接口，将请求交给 Service 处理并返回统一响应。
 * 接口：POST /api/auth/login。
 */
package com.timeslot.identity.controller;

import com.timeslot.common.api.ApiResponse;
import com.timeslot.identity.dto.LoginRequest;
import com.timeslot.identity.dto.LoginResponse;
import com.timeslot.identity.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.success(authService.login(request));
    }
}
