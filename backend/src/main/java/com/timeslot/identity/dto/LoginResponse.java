/**
 * 文件职责：登录成功响应，组合认证令牌和用户资料。
 * 接口：在 Controller、Service 和前端 API 之间传递结构化数据。
 * 方法：无显式业务方法；record 访问器由 Java 自动生成。
 */

package com.timeslot.identity.dto;

public record LoginResponse(String token, UserResponse user) {
}
