/**
 * 文件职责：提供 会议执行 HTTP 接口，将请求交给 Service 处理并返回统一响应。
 * 接口：GET /api/notifications；
 *        GET /api/notifications/unread-count；
 *        POST /api/notifications/{id}/read；
 *        POST /api/notifications/read-all。
 */
package com.timeslot.meeting.controller;

import com.timeslot.common.api.ApiResponse;
import com.timeslot.common.security.CurrentUserProvider;
import com.timeslot.meeting.dto.NotificationView;
import com.timeslot.meeting.service.NotificationService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {
    private final NotificationService notificationService;
    private final CurrentUserProvider currentUserProvider;

    public NotificationController(NotificationService notificationService, CurrentUserProvider currentUserProvider) {
        this.notificationService = notificationService;
        this.currentUserProvider = currentUserProvider;
    }

    @GetMapping
    public ApiResponse<List<NotificationView>> list(@RequestParam(defaultValue = "false") boolean unreadOnly) {
        return ApiResponse.success(notificationService.list(currentUserProvider.getRequired().userId(), unreadOnly));
    }

    @GetMapping("/unread-count")
    public ApiResponse<Integer> unreadCount() {
        return ApiResponse.success(notificationService.unreadCount(currentUserProvider.getRequired().userId()));
    }

    @PostMapping("/{id}/read")
    public ApiResponse<Void> markRead(@PathVariable Long id) {
        notificationService.markRead(currentUserProvider.getRequired().userId(), id);
        return ApiResponse.success(null);
    }

    @PostMapping("/read-all")
    public ApiResponse<Void> markAllRead() {
        notificationService.markAllRead(currentUserProvider.getRequired().userId());
        return ApiResponse.success(null);
    }
}
