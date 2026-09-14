package com.timeslot.identity.dto;

import com.timeslot.identity.domain.Department;

public record DepartmentResponse(Long id, String deptName, String description) {
    public static DepartmentResponse from(Department department) {
        return new DepartmentResponse(department.id(), department.deptName(), department.description());
    }
}
