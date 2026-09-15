package com.timeslot.identity.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateUserRequest(
        @Size(max = 50) String realName,
        @Email(message = "邮箱格式不正确") @Size(max = 100) String email,
        @Size(max = 20) String phone,
        Long departmentId,
        @Pattern(regexp = "USER|ADMIN", message = "角色仅支持 USER/ADMIN") String role) {
}
