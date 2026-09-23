/**
 * 文件职责：用户登录接口的请求 DTO，校验用户名和密码非空。
 * 接口：在 Controller、Service 和前端 API 之间传递结构化数据。
 * 方法：无显式业务方法；record 组件接收登录凭据。
 */

package com.timeslot.identity.dto;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(@NotBlank String username, @NotBlank String password) {
}
