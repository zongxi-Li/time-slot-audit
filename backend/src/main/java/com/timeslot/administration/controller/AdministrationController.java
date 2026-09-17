/**
 * 文件职责：提供 管理员运营 HTTP 接口，将请求交给 Service 处理并返回统一响应。
 * 接口：GET /api/admin/reservations；
 *        GET /api/admin/reservations/{id}；
 *        POST /api/admin/reservations/{id}/approve；
 *        POST /api/admin/reservations/{id}/reject；
 *        POST /api/admin/reservations/{id}/force-cancel；
 *        …。
 */
package com.timeslot.administration.controller;

import com.timeslot.administration.dto.AdminReservationResponse;
import com.timeslot.administration.dto.AuditLogResponse;
import com.timeslot.administration.dto.ForceCancelRequest;
import com.timeslot.administration.dto.OperationsDashboardResponse;
import com.timeslot.administration.dto.RejectReservationRequest;
import com.timeslot.administration.service.AdministrationService;
import com.timeslot.common.api.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdministrationController {
    private final AdministrationService service;

    public AdministrationController(AdministrationService service) {
        this.service = service;
    }

    @GetMapping("/reservations")
    public ApiResponse<List<AdminReservationResponse>> reservations(
            @RequestParam(required = false) String status) {
        return ApiResponse.success(service.reservations(status));
    }

    @GetMapping("/reservations/{id}")
    public ApiResponse<AdminReservationResponse> reservation(@PathVariable Long id) {
        return ApiResponse.success(service.reservation(id));
    }

    @PostMapping("/reservations/{id}/approve")
    public ApiResponse<AdminReservationResponse> approve(@PathVariable Long id, HttpServletRequest request) {
        return ApiResponse.success(service.approve(id, clientIp(request)));
    }

    @PostMapping("/reservations/{id}/reject")
    public ApiResponse<AdminReservationResponse> reject(@PathVariable Long id,
                                                        @Valid @RequestBody RejectReservationRequest body,
                                                        HttpServletRequest request) {
        return ApiResponse.success(service.reject(id, body.reason(), clientIp(request)));
    }

    @PostMapping("/reservations/{id}/force-cancel")
    public ApiResponse<AdminReservationResponse> forceCancel(@PathVariable Long id,
                                                             @Valid @RequestBody ForceCancelRequest body,
                                                             HttpServletRequest request) {
        return ApiResponse.success(service.forceCancel(id, body.reason(), clientIp(request)));
    }

    @GetMapping("/audit-logs")
    public ApiResponse<List<AuditLogResponse>> auditLogs(
            @RequestParam(required = false) Long operatorId,
            @RequestParam(required = false) String businessType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end,
            @RequestParam(required = false) Integer limit) {
        return ApiResponse.success(service.auditLogs(operatorId, businessType, start, end, limit));
    }

    @GetMapping("/statistics")
    public ApiResponse<OperationsDashboardResponse> statistics(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end,
            @RequestParam(required = false) Integer top) {
        return ApiResponse.success(service.dashboard(start, end, top));
    }

    @GetMapping(value = "/audit-logs/export", produces = "text/csv")
    public ResponseEntity<byte[]> exportAuditLogs(
            @RequestParam(required = false) Long operatorId,
            @RequestParam(required = false) String businessType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end) {
        byte[] csv = service.exportAuditLogs(operatorId, businessType, start, end);
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename("audit-logs.csv", StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(csv);
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) return forwarded.split(",", 2)[0].trim();
        return request.getRemoteAddr();
    }
}
