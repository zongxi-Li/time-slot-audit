/**
 * 文件职责：创建或修改部门接口共用的请求 DTO。
 * 接口：在 Controller、Service 和前端 API 之间传递结构化数据。
 * 方法：无显式业务方法；record 组件承载部门名称、说明及校验约束。
*/

package com.timeslot.identity.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record DepartmentRequest(
        @NotBlank(message = "部门名称不能为空") @Size(max = 50) String deptName,
        @Size(max = 200) String description) {
}
