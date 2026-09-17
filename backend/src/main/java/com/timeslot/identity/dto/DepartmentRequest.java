/**
 * 文件职责：定义 身份与用户 的请求或响应 DTO。
 * 接口：在 Controller、Service 和前端 API 之间传递结构化数据。
 */
package com.timeslot.identity.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record DepartmentRequest(
        @NotBlank(message = "部门名称不能为空") @Size(max = 50) String deptName,
        @Size(max = 200) String description) {
}
