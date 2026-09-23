/**
 * 文件职责：管理员重置用户密码接口的请求 DTO 及密码校验。
 * 接口：在 Controller、Service 和前端 API 之间传递结构化数据。
 * 方法：无显式业务方法；record 组件接收新密码。
*/

package com.timeslot.identity.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ResetPasswordRequest(
        @NotBlank(message = "新密码不能为空") @Size(min = 6, max = 100, message = "密码长度需在 6-100 之间") String password) {
}
