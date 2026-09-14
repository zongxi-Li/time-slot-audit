package com.timeslot.identity.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateUserRequest(
        @NotBlank(message = "用户名不能为空") @Size(min = 2, max = 50, message = "用户名长度需在 2-50 之间") String username,
        @NotBlank(message = "密码不能为空") @Size(min = 6, max = 100, message = "密码长度需在 6-100 之间") String password,
        @NotBlank(message = "真实姓名不能为空") @Size(max = 50) String realName,
        @Email(message = "邮箱格式不正确") @Size(max = 100) String email,
        @Size(max = 20) String phone,
        @NotBlank(message = "角色不能为空") @Pattern(regexp = "USER|ADMIN", message = "角色仅支持 USER/ADMIN") String role,
        Long departmentId) {
}
