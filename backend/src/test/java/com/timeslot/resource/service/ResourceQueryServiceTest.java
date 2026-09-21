/**
 * 文件职责：验证 ResourceQueryService 的条件筛选与时段空闲查询业务规则。
 * 接口：使用 JUnit、Mockito 或 Spring 测试工具，不属于运行时接口。
 */
package com.timeslot.resource.service;

import com.timeslot.common.exception.BusinessException;
import com.timeslot.resource.domain.MeetingRoom;
import com.timeslot.resource.mapper.ResourceMapper;
import com.timeslot.resource.mapper.RoomQueryMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ResourceQueryServiceTest {
    @Mock ResourceMapper resourceMapper;
    @Mock RoomQueryMapper roomQueryMapper;

    private ResourceQueryService service;

    private static final LocalDate DATE = LocalDate.of(2026, 9, 21);

    @BeforeEach
    void setUp() {
        service = new ResourceQueryService(resourceMapper, roomQueryMapper);
    }

    private ResourceMapper.RoomRow roomRow(long id, String name, String location, int capacity,
                                           int status, String facilitiesCsv) {
        ResourceMapper.RoomRow row = new ResourceMapper.RoomRow();
        row.setId(id);
        row.setCategoryId(1L);
        row.setRoomName(name);
        row.setLocation(location);
        row.setCapacity(capacity);
        row.setStatus(status);
        row.setCategoryName("研发");
        row.setFacilitiesCsv(facilitiesCsv);
        return row;
    }

    @Test
    void searchRoomsTrimsFiltersAndMapsFacilities() {
        when(roomQueryMapper.searchRooms("3F", 10, "投影", null))
                .thenReturn(List.of(roomRow(1L, "三维厅", "3F-301", 20, 1, "投影,白板")));

        List<MeetingRoom> result = service.searchRooms(" 3F ", 10, " 投影 ");

        assertEquals(1, result.size());
        assertEquals(List.of("投影", "白板"), result.get(0).facilities());
        assertEquals("三维厅", result.get(0).name());
    }

    @Test
    void searchRoomsTreatsBlankFiltersAsAbsent() {
        when(roomQueryMapper.searchRooms(isNull(), isNull(), isNull(), isNull())).thenReturn(List.of());

        service.searchRooms("   ", null, "");

        verify(roomQueryMapper).searchRooms(isNull(), isNull(), isNull(), isNull());
    }

    @Test
    void searchRoomsRejectsNonPositiveCapacity() {
        assertThrows(BusinessException.class, () -> service.searchRooms(null, 0, null));
    }

    @Test
    void findAvailableRoomsExcludesOccupiedAndNonBookableRooms() {
        when(roomQueryMapper.findOccupiedRoomIds(any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of(2L, 3L));
        when(roomQueryMapper.searchRooms(isNull(), isNull(), isNull(), eq(1)))
                .thenReturn(List.of(
                        roomRow(1L, "云顶厅", "1F-101", 10, 1, "投影"),
                        roomRow(2L, "被占用", "1F-102", 10, 1, "投影"),
                        roomRow(3L, "维护中", "1F-103", 10, 0, "白板")));

        List<MeetingRoom> result =
                service.findAvailableRooms(DATE, LocalTime.of(14, 0), LocalTime.of(16, 0), null, null, null);

        assertEquals(List.of(1L), result.stream().map(MeetingRoom::id).toList());
    }

    @Test
    void findAvailableRoomsTreatsEndNotAfterStartAsNextDayEnd() {
        when(roomQueryMapper.findOccupiedRoomIds(any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of());
        when(roomQueryMapper.searchRooms(any(), any(), any(), anyInt())).thenReturn(List.of());

        service.findAvailableRooms(DATE, LocalTime.of(22, 0), LocalTime.of(2, 0), null, null, null);

        ArgumentCaptor<LocalDateTime> startCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> endCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(roomQueryMapper).findOccupiedRoomIds(startCaptor.capture(), endCaptor.capture());
        assertEquals(DATE.atTime(22, 0), startCaptor.getValue());
        assertEquals(DATE.plusDays(1).atTime(2, 0), endCaptor.getValue());
    }

    @Test
    void findAvailableRoomsRejectsMissingDateOrTime() {
        assertThrows(BusinessException.class,
                () -> service.findAvailableRooms(null, LocalTime.of(9, 0), LocalTime.of(10, 0), null, null, null));
        assertThrows(BusinessException.class,
                () -> service.findAvailableRooms(DATE, null, LocalTime.of(10, 0), null, null, null));
        assertThrows(BusinessException.class,
                () -> service.findAvailableRooms(DATE, LocalTime.of(9, 0), null, null, null, null));
    }

    @Test
    void findAvailableRoomsRejectsNonPositiveCapacity() {
        assertThrows(BusinessException.class,
                () -> service.findAvailableRooms(DATE, LocalTime.of(9, 0), LocalTime.of(10, 0), null, -1, null));
    }
}
