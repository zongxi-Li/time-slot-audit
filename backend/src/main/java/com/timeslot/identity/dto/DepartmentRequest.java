package com.timeslot.identity.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record DepartmentRequest(
        @NotBlank(message = "部门名称不能为空") @Size(max = 50) String deptName,
        @Size(max = 200) String description) {
}
