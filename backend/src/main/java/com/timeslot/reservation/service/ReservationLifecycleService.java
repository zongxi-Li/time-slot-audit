/**
 * 文件职责：集中处理预约审批、拒绝和强制取消等生命周期变化。
 * 接口：被 administration 通过生命周期端口调用。
 */
package com.timeslot.reservation.service;

import com.timeslot.common.api.ErrorCode;
import com.timeslot.common.exception.BusinessException;
import com.timeslot.reservation.domain.Reservation;
import com.timeslot.reservation.domain.ReservationStatus;
import com.timeslot.reservation.dto.ReservationResponse;
import com.timeslot.reservation.mapper.ReservationMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Set;

/**
 * The only write entry for reservation status transitions driven by other domains.
 * administration (approve/reject/force-cancel) and meeting (check-in flows) must go
 * through this service; calling {@link ReservationMapper#updateStatus} directly from
 * another domain is a boundary violation. Audit records (approval_record /
 * operation_log) stay owned by the calling domain and are written around these calls.
 */
@Service
public class ReservationLifecycleService {
    private static final int MAX_REASON_LENGTH = 500;
    private static final Set<ReservationStatus> FORCE_CANCELABLE = Set.of(ReservationStatus.PENDING, ReservationStatus.CONFIRMED);

    private final ReservationMapper reservationMapper;
    private final Clock clock;

    public ReservationLifecycleService(ReservationMapper reservationMapper, Clock clock) {
        this.reservationMapper = reservationMapper;
        this.clock = clock;
    }

    /** PENDING -> CONFIRMED. */
    @Transactional
    public ReservationResponse approve(Long reservationId, Long operatorId) {
        Reservation reservation = lockForTransition(reservationId);
        requireStatus(reservation, ReservationStatus.PENDING, "仅待审批的预约可以通过审批");
        return transition(reservation, ReservationStatus.CONFIRMED, null);
    }

    /** PENDING -> REJECTED. The contract requires a non-blank reject remark. */
    @Transactional
    public ReservationResponse reject(Long reservationId, Long operatorId, String reason) {
        requireReason(reason, "驳回必须填写原因");
        Reservation reservation = lockForTransition(reservationId);
        requireStatus(reservation, ReservationStatus.PENDING, "仅待审批的预约可以驳回");
        return transition(reservation, ReservationStatus.REJECTED, reason);
    }

    /**
     * PENDING/CONFIRMED -> CANCELLED by governance, allowed even after the meeting
     * started (unlike the owner cancel endpoint), always with a mandatory reason.
     */
    @Transactional
    public ReservationResponse forceCancel(Long reservationId, Long operatorId, String reason) {
        requireReason(reason, "强制取消必须填写原因");
        Reservation reservation = lockForTransition(reservationId);
        if (!FORCE_CANCELABLE.contains(reservation.getStatus())) {
            throw new BusinessException(ErrorCode.RESERVATION_INVALID_STATE, "仅待审批或已确认的预约可以强制取消");
        }
        return transition(reservation, ReservationStatus.CANCELLED, reason);
    }

    private Reservation lockForTransition(Long reservationId) {
        Reservation reservation = reservationMapper.findByIdForUpdate(reservationId);
        if (reservation == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, HttpStatus.NOT_FOUND, "预约不存在");
        }
        return reservation;
    }

    private void requireStatus(Reservation reservation, ReservationStatus required, String message) {
        if (reservation.getStatus() != required) {
            throw new BusinessException(ErrorCode.RESERVATION_INVALID_STATE, message);
        }
    }

    private void requireReason(String reason, String message) {
        if (reason == null || reason.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, HttpStatus.BAD_REQUEST, message);
        }
        if (reason.length() > MAX_REASON_LENGTH) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, HttpStatus.BAD_REQUEST, "原因长度不能超过" + MAX_REASON_LENGTH + "字");
        }
    }

    private ReservationResponse transition(Reservation reservation, ReservationStatus target, String reason) {
        reservationMapper.updateStatus(reservation.getId(), target.name(), reason);
        reservation.setStatus(target);
        return ReservationResponse.from(reservation, clock);
    }
}
