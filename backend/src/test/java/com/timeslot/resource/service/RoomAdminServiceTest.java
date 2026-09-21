/**
 * 文件职责：验证 RoomAdminService 删除会议室的保护规则与级联清理顺序。
 * 接口：使用 JUnit、Mockito 或 Spring 测试工具，不属于运行时接口。
 */
package com.timeslot.resource.service;

import com.timeslot.common.api.ErrorCode;
import com.timeslot.common.exception.BusinessException;
import com.timeslot.resource.mapper.ResourceMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

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

    /** 业务时钟固定在 2026-09-21 10:00（Asia/Shanghai）。 */
    private final Clock clock = Clock.fixed(Instant.parse("2026-09-21T02:00:00Z"), ZoneId.of("Asia/Shanghai"));
    private RoomAdminService service;

    @BeforeEach
    void setUp() {
        service = new RoomAdminService(mapper, clock);
        ResourceMapper.RoomRow room = new ResourceMapper.RoomRow();
        room.setId(9L);
        room.setRoomName("C909");
        when(mapper.findRoomForUpdate(9L)).thenReturn(room);
    }

    @Test
    void deleteRoomBlockedByFutureReservation() {
        when(mapper.countFutureReservations(9L, LocalDateTime.of(2026, 9, 21, 10, 0))).thenReturn(2);

        BusinessException exception = assertThrows(BusinessException.class, () -> service.deleteRoom(9L));

        assertEquals(ErrorCode.ROOM_DELETE_BLOCKED, exception.getCode());
        assertTrue(exception.getMessage().contains("未来预约"));
        verify(mapper, never()).deleteRoom(anyLong());
    }

    @Test
    void deleteRoomBlockedByHistoricalReservationOnly() {
        when(mapper.countFutureReservations(anyLong(), any())).thenReturn(0);
        when(mapper.countAllReservations(9L)).thenReturn(3);

        BusinessException exception = assertThrows(BusinessException.class, () -> service.deleteRoom(9L));

        assertEquals(ErrorCode.ROOM_DELETE_BLOCKED, exception.getCode());
        assertTrue(exception.getMessage().contains("历史预约"));
        verify(mapper, never()).deleteRoom(anyLong());
    }

    @Test
    void deleteRemovesChildrenThenRoomWhenNoReservations() {
        when(mapper.countFutureReservations(anyLong(), any())).thenReturn(0);
        when(mapper.countAllReservations(9L)).thenReturn(0);

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
