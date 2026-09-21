/**
 * 文件职责：验证 AdministrationServiceTest 相关业务、接口安全或边界条件。
 * 接口：使用 JUnit、Mockito 或 Spring 测试工具，不属于运行时接口。
 */
package com.timeslot.administration.service;

import com.timeslot.administration.dto.AdminReservationResponse;
import com.timeslot.administration.dto.AdminReservationRow;
import com.timeslot.administration.mapper.AdministrationMapper;
import com.timeslot.administration.spi.ReservationLifecyclePort;
import com.timeslot.common.api.ErrorCode;
import com.timeslot.common.bookingwindow.BookingWindowResponse;
import com.timeslot.common.bookingwindow.BookingWindowService;
import com.timeslot.reservation.spi.ReservationNotificationPort;
import com.timeslot.common.exception.BusinessException;
import com.timeslot.common.security.AuthenticatedUser;
import com.timeslot.common.security.CurrentUserProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;
import org.springframework.beans.factory.ObjectProvider;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AdministrationServiceTest {
    @Mock AdministrationMapper mapper;
    @Mock CurrentUserProvider currentUserProvider;
    @Mock ObjectProvider<ReservationLifecyclePort> lifecycleProvider;
    @Mock ReservationLifecyclePort lifecycle;
    @Mock ReservationNotificationPort notificationPort;
    @Mock BookingWindowService bookingWindowService;

    private AdministrationService service;
    private AdminReservationRow pending;

    @BeforeEach
    void setUp() {
        service = new AdministrationService(mapper, currentUserProvider, lifecycleProvider, notificationPort,
                bookingWindowService, Clock.systemDefaultZone());
        when(bookingWindowService.get()).thenReturn(new BookingWindowResponse(480, 1920));
        pending = reservation("PENDING");
        when(currentUserProvider.getRequired()).thenReturn(new AuthenticatedUser(1L, "admin", "ADMIN"));
        when(lifecycleProvider.getIfAvailable()).thenReturn(lifecycle);
        when(mapper.findApprovalHistory(42L)).thenReturn(List.of());
    }

    @Test
    void approveDelegatesToReservationDomainAndWritesBothAuditRecords() {
        when(mapper.findReservationById(42L)).thenReturn(pending, reservation("CONFIRMED"));

        AdminReservationResponse result = service.approve(42L, "127.0.0.1");

        assertEquals("CONFIRMED", result.status());
        verify(lifecycle).approve(42L, 1L);
        verify(mapper).insertApprovalRecord(42L, 1L, "APPROVE", null);
        verify(mapper).insertOperationLog(1L, "APPROVE_RESERVATION", "RESERVATION", 42L,
                "审批通过预约 RSV42（评审会）", "127.0.0.1");
    }

    @Test
    void rejectRequiresReasonAndWritesReasonToBothRecords() {
        when(mapper.findReservationById(42L)).thenReturn(pending, reservation("REJECTED"));

        service.reject(42L, "  场地维护  ", "10.0.0.1");

        verify(lifecycle).reject(42L, 1L, "场地维护");
        verify(mapper).insertApprovalRecord(42L, 1L, "REJECT", "场地维护");
        verify(mapper).insertOperationLog(1L, "REJECT_RESERVATION", "RESERVATION", 42L,
                "驳回预约 RSV42（评审会），原因：场地维护", "10.0.0.1");
        verify(notificationPort).notifyRejected(2L, 42L, "评审会", "场地维护");
    }

    @Test
    void invalidStateFromLifecycleRollsBackWithoutWritingAuditFacts() {
        when(mapper.findReservationById(42L)).thenReturn(reservation("CONFIRMED"));
        doThrow(new BusinessException(ErrorCode.RESERVATION_INVALID_STATE, "当前预约状态不可审批（CONFIRMED）"))
                .when(lifecycle).approve(42L, 1L);

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.approve(42L, "127.0.0.1"));

        assertEquals(ErrorCode.RESERVATION_INVALID_STATE, error.getCode());
        // 状态合法性由 reservation 域状态机裁决；其失败必须阻止审批记录与操作日志落库。
        verify(lifecycle).approve(42L, 1L);
        verify(mapper, never()).insertApprovalRecord(42L, 1L, "APPROVE", null);
        verify(mapper, never()).insertOperationLog(anyLong(), anyString(), anyString(), anyLong(), anyString(), anyString());
    }

    @Test
    void missingLifecycleServiceFailsWithoutWritingAuditFacts() {
        when(mapper.findReservationById(42L)).thenReturn(pending);
        when(lifecycleProvider.getIfAvailable()).thenReturn(null);

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.approve(42L, "127.0.0.1"));

        assertEquals(503, error.getStatus().value());
        verify(mapper, never()).insertApprovalRecord(42L, 1L, "APPROVE", null);
    }

    @Test
    void auditQueryNormalizesBusinessTypeAndBoundsLimit() {
        service.auditLogs(1L, " reservation ", null, null, 99999);

        verify(mapper).findAuditLogs(1L, "RESERVATION", null, null, 5000);
    }

    /** 任务书点名三项聚合：使用率、日均会议时长、爽约率的换算口径。 */
    @Test
    void dashboardComputesUtilizationAvgDailyHoursAndNoShowRate() {
        LocalDateTime start = LocalDateTime.of(2026, 9, 1, 0, 0);
        LocalDateTime end = LocalDateTime.of(2026, 9, 21, 0, 0);
        when(mapper.countReservations(start, end)).thenReturn(10L);
        when(mapper.countCancelledReservations(start, end)).thenReturn(2L);
        AdministrationMapper.RoomUsageStatRow used = new AdministrationMapper.RoomUsageStatRow();
        used.setRoomId(1L);
        used.setRoomName("A301");
        used.setConfirmedCount(5L);
        used.setUsedHours(new java.math.BigDecimal("14.00"));
        AdministrationMapper.RoomUsageStatRow idle = new AdministrationMapper.RoomUsageStatRow();
        idle.setRoomId(2L);
        idle.setRoomName("A302");
        idle.setConfirmedCount(0L);
        idle.setUsedHours(java.math.BigDecimal.ZERO);
        when(mapper.findRoomUsageStats(start, end)).thenReturn(List.of(used, idle));
        when(mapper.countAttendees(start, end)).thenReturn(40L);
        when(mapper.countNoShowAttendees(start, end)).thenReturn(10L);

        var dashboard = service.dashboard(start, end, 5);

        // 20 天 × 每日开放 24h（窗口 480..1920 即 1440 分钟）= 480h 开放时长：A301 使用率 2.92%、日均 0.70h
        assertEquals(20L, dashboard.statDays());
        assertEquals(2, dashboard.roomUtilizations().size());
        assertEquals(new java.math.BigDecimal("2.92"), dashboard.roomUtilizations().get(0).utilizationRate());
        assertEquals(new java.math.BigDecimal("0.70"), dashboard.roomUtilizations().get(0).avgDailyHours());
        assertEquals(new java.math.BigDecimal("0.00"), dashboard.roomUtilizations().get(1).utilizationRate());
        assertEquals(new java.math.BigDecimal("0.70"), dashboard.avgDailyMeetingHours());
        assertEquals(new java.math.BigDecimal("25.00"), dashboard.noShowRate());
    }

    private AdminReservationRow reservation(String status) {
        return new AdminReservationRow(42L, "RSV42", 5L, "B502", 2L, "张三", "评审会",
                LocalDateTime.of(2026, 9, 20, 10, 0), LocalDateTime.of(2026, 9, 20, 11, 0),
                20, status, null, LocalDateTime.of(2026, 9, 15, 9, 0));
    }
}
