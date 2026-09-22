/**
 * 文件职责：验证 RoomAdminService 删除会议室的保护规则与级联清理顺序。
 * 接口：使用 JUnit、Mockito 或 Spring 测试工具，不属于运行时接口。
 */
package com.timeslot.resource.service;

import com.timeslot.common.api.ErrorCode;
import com.timeslot.common.exception.BusinessException;
import com.timeslot.resource.mapper.ResourceMapper;
import com.timeslot.resource.spi.RoomReservationGuardPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RoomAdminServiceTest {
    @Mock ResourceMapper mapper;
    @Mock RoomReservationGuardPort reservationGuardPort;

    /** 业务时钟固定在 2026-09-21 10:00（Asia/Shanghai）。 */
    private final Clock clock = Clock.fixed(Instant.parse("2026-09-21T02:00:00Z"), ZoneId.of("Asia/Shanghai"));
    private RoomAdminService service;

    @BeforeEach
    void setUp() {
        service = new RoomAdminService(mapper, reservationGuardPort, clock);
        ResourceMapper.RoomRow room = new ResourceMapper.RoomRow();
        room.setId(9L);
        room.setRoomName("C909");
        when(mapper.findRoomForUpdate(9L)).thenReturn(room);
    }

    @Test
    void deleteRoomBlockedByFutureReservation() {
        when(reservationGuardPort.hasFutureActiveReservation(9L, LocalDateTime.of(2026, 9, 21, 10, 0))).thenReturn(true);

        BusinessException exception = assertThrows(BusinessException.class, () -> service.deleteRoom(9L));

        assertEquals(ErrorCode.ROOM_DELETE_BLOCKED, exception.getCode());
        // 业务规则阻止必须返回 409 业务错误，不能落到 500 兜底
        assertEquals(HttpStatus.CONFLICT, exception.getStatus());
        assertTrue(exception.getMessage().contains("未来预约"));
        verify(reservationGuardPort).hasFutureActiveReservation(9L, LocalDateTime.of(2026, 9, 21, 10, 0));
        verify(reservationGuardPort, never()).hasAnyReservation(9L);
        verify(mapper, never()).deleteRoom(anyLong());
    }

    @Test
    void deleteRoomBlockedByHistoricalReservationOnly() {
        when(reservationGuardPort.hasFutureActiveReservation(anyLong(), any())).thenReturn(false);
        when(reservationGuardPort.hasAnyReservation(9L)).thenReturn(true);

        BusinessException exception = assertThrows(BusinessException.class, () -> service.deleteRoom(9L));

        assertEquals(ErrorCode.ROOM_DELETE_BLOCKED, exception.getCode());
        assertTrue(exception.getMessage().contains("历史预约"));
        verify(mapper, never()).deleteRoom(anyLong());
    }

    @Test
    void deleteRemovesChildrenThenRoomWhenNoReservations() {
        when(reservationGuardPort.hasFutureActiveReservation(anyLong(), any())).thenReturn(false);
        when(reservationGuardPort.hasAnyReservation(9L)).thenReturn(false);

        service.deleteRoom(9L);

        // 子表先清、主行最后删（外键顺序），整体同一事务。
        InOrder order = inOrder(mapper);
        order.verify(mapper).deleteRepairTicketsByRoom(9L);
        order.verify(mapper).deleteMaintenanceByRoom(9L);
        order.verify(mapper).deleteOpenRulesByRoom(9L);
        order.verify(mapper).deleteFacilitiesByRoom(9L);
        order.verify(mapper).deleteRoom(9L);
    }

    @Test
    void deleteMissingRoomIsNotFound() {
        when(mapper.findRoomForUpdate(404L)).thenReturn(null);

        assertEquals(ErrorCode.RESOURCE_NOT_FOUND,
                assertThrows(BusinessException.class, () -> service.deleteRoom(404L)).getCode());
        verify(mapper, never()).deleteRoom(anyLong());
    }
}
