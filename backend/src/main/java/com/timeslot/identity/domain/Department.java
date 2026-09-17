/**
 * 文件职责：定义 身份与用户 的实体、值对象或枚举。
 * 接口：供本域 Service、Mapper 和 DTO 转换使用。
 */
package com.timeslot.identity.domain;

import java.time.LocalDateTime;

public record Department(Long id, String deptName, String description, LocalDateTime createdAt) {
}
