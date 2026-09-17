/**
 * 文件职责：定义 身份与用户 的请求或响应 DTO。
 * 接口：在 Controller、Service 和前端 API 之间传递结构化数据。
 */
package com.timeslot.identity.dto;

import com.timeslot.identity.domain.Department;

public record DepartmentResponse(Long id, String deptName, String description) {
    public static DepartmentResponse from(Department department) {
        return new DepartmentResponse(department.id(), department.deptName(), department.description());
    }
}
