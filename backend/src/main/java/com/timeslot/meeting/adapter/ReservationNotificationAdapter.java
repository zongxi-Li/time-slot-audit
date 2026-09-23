/**
 * 文件职责：把预约生命周期事件转换为 meeting 领域中的用户通知。
 * 接口：实现 ReservationNotificationPort。
 *
 * 边界：仅调用本域 NotificationService 与 identity 的 UserMapper；不写 reservation 表。
 * 待审批通知向所有活跃 ADMIN 广播，新增管理员亦会自动覆盖，无需硬编码账号。
 * 方法：notifyCreated/notifyPendingApproval/notifyApproved/notifyRejected/notifyCancelled 分别发送创建、待审批、通过、驳回和取消通知。
*/

package com.timeslot.meeting.adapter;

import com.timeslot.identity.mapper.UserMapper;
import com.timeslot.meeting.domain.NotificationType;
import com.timeslot.meeting.service.NotificationService;
import com.timeslot.reservation.domain.ReservationStatus;
import com.timeslot.reservation.spi.ReservationNotificationPort;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
public class ReservationNotificationAdapter implements ReservationNotificationPort {

    private static final String ADMIN_ROLE = "ADMIN";

    private final NotificationService notificationService;
    private final UserMapper userMapper;

    public ReservationNotificationAdapter(NotificationService notificationService, UserMapper userMapper) {
        this.notificationService = notificationService;
        this.userMapper = userMapper;
    }

    @Override
    public void notifyCreated(Long userId, Long reservationId, String title, String roomName,
                              LocalDateTime start, ReservationStatus status) {
        String content = status == ReservationStatus.PENDING
                ? "你创建的预约「" + title + "」（" + roomName + "，" + start + " 开始）已提交，等待管理员审批。"
                : "你创建的预约「" + title + "」（" + roomName + "，" + start + " 开始）已确认，请准时参会并签到。";
        notificationService.notify(userId, NotificationType.RESERVATION_CREATED, "预约创建成功", content,
                reservationId, null);
    }

    @Override
    public void notifyPendingApproval(Long reservationId, String title, String roomName, LocalDateTime start) {
        List<Long> admins = userMapper.findIdsByRole(ADMIN_ROLE);
        for (Long adminId : admins) {
            notificationService.notify(adminId, NotificationType.RESERVATION_PENDING_APPROVAL, "待审批预约",
                    "有新的待审批预约「" + title + "」（" + roomName + "，" + start + " 开始），请及时处理。",
                    reservationId, null);
        }
    }

    @Override
    public void notifyApproved(Long userId, Long reservationId, String title) {
        notificationService.notify(userId, NotificationType.RESERVATION_APPROVED, "预约已通过",
                "你的预约「" + title + "」已审批通过，请准时参会并签到。", reservationId, null);
    }

    @Override
    public void notifyRejected(Long userId, Long reservationId, String title, String reason) {
        String content = "你的预约「" + title + "」未通过审批"
                + (reason == null || reason.isBlank() ? "。" : "，原因：" + reason + "。");
        notificationService.notify(userId, NotificationType.RESERVATION_REJECTED, "预约被驳回", content,
                reservationId, null);
    }

    @Override
    public void notifyCancelled(Long userId, Long reservationId, String title) {
        notificationService.notify(userId, NotificationType.RESERVATION_CANCELLED, "预约已取消",
                "你的预约「" + title + "」已取消。", reservationId, null);
    }
}
