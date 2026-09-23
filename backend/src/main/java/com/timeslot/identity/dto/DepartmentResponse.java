/**
 * 文件职责：向前端返回部门基础信息。
 * 接口：在 Controller、Service 和前端 API 之间传递结构化数据。
 * 方法：from 将 Department 领域对象转换为 API 响应。
*/

package com.timeslot.identity.dto;

import com.timeslot.identity.domain.Department;

public record DepartmentResponse(Long id, String deptName, String description) {
    public static DepartmentResponse from(Department department) {
        return new DepartmentResponse(department.id(), department.deptName(), department.description());
    }
}
