/**
 * 文件职责：创建、查询和标记用户通知。
 * 接口：由 NotificationController 和会议服务调用。
 */
package com.timeslot.meeting.service;

import com.timeslot.common.api.ErrorCode;
import com.timeslot.common.exception.BusinessException;
import com.timeslot.meeting.domain.Notification;
import com.timeslot.meeting.domain.NotificationType;
import com.timeslot.meeting.dto.NotificationView;
import com.timeslot.meeting.mapper.NotificationMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * meeting 域业务内通知。
 *
 * 幂等策略：uk_notification_dedup(user_id, dedup_key) + INSERT IGNORE。
 * 调度类通知（提醒、No-Show）使用确定性 dedup_key，重复执行不产生重复数据；
 * 用户动作类通知（加入/移出）每次动作都应送达，使用随机 dedup_key。
 */
@Service
public class NotificationService {
    private final NotificationMapper notificationMapper;
    private final Clock clock;

    public NotificationService(NotificationMapper notificationMapper, Clock clock) {
        this.notificationMapper = notificationMapper;
        this.clock = clock;
    }

    /** 发送一条通知；dedupKey 为空表示用户动作类通知（不去重）。 */
    public void notify(Long userId, NotificationType type, String title, String content,
                       Long reservationId, String dedupKey) {
        Notification notification = new Notification();
        notification.setUserId(userId);
        notification.setType(type);
        notification.setTitle(title);
        notification.setContent(content);
        notification.setReservationId(reservationId);
        notification.setDedupKey(dedupKey == null
                ? type.name() + ":" + UUID.randomUUID()
                : dedupKey);
        notificationMapper.insertIgnore(notification);
    }

    @Transactional(readOnly = true)
    public List<NotificationView> list(Long userId, boolean unreadOnly) {
        return notificationMapper.findByUser(userId, unreadOnly).stream().map(NotificationView::from).toList();
    }

    @Transactional(readOnly = true)
    public int unreadCount(Long userId) {
        return notificationMapper.countUnread(userId);
    }

    @Transactional
    public void markRead(Long userId, Long id) {
        int updated = notificationMapper.markRead(id, userId, LocalDateTime.now(clock));
        if (updated == 0) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, HttpStatus.NOT_FOUND, "通知不存在或已读");
        }
    }

    @Transactional
    public void markAllRead(Long userId) {
        notificationMapper.markAllRead(userId, LocalDateTime.now(clock));
    }
}
