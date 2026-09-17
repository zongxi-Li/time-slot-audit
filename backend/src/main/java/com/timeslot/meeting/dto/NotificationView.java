/**
 * 文件职责：定义 会议执行 的请求或响应 DTO。
 * 接口：在 Controller、Service 和前端 API 之间传递结构化数据。
 */
package com.timeslot.meeting.dto;

import com.timeslot.meeting.domain.Notification;

import java.time.LocalDateTime;

/** 个人通知视图。 */
public record NotificationView(Long id, String type, String title, String content, Long reservationId,
                               boolean read, LocalDateTime createdAt, LocalDateTime readAt) {
    public static NotificationView from(Notification notification) {
        return new NotificationView(notification.getId(),
                notification.getType() == null ? null : notification.getType().name(),
                notification.getTitle(), notification.getContent(), notification.getReservationId(),
                notification.isRead(), notification.getCreatedAt(), notification.getReadAt());
    }
}
