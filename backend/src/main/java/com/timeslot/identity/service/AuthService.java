/**
 * 文件职责：执行用户名密码登录、密码校验和 JWT 签发。
 * 接口：由 AuthController 的 POST /api/auth/login 调用。
 */
package com.timeslot.identity.service;

import com.timeslot.common.api.ErrorCode;
import com.timeslot.common.exception.BusinessException;
import com.timeslot.common.security.AuthenticatedUser;
import com.timeslot.common.security.JwtService;
import com.timeslot.identity.domain.User;
import com.timeslot.identity.dto.LoginRequest;
import com.timeslot.identity.dto.LoginResponse;
import com.timeslot.identity.dto.UserResponse;
import com.timeslot.identity.mapper.UserMapper;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(UserMapper userMapper, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    public LoginResponse login(LoginRequest request) {
        User user = userMapper.findByUsername(request.username());
        if (user == null || !user.enabled() || !passwordEncoder.matches(request.password(), user.password())) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, HttpStatus.UNAUTHORIZED, "用户名或密码错误");
        }
        AuthenticatedUser principal = new AuthenticatedUser(user.id(), user.username(), user.role());
        return new LoginResponse(jwtService.issue(principal), UserResponse.from(user));
    }

    public UserResponse getCurrentUser(AuthenticatedUser principal) {
        User user = userMapper.findById(principal.userId());
        if (user == null || !user.enabled()) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, HttpStatus.UNAUTHORIZED, "当前账号不可用");
        }
        return UserResponse.from(user);
    }
}
