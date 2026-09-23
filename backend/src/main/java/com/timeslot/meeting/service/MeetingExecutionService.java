/**
 * 文件职责：编排参与人管理、签到签退、考勤、执行记录及自动会议处理。
 * 接口：由 MeetingExecutionController 调用。
 * 方法：listAttendees/addAttendee/removeAttendee 管理参与人；checkIn/checkOut/attendance 处理与查询考勤；saveExecutionRecord/executionRecord 保存和查询实况；myMeetings 查询本人会议；processDueExecutions/sendStartReminders/markNoShows 执行定时流程；其余方法校验操作者、预约和目标用户。
*/

package com.timeslot.meeting.service;

import com.timeslot.common.api.ErrorCode;
import com.timeslot.common.exception.BusinessException;
import com.timeslot.common.security.AuthenticatedUser;
import com.timeslot.common.security.CurrentUserProvider;
import com.timeslot.meeting.domain.Attendee;
import com.timeslot.meeting.domain.AttendeeRole;
import com.timeslot.meeting.domain.AttendeeStatus;
import com.timeslot.meeting.domain.MeetingExecution;
import com.timeslot.meeting.domain.NotificationType;
import com.timeslot.meeting.domain.UserRef;
import com.timeslot.meeting.dto.AddAttendeeRequest;
import com.timeslot.meeting.dto.AttendanceView;
import com.timeslot.meeting.dto.AttendeeView;
import com.timeslot.meeting.dto.MeetingExecutionView;
import com.timeslot.meeting.dto.MeetingExecutionRecordView;
import com.timeslot.meeting.dto.SaveMeetingExecutionRequest;
import com.timeslot.meeting.mapper.MeetingExecutionMapper;
import com.timeslot.reservation.domain.Reservation;
import com.timeslot.reservation.domain.ReservationStatus;
import com.timeslot.reservation.service.ReservationQueryService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * 会议执行域核心服务：参与人管理、签到/签退、No-Show 判定与开始提醒。
 *
 * 边界约束：
 * - 预约业务事实只通过 {@link ReservationQueryService} 只读获取；
 * - 永不写 reservation.status，No-Show 只落在本域 reservation_attendee；
 * - 写操作仅作用于 meeting 域表。
 */
@Service
public class MeetingExecutionService {
    /** 签到提前开放窗口：开始前 15 分钟可签到。 */
    static final int CHECK_IN_EARLY_MINUTES = 15;
    /** 签退宽限：会议结束后 60 分钟内仍可补签退。 */
    static final int CHECK_OUT_GRACE_MINUTES = 60;
    /** 会议开始提醒提前量。 */
    static final int REMINDER_LEAD_MINUTES = 30;
    /** No-Show 调度回扫窗口（天），保证调度中断后可补判。 */
    static final int EXECUTION_LOOKBACK_DAYS = 7;

    private final MeetingExecutionMapper attendeeMapper;
    private final ReservationQueryService reservationQueryService;
    private final NotificationService notificationService;
    private final CurrentUserProvider currentUserProvider;
    private final Clock clock;

    public MeetingExecutionService(MeetingExecutionMapper attendeeMapper,
                                   ReservationQueryService reservationQueryService,
                                   NotificationService notificationService,
                                   CurrentUserProvider currentUserProvider,
                                   Clock clock) {
        this.attendeeMapper = attendeeMapper;
        this.reservationQueryService = reservationQueryService;
        this.notificationService = notificationService;
        this.currentUserProvider = currentUserProvider;
        this.clock = clock;
    }

    // ---------------------------------------------------------------------
    // 参与人管理
    // ---------------------------------------------------------------------

    @Transactional
    public List<AttendeeView> listAttendees(Long reservationId) {
        Reservation reservation = reservationQueryService.requireReservation(reservationId);
        ensureOrganizerPresent(reservation);
        return attendeeMapper.findByReservationId(reservationId).stream().map(AttendeeView::from).toList();
    }

    @Transactional
    public AttendeeView addAttendee(Long reservationId, AddAttendeeRequest request) {
        AuthenticatedUser operator = currentUserProvider.getRequired();
        Reservation reservation = reservationQueryService.requireReservation(reservationId);
        requireManager(reservation, operator, "仅预约创建人或管理员可以管理参与人");
        requireExecutionOpen(reservation, "该预约已取消或被驳回，不能新增参与人");
        if (LocalDateTime.now(clock).isAfter(reservation.getEndTime())) {
            throw new BusinessException(ErrorCode.RESERVATION_INVALID_STATE, "会议已结束，不能新增参与人");
        }
        UserRef target = resolveTargetUser(request);

        ensureOrganizerPresent(reservation);
        if (attendeeMapper.countByReservationId(reservationId) >= reservation.getParticipantCount()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, HttpStatus.BAD_REQUEST,
                    "参与人数已达预约申报上限（" + reservation.getParticipantCount() + " 人）");
        }
        try {
            attendeeMapper.insertAttendee(reservationId, target.getUserId());
        } catch (DuplicateKeyException duplicateKeyException) {
            throw new BusinessException(ErrorCode.DUPLICATE_REQUEST, "该用户已是会议参与人");
        }
        notificationService.notify(target.getUserId(), NotificationType.ATTENDEE_ADDED, "被加入会议",
                "你被加入会议「" + reservation.getTitle() + "」（" + reservation.getRoomName() + " "
                        + reservation.getStartTime() + " 开始），请按时签到。",
                reservationId, null);
        return AttendeeView.from(attendeeMapper.findRow(reservationId, target.getUserId()));
    }

    @Transactional
    public void removeAttendee(Long reservationId, Long targetUserId) {
        AuthenticatedUser operator = currentUserProvider.getRequired();
        Reservation reservation = reservationQueryService.requireReservation(reservationId);
        requireManager(reservation, operator, "仅预约创建人或管理员可以管理参与人");
        requireExecutionOpen(reservation, "该预约已取消或被驳回，不能移除参与人");
        Attendee row = attendeeMapper.findRow(reservationId, targetUserId);
        if (row == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, HttpStatus.NOT_FOUND, "该用户不是会议参与人");
        }
        if (row.getAttendeeRole() == AttendeeRole.ORGANIZER) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, HttpStatus.BAD_REQUEST, "组织者不能被移出会议");
        }
        if (row.getAttendanceStatus() != AttendeeStatus.EXPECTED) {
            throw new BusinessException(ErrorCode.RESERVATION_INVALID_STATE, "该参与人已有出勤事实，不能移除");
        }
        attendeeMapper.deleteById(row.getId());
        notificationService.notify(targetUserId, NotificationType.ATTENDEE_REMOVED, "被移出会议",
                "你已被移出会议「" + reservation.getTitle() + "」。", reservationId, null);
    }

    // ---------------------------------------------------------------------
    // 签到 / 签退
    // ---------------------------------------------------------------------

    @Transactional
    public AttendeeView checkIn(Long reservationId) {
        AuthenticatedUser user = currentUserProvider.getRequired();
        Reservation reservation = requireExecutableReservation(reservationId);
        LocalDateTime now = LocalDateTime.now(clock);
        if (now.isBefore(reservation.getStartTime().minusMinutes(CHECK_IN_EARLY_MINUTES))) {
            throw new BusinessException(ErrorCode.RESERVATION_INVALID_STATE,
                    "签到尚未开始，会议开始前 " + CHECK_IN_EARLY_MINUTES + " 分钟开放签到");
        }
        if (!now.isBefore(reservation.getEndTime())) {
            throw new BusinessException(ErrorCode.RESERVATION_INVALID_STATE, "会议已结束，签到窗口已关闭");
        }
        Attendee row = requireOwnAttendeeRow(reservationId, user.userId());
        // 幂等：重复签到直接返回当前出勤事实，不产生副作用。
        if (row.getAttendanceStatus() == AttendeeStatus.CHECKED_IN
                || row.getAttendanceStatus() == AttendeeStatus.CHECKED_OUT) {
            return AttendeeView.from(row);
        }
        attendeeMapper.markCheckedIn(row.getId(), now);
        return AttendeeView.from(attendeeMapper.findRow(reservationId, user.userId()));
    }

    @Transactional
    public AttendeeView checkOut(Long reservationId) {
        AuthenticatedUser user = currentUserProvider.getRequired();
        Reservation reservation = requireExecutableReservation(reservationId);
        LocalDateTime now = LocalDateTime.now(clock);
        if (now.isAfter(reservation.getEndTime().plusMinutes(CHECK_OUT_GRACE_MINUTES))) {
            throw new BusinessException(ErrorCode.RESERVATION_INVALID_STATE,
                    "已超过签退宽限期（会议结束后 " + CHECK_OUT_GRACE_MINUTES + " 分钟内有效）");
        }
        Attendee row = requireOwnAttendeeRow(reservationId, user.userId());
        if (row.getAttendanceStatus() == AttendeeStatus.EXPECTED) {
            throw new BusinessException(ErrorCode.RESERVATION_INVALID_STATE, "尚未签到，不能签退");
        }
        if (row.getAttendanceStatus() == AttendeeStatus.NO_SHOW) {
            throw new BusinessException(ErrorCode.RESERVATION_INVALID_STATE, "该参与人已被判定缺席，不能签退");
        }
        // 幂等：重复签退直接返回当前出勤事实。
        if (row.getAttendanceStatus() == AttendeeStatus.CHECKED_OUT) {
            return AttendeeView.from(row);
        }
        attendeeMapper.markCheckedOut(row.getId(), now);
        return AttendeeView.from(attendeeMapper.findRow(reservationId, user.userId()));
    }

    @Transactional
    public AttendanceView attendance(Long reservationId) {
        AuthenticatedUser user = currentUserProvider.getRequired();
        Reservation reservation = reservationQueryService.requireReservation(reservationId);
        ensureOrganizerPresent(reservation);
        List<Attendee> rows = attendeeMapper.findByReservationId(reservationId);
        boolean participant = rows.stream().anyMatch(r -> r.getUserId().equals(user.userId()));
        if (!participant && !reservation.getUserId().equals(user.userId()) && !"ADMIN".equals(user.role())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, HttpStatus.FORBIDDEN, "无权查看该会议出勤");
        }
        AttendeeView mine = rows.stream()
                .filter(r -> r.getUserId().equals(user.userId()))
                .map(AttendeeView::from).findFirst().orElse(null);
        return new AttendanceView(reservationId, rows.size(),
                rows.stream().filter(r -> r.getAttendanceStatus() == AttendeeStatus.EXPECTED).count(),
                rows.stream().filter(r -> r.getAttendanceStatus() == AttendeeStatus.CHECKED_IN).count(),
                rows.stream().filter(r -> r.getAttendanceStatus() == AttendeeStatus.CHECKED_OUT).count(),
                rows.stream().filter(r -> r.getAttendanceStatus() == AttendeeStatus.NO_SHOW).count(),
                rows.stream().map(AttendeeView::from).toList(), mine);
    }

    // ---------------------------------------------------------------------
    // 会议级实际使用记录
    // ---------------------------------------------------------------------

    /**
     * 由预约组织者或管理员保存一场会议的实际执行结果。
     * 预约的 start_time/end_time 是计划时间，本方法只写 meeting_execution，二者不混用。
     */
    @Transactional
    public MeetingExecutionRecordView saveExecutionRecord(Long reservationId, SaveMeetingExecutionRequest request) {
        if (request == null || request.actualStartTime() == null || request.actualEndTime() == null
                || request.actualAttendeeCount() == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, HttpStatus.BAD_REQUEST,
                    "实际开始时间、实际结束时间和实际参会人数不能为空");
        }
        if (!request.actualEndTime().isAfter(request.actualStartTime())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, HttpStatus.BAD_REQUEST,
                    "实际结束时间必须晚于实际开始时间");
        }
        if (request.actualAttendeeCount() < 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, HttpStatus.BAD_REQUEST,
                    "实际参会人数不能为负数");
        }

        AuthenticatedUser operator = currentUserProvider.getRequired();
        Reservation reservation = reservationQueryService.requireReservation(reservationId);
        requireManager(reservation, operator, "仅预约组织者或管理员可以登记会议实际使用记录");
        if (reservation.getStatus() != ReservationStatus.CONFIRMED) {
            throw new BusinessException(ErrorCode.RESERVATION_INVALID_STATE,
                    "只有已确认的预约可以登记会议实际使用记录");
        }
        if (LocalDateTime.now(clock).isBefore(reservation.getEndTime())) {
            throw new BusinessException(ErrorCode.RESERVATION_INVALID_STATE,
                    "会议尚未结束，不能登记实际使用记录");
        }
        if (request.actualEndTime().isAfter(LocalDateTime.now(clock))) {
            throw new BusinessException(ErrorCode.RESERVATION_INVALID_STATE,
                    "实际结束时间不能晚于当前业务时间");
        }

        attendeeMapper.upsertExecutionRecord(reservationId, request.actualStartTime(), request.actualEndTime(),
                request.actualAttendeeCount(), operator.userId());
        return MeetingExecutionRecordView.from(attendeeMapper.findExecutionRecord(reservationId));
    }

    @Transactional(readOnly = true)
    public MeetingExecutionRecordView executionRecord(Long reservationId) {
        AuthenticatedUser operator = currentUserProvider.getRequired();
        Reservation reservation = reservationQueryService.requireReservation(reservationId);
        requireManager(reservation, operator, "仅预约组织者或管理员可以查看会议实际使用记录");
        return MeetingExecutionRecordView.from(attendeeMapper.findExecutionRecord(reservationId));
    }

    // ---------------------------------------------------------------------
    // 我的会议
    // ---------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<MeetingExecutionView> myMeetings() {
        Long userId = currentUserProvider.getRequired().userId();
        return attendeeMapper.findMyMeetings(userId).stream().map(MeetingExecutionView::from).toList();
    }

    // ---------------------------------------------------------------------
    // 调度入口：开始提醒 + No-Show 判定（幂等，可重复执行）
    // ---------------------------------------------------------------------

    public void processDueExecutions(LocalDateTime now) {
        sendStartReminders(now);
        markNoShows(now);
    }

    private void sendStartReminders(LocalDateTime now) {
        LocalDateTime to = now.plusMinutes(REMINDER_LEAD_MINUTES);
        for (Reservation reservation : reservationQueryService.findConfirmedStartingBetween(now, to)) {
            ensureOrganizerPresent(reservation);
            String startKey = reservation.getStartTime().truncatedTo(ChronoUnit.MINUTES).toString();
            for (Attendee attendee : attendeeMapper.findByReservationId(reservation.getId())) {
                notificationService.notify(attendee.getUserId(), NotificationType.MEETING_REMINDER, "会议即将开始",
                        "「" + reservation.getTitle() + "」将于 " + reservation.getStartTime() + " 在 "
                                + reservation.getRoomName() + " 开始，请按时签到。",
                        reservation.getId(), "MEETING_REMINDER:" + reservation.getId() + ":"
                                + attendee.getUserId() + ":" + startKey);
            }
        }
    }

    private void markNoShows(LocalDateTime now) {
        for (Reservation reservation : reservationQueryService.findConfirmedEndedBefore(now, EXECUTION_LOOKBACK_DAYS)) {
            ensureOrganizerPresent(reservation);
            List<Attendee> expected = attendeeMapper.findByReservationId(reservation.getId()).stream()
                    .filter(a -> a.getAttendanceStatus() == AttendeeStatus.EXPECTED)
                    .toList();
            if (expected.isEmpty()) continue;
            // SQL 仅命中 EXPECTED 行：并发或重复调度不会产生重复副作用。
            attendeeMapper.markNoShowForExpected(reservation.getId());
            for (Attendee attendee : expected) {
                notificationService.notify(attendee.getUserId(), NotificationType.NO_SHOW_MARKED, "缺席记录",
                        "会议「" + reservation.getTitle() + "」已结束，你未签到，已被记录为 NO_SHOW。",
                        reservation.getId(),
                        "NO_SHOW_MARKED:" + reservation.getId() + ":" + attendee.getUserId());
            }
        }
    }

    // ---------------------------------------------------------------------
    // 内部规则
    // ---------------------------------------------------------------------

    /** 组织者（预约创建人）行幂等补齐：首次触达执行流程时落一行 ORGANIZER。 */
    private void ensureOrganizerPresent(Reservation reservation) {
        attendeeMapper.insertOrganizerIgnore(reservation.getId(), reservation.getUserId());
    }

    private Reservation requireExecutableReservation(Long reservationId) {
        Reservation reservation = reservationQueryService.requireReservation(reservationId);
        if (reservation.getStatus() != ReservationStatus.CONFIRMED) {
            throw new BusinessException(ErrorCode.RESERVATION_INVALID_STATE, "预约未确认，不能执行签到签退");
        }
        ensureOrganizerPresent(reservation);
        return reservation;
    }

    private Attendee requireOwnAttendeeRow(Long reservationId, Long userId) {
        Attendee row = attendeeMapper.findRow(reservationId, userId);
        if (row == null) {
            throw new BusinessException(ErrorCode.FORBIDDEN, HttpStatus.FORBIDDEN, "仅会议参与人可以执行该操作");
        }
        return row;
    }

    private void requireManager(Reservation reservation, AuthenticatedUser user, String message) {
        if (!reservation.getUserId().equals(user.userId()) && !"ADMIN".equals(user.role())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, HttpStatus.FORBIDDEN, message);
        }
    }

    /** CANCELLED / REJECTED 的预约只保留历史，不再接受参与人结构变更。 */
    private void requireExecutionOpen(Reservation reservation, String message) {
        if (reservation.getStatus() == ReservationStatus.CANCELLED
                || reservation.getStatus() == ReservationStatus.REJECTED) {
            throw new BusinessException(ErrorCode.RESERVATION_INVALID_STATE, message);
        }
    }

    private UserRef resolveTargetUser(AddAttendeeRequest request) {
        if (request == null || (request.userId() == null && (request.username() == null || request.username().isBlank()))) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, HttpStatus.BAD_REQUEST, "必须提供 userId 或 username");
        }
        UserRef target = request.userId() != null
                ? attendeeMapper.findUserById(request.userId())
                : attendeeMapper.findUserByUsername(request.username().trim());
        if (target == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, HttpStatus.NOT_FOUND, "用户不存在");
        }
        return target;
    }
}
