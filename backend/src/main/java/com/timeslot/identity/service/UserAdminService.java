package com.timeslot.identity.service;

import com.timeslot.common.api.ErrorCode;
import com.timeslot.common.exception.BusinessException;
import com.timeslot.common.security.CurrentUserProvider;
import com.timeslot.identity.domain.CreditRules;
import com.timeslot.identity.domain.User;
import com.timeslot.identity.dto.CreateUserRequest;
import com.timeslot.identity.dto.ResetPasswordRequest;
import com.timeslot.identity.dto.UpdateUserRequest;
import com.timeslot.identity.dto.UpdateUserStatusRequest;
import com.timeslot.identity.dto.UserResponse;
import com.timeslot.identity.mapper.DepartmentMapper;
import com.timeslot.identity.mapper.UserMapper;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class UserAdminService {
    private final UserMapper userMapper;
    private final DepartmentMapper departmentMapper;
    private final PasswordEncoder passwordEncoder;
    private final CurrentUserProvider currentUserProvider;

    public UserAdminService(UserMapper userMapper, DepartmentMapper departmentMapper,
                            PasswordEncoder passwordEncoder, CurrentUserProvider currentUserProvider) {
        this.userMapper = userMapper;
        this.departmentMapper = departmentMapper;
        this.passwordEncoder = passwordEncoder;
        this.currentUserProvider = currentUserProvider;
    }

    public List<UserResponse> list(String keyword, Integer status) {
        return userMapper.search(blankToNull(keyword), status).stream().map(UserResponse::from).toList();
    }

    public UserResponse detail(Long id) {
        return UserResponse.from(requireUser(id));
    }

    @Transactional
    public UserResponse create(CreateUserRequest request) {
        String username = request.username().trim();
        if (userMapper.countByUsername(username) > 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, HttpStatus.BAD_REQUEST, "用户名已存在");
        }
        requireDepartment(request.departmentId());
        userMapper.insert(username, passwordEncoder.encode(request.password()), request.realName().trim(),
                request.email(), request.phone(), request.role(), 1, request.departmentId(),
                CreditRules.DEFAULT_CREDIT_SCORE, null);
        return UserResponse.from(userMapper.findByUsername(username));
    }

    @Transactional
    public UserResponse update(Long id, UpdateUserRequest request) {
        requireUser(id);
        boolean noChange = request.realName() == null && request.email() == null && request.phone() == null
                && request.departmentId() == null && request.role() == null;
        if (noChange) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, HttpStatus.BAD_REQUEST, "至少提供一个修改字段");
        }
        requireDepartment(request.departmentId());
        if (request.role() != null) {
            User user = userMapper.findById(id);
            if ("USER".equals(request.role()) && "ADMIN".equals(user.role())
                    && user.id().equals(currentUserProvider.getRequired().userId())) {
                throw new BusinessException(ErrorCode.FORBIDDEN, HttpStatus.FORBIDDEN, "不能修改自己的角色");
            }
        }
        userMapper.updateProfile(id, trimOrNull(request.realName()), trimOrNull(request.email()),
                trimOrNull(request.phone()), request.departmentId(), request.role());
        return detail(id);
    }

    @Transactional
    public UserResponse updateStatus(Long id, UpdateUserStatusRequest request) {
        User user = requireUser(id);
        if (request.status() == 0 && user.id().equals(currentUserProvider.getRequired().userId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, HttpStatus.FORBIDDEN, "不能停用自己的账号");
        }
        if (request.status() == user.status()) {
            return UserResponse.from(user);
        }
        userMapper.updateStatus(id, request.status());
        return detail(id);
    }

    @Transactional
    public void resetPassword(Long id, ResetPasswordRequest request) {
        requireUser(id);
        userMapper.updatePassword(id, passwordEncoder.encode(request.password()));
    }

    User requireUser(Long id) {
        User user = userMapper.findById(id);
        if (user == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, HttpStatus.NOT_FOUND, "用户不存在");
        }
        return user;
    }

    private void requireDepartment(Long departmentId) {
        if (departmentId != null && departmentMapper.findById(departmentId) == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, HttpStatus.BAD_REQUEST, "部门不存在");
        }
    }

    private String trimOrNull(String value) {
        return value == null ? null : value.trim();
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
