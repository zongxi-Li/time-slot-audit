/**
 * 文件职责：身份认证 HTTP 接口。
 * 接口：POST /api/auth/login。
 * 方法：login 接收登录 DTO，调用认证服务并返回令牌和用户信息。
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
