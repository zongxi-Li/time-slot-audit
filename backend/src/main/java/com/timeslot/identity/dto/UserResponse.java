/**
 * 文件职责：用户资料查询和管理接口的响应 DTO。
 * 接口：在 Controller、Service 和前端 API 之间传递结构化数据。
 * 方法：from 将 User 领域对象转换为 API 响应；record 组件定义对外用户字段。
*/

package com.timeslot.identity.dto;

import com.timeslot.identity.domain.User;

import java.time.LocalDateTime;

public record UserResponse(Long id, String username, String realName, String email, String phone,
                           String role, Integer status, Long departmentId, String departmentName,
                           Integer creditScore, LocalDateTime restrictedUntil) {
    public static UserResponse from(User user) {
        return new UserResponse(user.id(), user.username(), user.realName(), user.email(), user.phone(),
                user.role(), user.status(), user.departmentId(), user.departmentName(),
                user.creditScore(), user.restrictedUntil());
    }
}
