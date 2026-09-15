package com.timeslot.administration.service;

import com.timeslot.administration.domain.ApprovalAction;
import com.timeslot.administration.dto.AdminReservationResponse;
import com.timeslot.administration.dto.AdminReservationRow;
import com.timeslot.administration.dto.AuditLogResponse;
import com.timeslot.administration.dto.OperationsDashboardResponse;
import com.timeslot.administration.spi.ReservationLifecyclePort;
import com.timeslot.administration.mapper.AdministrationMapper;
import com.timeslot.common.api.ErrorCode;
import com.timeslot.common.exception.BusinessException;
import com.timeslot.common.security.AuthenticatedUser;
import com.timeslot.common.security.CurrentUserProvider;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@Service
public class AdministrationService {
    private static final Set<String> RESERVATION_STATUSES =
            Set.of("PENDING", "CONFIRMED", "REJECTED", "CANCELLED");
    private static final int MAX_AUDIT_LIMIT = 5000;

    private final AdministrationMapper mapper;
    private final CurrentUserProvider currentUserProvider;
    private final ObjectProvider<ReservationLifecyclePort> lifecycleProvider;

    public AdministrationService(AdministrationMapper mapper,
                                 CurrentUserProvider currentUserProvider,
                                 ObjectProvider<ReservationLifecyclePort> lifecycleProvider) {
        this.mapper = mapper;
        this.currentUserProvider = currentUserProvider;
        this.lifecycleProvider = lifecycleProvider;
    }

    public List<AdminReservationResponse> reservations(String status) {
        String normalizedStatus = normalizeOptional(status);
        if (normalizedStatus != null && !RESERVATION_STATUSES.contains(normalizedStatus)) {
            throw validation("预约状态无效");
        }
        return mapper.findReservations(normalizedStatus).stream()
                .map(AdminReservationRow::toResponse)
                .toList();
    }

    public AdminReservationResponse reservation(Long id) {
        AdminReservationResponse reservation = requireReservation(id);
        return reservation.withHistory(mapper.findApprovalHistory(id));
    }

    @Transactional
    public AdminReservationResponse approve(Long id, String ipAddress) {
        AdminReservationResponse reservation = requirePending(id);
        AuthenticatedUser operator = currentUserProvider.getRequired();
        lifecycle().approve(id, operator.userId());
        mapper.insertApprovalRecord(id, operator.userId(), ApprovalAction.APPROVE.name(), null);
        mapper.insertOperationLog(operator.userId(), "APPROVE_RESERVATION", "RESERVATION", id,
                "审批通过预约 " + reservation.reservationNo() + "（" + reservation.title() + "）", ipAddress);
        return reservation(id);
    }

    @Transactional
    public AdminReservationResponse reject(Long id, String reason, String ipAddress) {
        AdminReservationResponse reservation = requirePending(id);
        AuthenticatedUser operator = currentUserProvider.getRequired();
        String normalizedReason = requireReason(reason, "驳回原因不能为空");
        lifecycle().reject(id, operator.userId(), normalizedReason);
        mapper.insertApprovalRecord(id, operator.userId(), ApprovalAction.REJECT.name(), normalizedReason);
        mapper.insertOperationLog(operator.userId(), "REJECT_RESERVATION", "RESERVATION", id,
                "驳回预约 " + reservation.reservationNo() + "（" + reservation.title() + "），原因：" + normalizedReason,
                ipAddress);
        return reservation(id);
    }

    @Transactional
    public AdminReservationResponse forceCancel(Long id, String reason, String ipAddress) {
        AdminReservationResponse reservation = requireReservation(id);
        AuthenticatedUser operator = currentUserProvider.getRequired();
        String normalizedReason = requireReason(reason, "强制取消原因不能为空");
        lifecycle().forceCancel(id, operator.userId(), normalizedReason);
        mapper.insertOperationLog(operator.userId(), "FORCE_CANCEL_RESERVATION", "RESERVATION", id,
                "强制取消预约 " + reservation.reservationNo() + "（" + reservation.title() + "），原因：" + normalizedReason,
                ipAddress);
        return reservation(id);
    }

    public List<AuditLogResponse> auditLogs(Long operatorId, String businessType,
                                            LocalDateTime start, LocalDateTime end, Integer limit) {
        validateRange(start, end);
        int safeLimit = limit == null ? 200 : Math.max(1, Math.min(limit, MAX_AUDIT_LIMIT));
        return mapper.findAuditLogs(operatorId, normalizeOptional(businessType), start, end, safeLimit);
    }

    public OperationsDashboardResponse dashboard(LocalDateTime start, LocalDateTime end, Integer top) {
        LocalDateTime safeEnd = end == null ? LocalDateTime.now() : end;
        LocalDateTime safeStart = start == null ? safeEnd.minusDays(30) : start;
        validateRange(safeStart, safeEnd);
        int safeTop = top == null ? 5 : Math.max(1, Math.min(top, 20));
        long total = mapper.countReservations(safeStart, safeEnd);
        long cancelled = mapper.countCancelledReservations(safeStart, safeEnd);
        BigDecimal rate = total == 0 ? BigDecimal.ZERO : BigDecimal.valueOf(cancelled)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP);
        return new OperationsDashboardResponse(safeStart, safeEnd, total, cancelled, rate,
                mapper.findPopularRooms(safeStart, safeEnd, safeTop), mapper.findPeakHours(safeStart, safeEnd));
    }

    public byte[] exportAuditLogs(Long operatorId, String businessType,
                                  LocalDateTime start, LocalDateTime end) {
        StringBuilder csv = new StringBuilder("\uFEFFID,操作人ID,操作人,操作类型,业务类型,业务ID,内容,IP地址,操作时间\r\n");
        for (AuditLogResponse log : auditLogs(operatorId, businessType, start, end, MAX_AUDIT_LIMIT)) {
            csv.append(log.id()).append(',')
                    .append(log.userId()).append(',')
                    .append(csvCell(log.operatorName())).append(',')
                    .append(csvCell(log.operationType())).append(',')
                    .append(csvCell(log.businessType())).append(',')
                    .append(log.businessId()).append(',')
                    .append(csvCell(log.content())).append(',')
                    .append(csvCell(log.ipAddress())).append(',')
                    .append(csvCell(log.createdAt() == null ? null : log.createdAt().toString()))
                    .append("\r\n");
        }
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    private AdminReservationResponse requireReservation(Long id) {
        AdminReservationRow row = mapper.findReservationById(id);
        if (row == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, HttpStatus.NOT_FOUND, "预约不存在");
        }
        return row.toResponse();
    }

    private AdminReservationResponse requirePending(Long id) {
        AdminReservationResponse reservation = requireReservation(id);
        if (!"PENDING".equals(reservation.status())) {
            throw new BusinessException(ErrorCode.RESERVATION_INVALID_STATE, "仅待审批预约可执行该操作");
        }
        return reservation;
    }

    private ReservationLifecyclePort lifecycle() {
        ReservationLifecyclePort lifecycle = lifecycleProvider.getIfAvailable();
        if (lifecycle == null) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, HttpStatus.SERVICE_UNAVAILABLE,
                    "预约生命周期服务尚未接入，请先合并 reservation 域公开服务");
        }
        return lifecycle;
    }

    private String requireReason(String reason, String message) {
        if (reason == null || reason.isBlank()) throw validation(message);
        String normalized = reason.trim();
        if (normalized.length() > 500) throw validation("原因不能超过500字");
        return normalized;
    }

    private void validateRange(LocalDateTime start, LocalDateTime end) {
        if (start != null && end != null && !start.isBefore(end)) {
            throw validation("开始时间必须早于结束时间");
        }
    }

    private String normalizeOptional(String value) {
        return value == null || value.isBlank() ? null : value.trim().toUpperCase();
    }

    private BusinessException validation(String message) {
        return new BusinessException(ErrorCode.VALIDATION_ERROR, HttpStatus.BAD_REQUEST, message);
    }

    private String csvCell(String value) {
        if (value == null) return "";
        return '"' + value.replace("\"", "\"\"").replace("\r", " ").replace("\n", " ") + '"';
    }
}
