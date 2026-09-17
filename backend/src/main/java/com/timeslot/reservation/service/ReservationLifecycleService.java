/**
 * 文件职责：集中处理预约审批、拒绝和强制取消等生命周期变化。
 * 接口：被 administration 通过生命周期端口调用。
 */
package com.timeslot.reservation.service;

import com.timeslot.common.api.ErrorCode;
import com.timeslot.common.exception.BusinessException;
import com.timeslot.reservation.domain.Reservation;
import com.timeslot.reservation.domain.ReservationEvent;
import com.timeslot.reservation.domain.ReservationStatus;
import com.timeslot.reservation.domain.ReservationStateMachine;
import com.timeslot.reservation.dto.ReservationResponse;
import com.timeslot.reservation.mapper.ReservationMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

/**
 * The only write entry for reservation status transitions driven by other domains.
 * administration (approve/reject/force-cancel) and meeting (check-in flows) must go
 * through this service; calling the reservation mappers' status updates directly from
 * another domain is a boundary violation. Audit records (approval_record /
 * operation_log) stay owned by the calling domain and are written around these calls.
 *
 * {@code operatorId} is intentionally unused here: approver identity belongs to the
 * administration domain (approval_record / operation_log) and must not leak into
 * reservation-owned tables. It is kept in the public signature for port compatibility.
 */
@Service
public class ReservationLifecycleService {
    private static final int MAX_REASON_LENGTH = 500;

    private final ReservationMapper reservationMapper;
    private final Clock clock;

    public ReservationLifecycleService(ReservationMapper reservationMapper, Clock clock) {
        this.reservationMapper = reservationMapper;
        this.clock = clock;
    }

    /** PENDING -> CONFIRMED. Illegal states are rejected by the shared state machine. */
    @Transactional
    public ReservationResponse approve(Long reservationId, Long operatorId) {
        Reservation reservation = lockForTransition(reservationId);
        ReservationStatus target = ReservationStateMachine.transition(reservation.getStatus(),
                ReservationEvent.APPROVE);
        return transitionStatus(reservation, target);
    }

    /** PENDING -> REJECTED. The contract requires a non-blank reject remark. */
    @Transactional
    public ReservationResponse reject(Long reservationId, Long operatorId, String reason) {
        requireReason(reason, "驳回必须填写原因");
        Reservation reservation = lockForTransition(reservationId);
        ReservationStatus target = ReservationStateMachine.transition(reservation.getStatus(),
                ReservationEvent.REJECT);
        return transitionStatus(reservation, target);
    }

    /**
     * PENDING/CONFIRMED -> CANCELLED by governance, allowed even after the meeting
     * started (unlike the owner cancel endpoint), always with a mandatory reason.
     */
    @Transactional
    public ReservationResponse forceCancel(Long reservationId, Long operatorId, String reason) {
        requireReason(reason, "强制取消必须填写原因");
        Reservation reservation = lockForTransition(reservationId);
        ReservationStatus target = ReservationStateMachine.transition(reservation.getStatus(),
                ReservationEvent.FORCE_CANCEL);
        return cancel(reservation, target, reason);
    }

    private Reservation lockForTransition(Long reservationId) {
        Reservation reservation = reservationMapper.findByIdForUpdate(reservationId);
        if (reservation == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, HttpStatus.NOT_FOUND, "预约不存在");
        }
        return reservation;
    }

    private void requireReason(String reason, String message) {
        if (reason == null || reason.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, HttpStatus.BAD_REQUEST, message);
        }
        if (reason.length() > MAX_REASON_LENGTH) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, HttpStatus.BAD_REQUEST, "原因长度不能超过" + MAX_REASON_LENGTH + "字");
        }
    }

    /**
     * APPROVE / REJECT：只更新 status，驳回理由由 administration 写入 approval_record.remark，
     * 因此这里绝不触碰 cancel_reason，避免字段语义污染。
     */
    private ReservationResponse transitionStatus(Reservation reservation, ReservationStatus target) {
        int affectedRows = reservationMapper.transitionStatusExpected(reservation.getId(),
                reservation.getStatus().name(), target.name());
        requireAffectedRow(affectedRows);
        reservation.setStatus(target);
        return ReservationResponse.from(reservation, clock);
    }

    /** OWNER_CANCEL / FORCE_CANCEL 语义的取消：status 与 cancel_reason 原子写入。 */
    private ReservationResponse cancel(Reservation reservation, ReservationStatus target, String reason) {
        int affectedRows = reservationMapper.cancelExpected(reservation.getId(),
                reservation.getStatus().name(), target.name(), reason);
        requireAffectedRow(affectedRows);
        reservation.setStatus(target);
        return ReservationResponse.from(reservation, clock);
    }

    /** expected-state 条件未命中（affectedRows != 1）时 fail-closed，绝不静默成功。 */
    private void requireAffectedRow(int affectedRows) {
        if (affectedRows != 1) {
            throw new BusinessException(ErrorCode.RESERVATION_INVALID_STATE, "预约状态已变化，操作未生效，请刷新后重试");
        }
    }
}
