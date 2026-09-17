/**
 * 文件职责：验证 NotificationServiceTest 相关业务、接口安全或边界条件。
 * 接口：使用 JUnit、Mockito 或 Spring 测试工具，不属于运行时接口。
 */
package com.timeslot.meeting.service;

import com.timeslot.common.exception.BusinessException;
import com.timeslot.meeting.domain.Notification;
import com.timeslot.meeting.domain.NotificationType;
import com.timeslot.meeting.mapper.NotificationMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class NotificationServiceTest {
    @Mock NotificationMapper notificationMapper;

    private NotificationService service;
    private final Clock clock = Clock.fixed(Instant.parse("2026-09-15T02:00:00Z"), ZoneId.of("Asia/Shanghai"));

    @BeforeEach
    void setUp() {
        service = new NotificationService(notificationMapper, clock);
    }

    @Test
    void notifyWithoutDedupKeyGeneratesRandomKey() {
        service.notify(3L, NotificationType.ATTENDEE_ADDED, "被加入会议", "内容", 100L, null);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationMapper).insertIgnore(captor.capture());
        Notification notification = captor.getValue();
        assertEquals(3L, notification.getUserId());
        assertTrue(notification.getDedupKey().startsWith("ATTENDEE_ADDED:"));
    }

    @Test
    void notifyWithDedupKeyKeepsDeterministicKey() {
        service.notify(3L, NotificationType.MEETING_REMINDER, "会议即将开始", "内容", 100L,
                "MEETING_REMINDER:100:3:2026-09-15T10:10");

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationMapper).insertIgnore(captor.capture());
        assertEquals("MEETING_REMINDER:100:3:2026-09-15T10:10", captor.getValue().getDedupKey());
    }

    @Test
    void markReadThrowsWhenNotificationMissingOrNotOwned() {
        when(notificationMapper.markRead(org.mockito.ArgumentMatchers.eq(7L),
                org.mockito.ArgumentMatchers.eq(3L), org.mockito.ArgumentMatchers.any())).thenReturn(0);

        assertThrows(BusinessException.class, () -> service.markRead(3L, 7L));
    }

    @Test
    void listMapsDomainToView() {
        Notification notification = new Notification();
        notification.setId(7L);
        notification.setUserId(3L);
        notification.setType(NotificationType.ATTENDEE_ADDED);
        notification.setTitle("被加入会议");
        notification.setContent("内容");
        notification.setRead(false);
        when(notificationMapper.findByUser(3L, false)).thenReturn(List.of(notification));

        var views = service.list(3L, false);

        assertEquals(1, views.size());
        assertEquals("ATTENDEE_ADDED", views.get(0).type());
        assertEquals(7L, views.get(0).id());
    }
}
