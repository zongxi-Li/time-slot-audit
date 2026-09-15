package com.timeslot.identity.controller;

import com.timeslot.common.api.ApiResponse;
import com.timeslot.identity.dto.CreateUserRequest;
import com.timeslot.identity.dto.CreditAdjustRequest;
import com.timeslot.identity.dto.QualificationResponse;
import com.timeslot.identity.dto.ResetPasswordRequest;
import com.timeslot.identity.dto.RestrictRequest;
import com.timeslot.identity.dto.UpdateUserRequest;
import com.timeslot.identity.dto.UpdateUserStatusRequest;
import com.timeslot.identity.dto.UserResponse;
import com.timeslot.identity.dto.ViolationResponse;
import com.timeslot.identity.service.BookingQualificationService;
import com.timeslot.identity.service.UserAdminService;
import com.timeslot.identity.service.UserCreditService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/users")
@PreAuthorize("hasRole('ADMIN')")
public class UserAdminController {
    private final UserAdminService userAdminService;
    private final UserCreditService userCreditService;
    private final BookingQualificationService bookingQualificationService;

    public UserAdminController(UserAdminService userAdminService, UserCreditService userCreditService,
                               BookingQualificationService bookingQualificationService) {
        this.userAdminService = userAdminService;
        this.userCreditService = userCreditService;
        this.bookingQualificationService = bookingQualificationService;
    }

    @GetMapping
    public ApiResponse<List<UserResponse>> list(@RequestParam(required = false) String keyword,
                                                @RequestParam(required = false) Integer status) {
        return ApiResponse.success(userAdminService.list(keyword, status));
    }

    @GetMapping("/{id}")
    public ApiResponse<UserResponse> detail(@PathVariable Long id) {
        return ApiResponse.success(userAdminService.detail(id));
    }

    @GetMapping("/{id}/qualification")
    public ApiResponse<QualificationResponse> qualification(@PathVariable Long id) {
        return ApiResponse.success(QualificationResponse.from(bookingQualificationService.check(id)));
    }

    @GetMapping("/{id}/violations")
    public ApiResponse<List<ViolationResponse>> violations(@PathVariable Long id) {
        return ApiResponse.success(userCreditService.listViolations(id));
    }

    @PutMapping("/{id}/credit")
    public ApiResponse<UserResponse> adjustCredit(@PathVariable Long id,
                                                  @Valid @RequestBody CreditAdjustRequest request) {
        return ApiResponse.success(userCreditService.adjustCredit(id, request));
    }

    @PutMapping("/{id}/restriction")
    public ApiResponse<UserResponse> setRestriction(@PathVariable Long id,
                                                    @Valid @RequestBody RestrictRequest request) {
        return ApiResponse.success(userCreditService.setRestriction(id, request));
    }

    @PostMapping
    public ApiResponse<UserResponse> create(@Valid @RequestBody CreateUserRequest request) {
        return ApiResponse.success(userAdminService.create(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<UserResponse> update(@PathVariable Long id, @Valid @RequestBody UpdateUserRequest request) {
        return ApiResponse.success(userAdminService.update(id, request));
    }

    @PutMapping("/{id}/status")
    public ApiResponse<UserResponse> updateStatus(@PathVariable Long id,
                                                  @Valid @RequestBody UpdateUserStatusRequest request) {
        return ApiResponse.success(userAdminService.updateStatus(id, request));
    }

    @PutMapping("/{id}/password")
    public ApiResponse<Void> resetPassword(@PathVariable Long id, @Valid @RequestBody ResetPasswordRequest request) {
        userAdminService.resetPassword(id, request);
        return ApiResponse.success(null);
    }
}
