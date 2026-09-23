/**
 * 文件职责：处理登录认证并提供当前用户相关身份查询。
 * 接口：由 AuthController 的 POST /api/auth/login 调用。
 * 方法：login 验证凭据并签发令牌；getCurrentUser 取得当前用户；listDirectory 获取可选用户目录。
 */

package com.timeslot.identity.service;

import com.timeslot.common.api.ErrorCode;
import com.timeslot.common.exception.BusinessException;
import com.timeslot.common.security.AuthenticatedUser;
import com.timeslot.common.security.JwtService;
import com.timeslot.identity.domain.User;
import com.timeslot.identity.dto.LoginRequest;
import com.timeslot.identity.dto.LoginResponse;
import com.timeslot.identity.dto.UserDirectoryResponse;
import com.timeslot.identity.dto.UserResponse;
import com.timeslot.identity.mapper.UserMapper;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;

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

    /** 用户目录（仅活跃账号）：供登录用户选择会议参与人，最小信息集不含治理字段。 */
    public List<UserDirectoryResponse> listDirectory() {
        return userMapper.findDirectory();
    }
}
