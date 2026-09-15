package com.timeslot.reservation.service;

import com.timeslot.common.api.ErrorCode;
import com.timeslot.common.exception.BusinessException;
import com.timeslot.common.security.AuthenticatedUser;
import com.timeslot.common.security.CurrentUserProvider;
import com.timeslot.reservation.domain.Reservation;
import com.timeslot.reservation.domain.ReservationStatus;
import com.timeslot.reservation.domain.TimeInterval;
import com.timeslot.reservation.dto.CancelReservationRequest;
import com.timeslot.reservation.dto.CreateReservationRequest;
import com.timeslot.reservation.dto.ReservationResponse;
import com.timeslot.reservation.mapper.ReservationMapper;
import com.timeslot.resource.domain.MeetingRoom;
import com.timeslot.resource.domain.MeetingRoomStatus;
import com.timeslot.resource.domain.RoomCategory;
import com.timeslot.resource.domain.RoomOpenRule;
import com.timeslot.resource.service.ResourceQueryService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Service
public class ReservationService {
    private final ReservationMapper reservationMapper;
    private final ResourceQueryService resourceQueryService;
    private final CurrentUserProvider currentUserProvider;
    private final Clock clock;

    public ReservationService(ReservationMapper reservationMapper, ResourceQueryService resourceQueryService,
                              CurrentUserProvider currentUserProvider, Clock clock) {
        this.reservationMapper = reservationMapper;
        this.resourceQueryService = resourceQueryService;
        this.currentUserProvider = currentUserProvider;
        this.clock = clock;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED, noRollbackFor = DuplicateKeyException.class)
    public ReservationResponse createReservation(CreateReservationRequest request) {
        AuthenticatedUser user = currentUserProvider.getRequired();
        Reservation existing = reservationMapper.findByUserIdAndRequestId(user.userId(), request.requestId());
        if (existing != null) return ReservationResponse.from(existing, clock);

        TimeInterval interval;
        try {
            interval = new TimeInterval(request.startTime(), request.endTime());
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, HttpStatus.BAD_REQUEST, "结束时间必须晚于开始时间");
        }
        // The open-rule model is per weekday, so a reservation must stay inside one day.
        if (!interval.start().toLocalDate().equals(interval.end().toLocalDate())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, HttpStatus.BAD_REQUEST, "预约不允许跨日期，请在同一天内选择时段");
        }
        LocalDateTime now = LocalDateTime.now(clock);
        if (interval.start().isBefore(now)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, HttpStatus.BAD_REQUEST, "预约开始时间不能早于当前时间");
        }

        // Critical ordering: lock one meeting_room row before the conflict query.
        MeetingRoom room = resourceQueryService.lockRoom(request.roomId());
        // A concurrent retry with the same requestId may have waited on this room lock.
        // Re-check after the lock so it returns the committed result instead of a false conflict.
        Reservation existingAfterLock = reservationMapper.findByUserIdAndRequestId(user.userId(), request.requestId());
        if (existingAfterLock != null) return ReservationResponse.from(existingAfterLock, clock);
        if (room.status() != MeetingRoomStatus.AVAILABLE) {
            throw new BusinessException(ErrorCode.ROOM_UNAVAILABLE, "会议室当前不可预约");
        }
        RoomCategory category = resourceQueryService.getCategory(room.categoryId());
        long advanceDays = ChronoUnit.DAYS.between(now.toLocalDate(), interval.start().toLocalDate());
        if (advanceDays > category.advanceDays()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, HttpStatus.BAD_REQUEST, "超过会议室允许的提前预约天数");
        }
        if (interval.start().until(interval.end(), ChronoUnit.MINUTES) > category.maxDurationMinutes()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, HttpStatus.BAD_REQUEST, "超过会议室允许的最长预约时长");
        }
        if (request.participantCount() > room.capacity()) {
            throw new BusinessException(ErrorCode.ROOM_CAPACITY_EXCEEDED, "参与人数超过会议室容量");
        }
        RoomOpenRule openRule = resourceQueryService.getOpenRule(room.id(), interval.start().getDayOfWeek().getValue());
        if (openRule == null || !openRule.enabled()
                || interval.start().toLocalTime().isBefore(openRule.openTime())
                || interval.end().toLocalTime().isAfter(openRule.closeTime())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, HttpStatus.BAD_REQUEST, "预约时间不在会议室开放时间内");
        }
        if (reservationMapper.countConflicts(room.id(), interval.start(), interval.end()) > 0) {
            throw new BusinessException(ErrorCode.RESERVATION_TIME_CONFLICT, "该时段已被其他用户占用");
        }

        Reservation reservation = new Reservation();
        reservation.setRequestId(request.requestId());
        reservation.setReservationNo("RSV" + UUID.randomUUID().toString().replace("-", "").substring(0, 20));
        reservation.setRoomId(room.id());
        reservation.setUserId(user.userId());
        reservation.setRoomName(room.name());
        reservation.setUserName(user.username());
        reservation.setTitle(request.title());
        reservation.setStartTime(interval.start());
        reservation.setEndTime(interval.end());
        reservation.setParticipantCount(request.participantCount());
        reservation.setStatus(category.approvalRequired() ? ReservationStatus.PENDING : ReservationStatus.CONFIRMED);
        reservation.setRemark(request.remark());
        try {
            reservationMapper.insert(reservation);
        } catch (DuplicateKeyException duplicateKeyException) {
            Reservation duplicate = reservationMapper.findByUserIdAndRequestId(user.userId(), request.requestId());
            if (duplicate != null) return ReservationResponse.from(duplicate, clock);
            throw duplicateKeyException;
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
        Reservation reservation = reservationMapper.findByIdForUpdate(id);
        if (reservation == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, HttpStatus.NOT_FOUND, "预约不存在");
        }
        // ADMIN must use the governed force-cancel path from administration (reason + audit).
        if (!reservation.getUserId().equals(user.userId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, HttpStatus.FORBIDDEN, "只能取消本人预约");
        }
        if (reservation.getStatus() != ReservationStatus.PENDING && reservation.getStatus() != ReservationStatus.CONFIRMED) {
            throw new BusinessException(ErrorCode.RESERVATION_INVALID_STATE, "当前预约状态不可取消");
        }
        if (!LocalDateTime.now(clock).isBefore(reservation.getStartTime())) {
            throw new BusinessException(ErrorCode.RESERVATION_INVALID_STATE, "预约已经开始，不能取消");
        }
        reservationMapper.updateStatus(id, ReservationStatus.CANCELLED.name(), request == null ? null : request.reason());
        reservation.setStatus(ReservationStatus.CANCELLED);
        return ReservationResponse.from(reservation, clock);
    }
}
