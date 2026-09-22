/**
 * 文件职责：把预约创建时的初始参与人登记适配到 meeting 域参与人能力。
 * 接口：实现 ReservationAttendeePort。
 *
 * 边界：仅写本域 reservation_attendee 表；用户解析复用 MeetingExecutionMapper 的
 * 跨域只读查询，不写 reservation 表。校验失败抛业务异常，随调用方事务整体回滚。
 */
package com.timeslot.meeting.adapter;

import com.timeslot.common.api.ErrorCode;
import com.timeslot.common.exception.BusinessException;
import com.timeslot.meeting.domain.NotificationType;
import com.timeslot.meeting.domain.UserRef;
import com.timeslot.meeting.mapper.MeetingExecutionMapper;
import com.timeslot.meeting.service.NotificationService;
import com.timeslot.reservation.domain.Reservation;
import com.timeslot.reservation.spi.ReservationAttendeePort;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.List;

@Component
public class ReservationAttendeeAdapter implements ReservationAttendeePort {

    private final MeetingExecutionMapper attendeeMapper;
    private final NotificationService notificationService;

    public ReservationAttendeeAdapter(MeetingExecutionMapper attendeeMapper,
                                      NotificationService notificationService) {
        this.attendeeMapper = attendeeMapper;
        this.notificationService = notificationService;
    }

    @Override
    public void attachInitialAttendees(Reservation reservation, List<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return;
        }
        // 去重并排除创建人：组织者行由执行域幂等补齐，重复选择创建人不视为错误。
        LinkedHashSet<Long> distinct = new LinkedHashSet<>(userIds);
        distinct.remove(reservation.getUserId());
        if (distinct.isEmpty()) {
            return;
        }

        attendeeMapper.insertOrganizerIgnore(reservation.getId(), reservation.getUserId());
        // 与 addAttendee 同一上限口径：组织者计入申报人数。
        if (attendeeMapper.countByReservationId(reservation.getId()) + distinct.size()
                > reservation.getParticipantCount()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, HttpStatus.BAD_REQUEST,
                    "初始参与人数超过预约申报上限（" + reservation.getParticipantCount() + " 人，含组织者）");
        }

        for (Long userId : distinct) {
            UserRef target = attendeeMapper.findUserById(userId);
            if (target == null) {
                throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, HttpStatus.NOT_FOUND,
                        "参与人不存在（userId=" + userId + "）");
            }
            try {
                attendeeMapper.insertAttendee(reservation.getId(), userId);
            } catch (DuplicateKeyException duplicateKeyException) {
                throw new BusinessException(ErrorCode.DUPLICATE_REQUEST, "该用户已是会议参与人");
            }
            notificationService.notify(userId, NotificationType.ATTENDEE_ADDED, "被加入会议",
                    "你被加入会议「" + reservation.getTitle() + "」（" + reservation.getRoomName() + " "
                            + reservation.getStartTime() + " 开始），请按时签到。",
                    reservation.getId(), null);
        }
    }
}
