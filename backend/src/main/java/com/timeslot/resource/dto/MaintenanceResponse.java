/**
 * 文件职责：向前端返回会议室维护计划及其起止时间、状态等数据。
 * 接口：在 Controller、Service 和前端 API 之间传递结构化数据。
 * 方法：无显式业务方法；record 访问器由 Java 自动生成。
*/

package com.timeslot.resource.dto;

import java.time.LocalDateTime;

public record MaintenanceResponse(Long id, Long roomId, String reason, LocalDateTime startTime,
                                  LocalDateTime endTime, String status, Long createdBy, LocalDateTime createdAt) {
}
