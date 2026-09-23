/**
 * 文件职责：表示部门领域数据，包括 ID、名称、说明和创建时间。
 * 接口：供本域 Service、Mapper 和 DTO 转换使用。
 * 方法：无显式业务方法；record 访问器由 Java 自动生成。
*/

package com.timeslot.identity.domain;

import java.time.LocalDateTime;

public record Department(Long id, String deptName, String description, LocalDateTime createdAt) {
}
