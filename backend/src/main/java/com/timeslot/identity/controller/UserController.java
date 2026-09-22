/**
 * 文件职责：提供 身份与用户 HTTP 接口，将请求交给 Service 处理并返回统一响应。
 * 接口：GET /api/users/me；
 *        GET /api/users/me/qualification；
 *        GET /api/users/me/violations。
 */
package com.timeslot.identity.controller;

import com.timeslot.common.api.ApiResponse;
import com.timeslot.common.security.AuthenticatedUser;
import com.timeslot.common.security.CurrentUserProvider;
import com.timeslot.identity.dto.QualificationResponse;
import com.timeslot.identity.dto.UserDirectoryResponse;
import com.timeslot.identity.dto.UserResponse;
import com.timeslot.identity.dto.ViolationResponse;
import com.timeslot.identity.service.AuthService;
import com.timeslot.identity.service.BookingQualificationService;
import com.timeslot.identity.service.UserCreditService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/users")
public class UserController {
    private final CurrentUserProvider currentUserProvider;
    private final AuthService authService;
    private final BookingQualificationService bookingQualificationService;
    private final UserCreditService userCreditService;

    public UserController(CurrentUserProvider currentUserProvider, AuthService authService,
                          BookingQualificationService bookingQualificationService,
                          UserCreditService userCreditService) {
        this.currentUserProvider = currentUserProvider;
        this.authService = authService;
        this.bookingQualificationService = bookingQualificationService;
        this.userCreditService = userCreditService;
    }

    @GetMapping("/me")
    public ApiResponse<UserResponse> me() {
        AuthenticatedUser current = currentUserProvider.getRequired();
        return ApiResponse.success(authService.getCurrentUser(current));
    }

    /** 用户目录（活跃账号 + 部门名）：任何登录用户可读，用于新建预约时选择参与人。 */
    @GetMapping("/directory")
    public ApiResponse<List<UserDirectoryResponse>> directory() {
        currentUserProvider.getRequired();
        return ApiResponse.success(authService.listDirectory());
    }

    @GetMapping("/me/qualification")
    public ApiResponse<QualificationResponse> myQualification() {
        AuthenticatedUser current = currentUserProvider.getRequired();
        return ApiResponse.success(
                QualificationResponse.from(bookingQualificationService.check(current.userId())));
    }

    @GetMapping("/me/violations")
    public ApiResponse<List<ViolationResponse>> myViolations() {
        AuthenticatedUser current = currentUserProvider.getRequired();
        return ApiResponse.success(userCreditService.listViolations(current.userId()));
    }
}
