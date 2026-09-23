/**
 * 文件职责：管理员修改用户资料接口的请求 DTO 及字段校验。
 * 接口：在 Controller、Service 和前端 API 之间传递结构化数据。
 * 方法：无显式业务方法；record 组件接收待更新的用户资料。
*/

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
