/**
 * 文件职责：执行预约创建、修改和取消，校验资格、容量、开放时间和时间冲突。
 * 接口：由 ReservationController 调用。
 */
package com.timeslot.reservation.service;

import com.timeslot.common.api.ErrorCode;
import com.timeslot.common.bookingwindow.BookingWindowResponse;
import com.timeslot.common.bookingwindow.BookingWindowService;
import com.timeslot.common.exception.BusinessException;
import com.timeslot.common.security.AuthenticatedUser;
import com.timeslot.common.security.CurrentUserProvider;
import com.timeslot.identity.domain.BookingQualification;
import com.timeslot.identity.service.BookingQualificationService;
import com.timeslot.reservation.domain.Reservation;
import com.timeslot.reservation.domain.ReservationEvent;
import com.timeslot.reservation.domain.ReservationStatus;
import com.timeslot.reservation.domain.ReservationStateMachine;
import com.timeslot.reservation.domain.TimeInterval;
import com.timeslot.reservation.dto.CancelReservationRequest;
import com.timeslot.reservation.dto.CreateReservationRequest;
import com.timeslot.reservation.dto.ReservationResponse;
import com.timeslot.reservation.dto.UpdateReservationRequest;
import com.timeslot.reservation.mapper.ReservationMapper;
import com.timeslot.reservation.spi.ReservationNotificationPort;
import com.timeslot.resource.dto.BookableRoomProfile;
import com.timeslot.resource.service.ResourceBookingQueryService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class ReservationService {
    /** 冲突提示中的占用时段展示格式；带完整日期以覆盖跨天预约。 */
    private static final java.time.format.DateTimeFormatter CONFLICT_SLOT_FORMATTER =
            java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final ReservationMapper reservationMapper;
    private final ResourceBookingQueryService resourceBookingQueryService;
    private final BookingQualificationService bookingQualificationService;
    private final CurrentUserProvider currentUserProvider;
    private final BookingWindowService bookingWindowService;
    private final ReservationNotificationPort notificationPort;
    private final Clock clock;

    public ReservationService(ReservationMapper reservationMapper, ResourceBookingQueryService resourceBookingQueryService,
                              BookingQualificationService bookingQualificationService,
                              CurrentUserProvider currentUserProvider, BookingWindowService bookingWindowService,
                              ReservationNotificationPort notificationPort, Clock clock) {
        this.reservationMapper = reservationMapper;
        this.resourceBookingQueryService = resourceBookingQueryService;
        this.bookingQualificationService = bookingQualificationService;
        this.currentUserProvider = currentUserProvider;
        this.bookingWindowService = bookingWindowService;
        this.notificationPort = notificationPort;
        this.clock = clock;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED, noRollbackFor = DuplicateKeyException.class)
    public ReservationResponse createReservation(CreateReservationRequest request) {
        AuthenticatedUser user = currentUserProvider.getRequired();
        Reservation existing = reservationMapper.findByUserIdAndRequestId(user.userId(), request.requestId());
        if (existing != null) return ReservationResponse.from(existing, clock);

        // 跨域只读调用 identity 公开能力：禁用/限制期/信用不足的账号不允许创建预约。
        BookingQualification qualification = bookingQualificationService.check(user.userId());
        if (!qualification.eligible()) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "当前账号不允许预约：" + qualification.reason());
        }

        LocalDateTime now = LocalDateTime.now(clock);
        TimeInterval interval = parseFutureInterval(request.startTime(), request.endTime(), now);

        // Critical ordering: lock one meeting_room row before the conflict query.
        BookableRoomProfile room = resourceBookingQueryService.lockBookableRoom(request.roomId());
        // A concurrent retry with the same requestId may have waited on this room lock.
        // Re-check after the lock so it returns the committed result instead of a false conflict.
        Reservation existingAfterLock = reservationMapper.findByUserIdAndRequestId(user.userId(), request.requestId());
        if (existingAfterLock != null) return ReservationResponse.from(existingAfterLock, clock);

        int weeks = normalizeRepeatWeeks(request.repeatWeeks());
        // 首次发生走完整规则链（含提前量）；后续周次提前量只看首次开始（同一次预约意图）。
        validateSlot(room, interval, request.participantCount(), now, null);
        List<TimeInterval> occurrences = new ArrayList<>();
        occurrences.add(interval);
        for (int i = 1; i < weeks; i++) {
            TimeInterval occurrence = new TimeInterval(interval.start().plusWeeks(i), interval.end().plusWeeks(i));
            validateRecurringOccurrence(room, occurrence, request.participantCount());
            occurrences.add(occurrence);
        }

        // 整批校验通过后才落库：任何一周冲突/不合法都不会写入任何一行（事务再兜底回滚）。
        Reservation anchor = null;
        for (int i = 0; i < occurrences.size(); i++) {
            TimeInterval occurrence = occurrences.get(i);
            Reservation reservation = new Reservation();
            // 幂等键：仅首次发生用原始 requestId（重试短路靠它），后续周次加后缀保证 (user_id, request_id) 唯一。
            reservation.setRequestId(i == 0 ? request.requestId() : request.requestId() + "~w" + i);
            reservation.setReservationNo("RSV" + UUID.randomUUID().toString().replace("-", "").substring(0, 20));
            reservation.setRoomId(room.roomId());
            reservation.setUserId(user.userId());
            reservation.setRoomName(room.roomName());
            reservation.setTitle(request.title());
            reservation.setStartTime(occurrence.start());
            reservation.setEndTime(occurrence.end());
            reservation.setParticipantCount(request.participantCount());
            reservation.setStatus(ReservationStateMachine.initialStatus(room.approvalRequired()));
            reservation.setRemark(request.remark());
            reservation.setVersion(0);
            if (i == 0) {
                try {
                    reservationMapper.insert(reservation);
                } catch (DuplicateKeyException duplicateKeyException) {
                    Reservation duplicate = reservationMapper.findByUserIdAndRequestId(user.userId(), request.requestId());
                    if (duplicate != null) return ReservationResponse.from(duplicate, clock);
                    throw duplicateKeyException;
                }
                anchor = reservation;
            } else {
                reservationMapper.insert(reservation);
            }
            // 出站通知：本人收到“创建成功”，受控分类还需广播管理员“待审批”。
            notifyReservationLifecycle(reservation);
        }
        return ReservationResponse.from(anchor, clock);
    }

    private void notifyReservationLifecycle(Reservation reservation) {
        notificationPort.notifyCreated(reservation.getUserId(), reservation.getId(), reservation.getTitle(),
                reservation.getRoomName(), reservation.getStartTime(), reservation.getStatus());
        if (reservation.getStatus() == ReservationStatus.PENDING) {
            notificationPort.notifyPendingApproval(reservation.getId(), reservation.getTitle(),
                    reservation.getRoomName(), reservation.getStartTime());
        }
    }

    /**
     * Owner-only reschedule. The new slot goes through the full rule chain again
     * (room row lock, room status, category rules, open window, half-open conflict
     * query excluding this reservation's own id). The schedule fields and the
     * recalculated status are persisted in one atomic UPDATE guarded by the
     * expected-state condition, so Response.status always equals Database.status.
     */
    @Transactional
    public ReservationResponse update(Long id, UpdateReservationRequest request) {
        AuthenticatedUser user = currentUserProvider.getRequired();
        Reservation reservation = requireOwnedEditable(id, user, ReservationEvent.RESCHEDULE);
        if (request.version() != reservation.getVersion()) {
            throw new BusinessException(ErrorCode.RESERVATION_INVALID_STATE,
                    "预约已被其他操作修改，请刷新后再提交");
        }

        LocalDateTime now = LocalDateTime.now(clock);
        TimeInterval interval = parseFutureInterval(request.startTime(), request.endTime(), now);
        BookableRoomProfile room = resourceBookingQueryService.lockBookableRoom(request.roomId());
        validateSlot(room, interval, request.participantCount(), now, reservation.getId());

        ReservationStatus expectedStatus = reservation.getStatus();
        // Approval semantics follow the (possibly new) room category: PENDING/CONFIRMED -> PENDING/CONFIRMED.
        ReservationStatus targetStatus = ReservationStateMachine.transition(expectedStatus,
                ReservationEvent.RESCHEDULE, room.approvalRequired());
        reservation.setRoomId(room.roomId());
        reservation.setRoomName(room.roomName());
        reservation.setTitle(request.title());
        reservation.setStartTime(interval.start());
        reservation.setEndTime(interval.end());
        reservation.setParticipantCount(request.participantCount());
        reservation.setRemark(request.remark());
        reservation.setStatus(targetStatus);
        int affectedRows = reservationMapper.updateScheduleAndStatus(reservation, expectedStatus.name(), request.version());
        requireAffectedRow(affectedRows);
        reservation.setVersion(reservation.getVersion() + 1);
        return ReservationResponse.from(reservation, clock);
    }

    /** Visible to the owner and read-only to ADMIN; ADMIN still cannot mutate via owner endpoints. */
    public ReservationResponse detail(Long id) {
        AuthenticatedUser user = currentUserProvider.getRequired();
        Reservation reservation = reservationMapper.findById(id);
        if (reservation == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, HttpStatus.NOT_FOUND, "预约不存在");
        }
        if (!reservation.getUserId().equals(user.userId()) && !"ADMIN".equals(user.role())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, HttpStatus.FORBIDDEN, "只能查看本人预约");
        }
        return ReservationResponse.from(reservation, clock);
    }

    public List<ReservationResponse> calendar(LocalDateTime start, LocalDateTime end, Long roomId) {
        if (start == null || end == null || !start.isBefore(end)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, HttpStatus.BAD_REQUEST, "时间范围无效");
        }
        return reservationMapper.findCalendar(start, end, roomId).stream().map(r -> ReservationResponse.from(r, clock)).toList();
    }

    public List<ReservationResponse> mine() {
        return reservationMapper.findByUserId(currentUserProvider.getRequired().userId()).stream()
                .map(r -> ReservationResponse.from(r, clock)).toList();
    }

    @Transactional
    public ReservationResponse cancel(Long id, CancelReservationRequest request) {
        AuthenticatedUser user = currentUserProvider.getRequired();
        Reservation reservation = requireOwnedEditable(id, user, ReservationEvent.OWNER_CANCEL);
        ReservationStatus targetStatus = ReservationStateMachine.transition(reservation.getStatus(),
                ReservationEvent.OWNER_CANCEL);
        String reason = request == null ? null : request.reason();
        int affectedRows = reservationMapper.cancelExpected(id, reservation.getStatus().name(),
                targetStatus.name(), reason);
        requireAffectedRow(affectedRows);
        reservation.setStatus(targetStatus);
        reservation.setVersion(reservation.getVersion() + 1);
        notificationPort.notifyCancelled(reservation.getUserId(), reservation.getId(), reservation.getTitle());
        return ReservationResponse.from(reservation, clock);
    }

    /**
     * Shared owner-side guard for update/cancel: the reservation must exist,
     * belong to the caller, be in a state the state machine allows for {@code event},
     * and not have started. ADMIN must instead use the governed force-cancel path
     * from administration (reason + audit); the verb in user-facing messages
     * comes from the event label（修改/取消）.
     */
    private Reservation requireOwnedEditable(Long id, AuthenticatedUser user, ReservationEvent event) {
        Reservation reservation = reservationMapper.findByIdForUpdate(id);
        if (reservation == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, HttpStatus.NOT_FOUND, "预约不存在");
        }
        if (!reservation.getUserId().equals(user.userId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, HttpStatus.FORBIDDEN, "只能" + event.label() + "本人预约");
        }
        // 状态合法性由唯一状态机裁决（非法即抛 RESERVATION_INVALID_STATE）。
        // RESCHEDULE 的合法性只看当前状态（PENDING/CONFIRMED）；目标状态在锁到新会议室后再按审批规则计算。
        ReservationStateMachine.transition(reservation.getStatus(), event, false);
        if (!LocalDateTime.now(clock).isBefore(reservation.getStartTime())) {
            throw new BusinessException(ErrorCode.RESERVATION_INVALID_STATE, "预约已开始，不能" + event.label());
        }
        return reservation;
    }

    /** expected-state 条件未命中（affectedRows != 1）时 fail-closed，绝不静默成功。 */
    private void requireAffectedRow(int affectedRows) {
        if (affectedRows != 1) {
            throw new BusinessException(ErrorCode.RESERVATION_INVALID_STATE, "预约状态已变化，操作未生效，请刷新后重试");
        }
    }

    private TimeInterval parseFutureInterval(LocalDateTime start, LocalDateTime end, LocalDateTime now) {
        TimeInterval interval = parseInterval(start, end);
        if (interval.start().isBefore(now)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, HttpStatus.BAD_REQUEST, "预约开始时间不能早于当前时间");
        }
        return interval;
    }

    private TimeInterval parseInterval(LocalDateTime start, LocalDateTime end) {
        TimeInterval interval;
        try {
            interval = new TimeInterval(start, end);
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, HttpStatus.BAD_REQUEST, "结束时间必须晚于开始时间");
        }
        // 允许结束时间落在次日（跨天），但必须仍在管理员设定的可预约窗口内；
        // 窗口按“预约日期 00:00 起算的分钟数”度量，超出部分由 validateSlot 拒绝。
        return interval;
    }

    /**
     * Shared rule chain for create and update; must run after the meeting_room row is
     * locked. {@code excludeReservationId} is null for create, otherwise the reservation
     * being rescheduled (its old row must not conflict with itself).
     */
    private void validateSlot(BookableRoomProfile room, TimeInterval interval, int participantCount,
                              LocalDateTime now, Long excludeReservationId) {
        requireRoomAvailable(room);
        long advanceDays = ChronoUnit.DAYS.between(now.toLocalDate(), interval.start().toLocalDate());
        if (advanceDays > room.advanceDays()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, HttpStatus.BAD_REQUEST, "超过会议室允许的提前预约天数");
        }
        validateWindowCapacityAndConflict(room, interval, participantCount, excludeReservationId);
    }

    /**
     * 周期性预约的第 2..N 次发生：同属一次预约意图，提前量约束只看首次开始；
     * 但会议室状态、时长、容量、开放窗口与冲突仍逐周校验，任一周不合法整批失败。
     */
    private void validateRecurringOccurrence(BookableRoomProfile room, TimeInterval interval, int participantCount) {
        requireRoomAvailable(room);
        validateWindowCapacityAndConflict(room, interval, participantCount, null);
    }

    private void requireRoomAvailable(BookableRoomProfile room) {
        if (!"AVAILABLE".equals(room.status())) {
            throw new BusinessException(ErrorCode.ROOM_UNAVAILABLE, "会议室当前不可预约");
        }
    }

    /** 时长、容量、开放窗口与时间冲突：创建与改期共用的完整校验顺序。 */
    private void validateWindowCapacityAndConflict(BookableRoomProfile room, TimeInterval interval,
                                                   int participantCount, Long excludeReservationId) {
        if (interval.start().until(interval.end(), ChronoUnit.MINUTES) > room.maxDurationMinutes()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, HttpStatus.BAD_REQUEST, "超过会议室允许的最长预约时长");
        }
        if (participantCount > room.capacity()) {
            throw new BusinessException(ErrorCode.ROOM_CAPACITY_EXCEEDED, "参与人数超过会议室容量");
        }
        // 全局可预约时段（管理员设置）：以预约开始日期 00:00 为原点度量，结束可跨到次日。
        BookingWindowResponse window = bookingWindowService.get();
        LocalDateTime dayStart = interval.start().toLocalDate().atStartOfDay();
        long startOffset = ChronoUnit.MINUTES.between(dayStart, interval.start());
        long endOffset = ChronoUnit.MINUTES.between(dayStart, interval.end());
        if (startOffset < window.startMinute() || endOffset > window.endMinute()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, HttpStatus.BAD_REQUEST,
                    "预约时间不在开放的可预约时段内（" + window.startLabel() + " 至 " + window.endLabel() + "）");
        }
        // 冲突必须说明冲突会议主题与占用时段，因此查出第一条冲突单而不是只计数。
        Reservation conflict = reservationMapper.findFirstConflict(room.roomId(), interval.start(),
                interval.end(), excludeReservationId);
        if (conflict != null) {
            throw new BusinessException(ErrorCode.RESERVATION_TIME_CONFLICT,
                    "预约时间冲突：与已有预约「" + conflict.getTitle() + "」时间重叠（对方占用 "
                            + CONFLICT_SLOT_FORMATTER.format(conflict.getStartTime()) + " 至 "
                            + CONFLICT_SLOT_FORMATTER.format(conflict.getEndTime()) + "）");
        }
    }

    /** 周期性会议（按周重复）周数：缺省 1 次；上限 {@value MAX_REPEAT_WEEKS} 周，防止一次提交铺满整学期。 */
    private static final int MAX_REPEAT_WEEKS = 8;

    private int normalizeRepeatWeeks(Integer repeatWeeks) {
        if (repeatWeeks == null) {
            return 1;
        }
        if (repeatWeeks < 1 || repeatWeeks > MAX_REPEAT_WEEKS) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, HttpStatus.BAD_REQUEST,
                    "周期性会议最多支持连续 " + MAX_REPEAT_WEEKS + " 周");
        }
        return repeatWeeks;
    }
}
