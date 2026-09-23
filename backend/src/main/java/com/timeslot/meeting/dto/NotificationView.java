/**
 * 文件职责：通知列表 API 的展示数据。
 * 接口：在 Controller、Service 和前端 API 之间传递结构化数据。
 * 方法：from 将 Notification 领域对象转换为通知视图。
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
