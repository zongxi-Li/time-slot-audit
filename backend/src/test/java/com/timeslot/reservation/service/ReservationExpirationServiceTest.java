/**
 * 文件职责：验证 ReservationExpirationService 清扫编排的业务与边界条件。
 * 接口：使用 JUnit、Mockito 或 Spring 测试工具，不属于运行时接口。
 */
package com.timeslot.reservation.service;

import com.timeslot.reservation.domain.Reservation;
import com.timeslot.reservation.domain.ReservationStatus;
import com.timeslot.reservation.spi.ReservationNotificationPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ReservationExpirationServiceTest {
    @Mock ReservationQueryService reservationQueryService;
    @Mock ReservationLifecycleService lifecycleService;
    @Mock ReservationNotificationPort notificationPort;

    private ReservationExpirationService service;
    private final LocalDateTime now = LocalDateTime.of(2026, 9, 21, 15, 25);

    @BeforeEach
    void setUp() {
        service = new ReservationExpirationService(reservationQueryService, lifecycleService, notificationPort);
    }

    private Reservation pendingEnded(long id) {
        Reservation reservation = new Reservation();
        reservation.setId(id);
        reservation.setUserId(3L);
        reservation.setTitle("全院月度总结会");
        reservation.setStatus(ReservationStatus.PENDING);
        return reservation;
    }

    @Test
    void sweepsExpiredPendingAndNotifiesOwnerWithSystemReason() {
        Reservation first = pendingEnded(1L);
        Reservation second = pendingEnded(2L);
        when(reservationQueryService.findPendingEndedBefore(now)).thenReturn(List.of(first, second));
        when(lifecycleService.expireIfEnded(1L, now)).thenReturn(true);
        when(lifecycleService.expireIfEnded(2L, now)).thenReturn(true);

        service.processDueExpirations(now);

        verify(notificationPort).notifyRejected(3L, 1L, "全院月度总结会",
                ReservationExpirationService.EXPIRE_REASON);
        verify(notificationPort).notifyRejected(3L, 2L, "全院月度总结会",
                ReservationExpirationService.EXPIRE_REASON);
    }

    @Test
    void guardMissSkipsNotification() {
        // 并发下已被人工处理：迁移未发生（false），绝不能给用户发系统驳回通知
        Reservation candidate = pendingEnded(1L);
        when(reservationQueryService.findPendingEndedBefore(now)).thenReturn(List.of(candidate));
        when(lifecycleService.expireIfEnded(1L, now)).thenReturn(false);

        service.processDueExpirations(now);

        verify(notificationPort, never()).notifyRejected(anyLong(), anyLong(), eq("全院月度总结会"), eq(
                ReservationExpirationService.EXPIRE_REASON));
    }

    @Test
    void singleRowFailureDoesNotBlockRemainingRows() {
        Reservation first = pendingEnded(1L);
        Reservation second = pendingEnded(2L);
        when(reservationQueryService.findPendingEndedBefore(now)).thenReturn(List.of(first, second));
        doThrow(new RuntimeException("db glitch")).when(lifecycleService).expireIfEnded(1L, now);
        when(lifecycleService.expireIfEnded(2L, now)).thenReturn(true);

        service.processDueExpirations(now);

        verify(notificationPort).notifyRejected(3L, 2L, "全院月度总结会",
                ReservationExpirationService.EXPIRE_REASON);
    }
}
