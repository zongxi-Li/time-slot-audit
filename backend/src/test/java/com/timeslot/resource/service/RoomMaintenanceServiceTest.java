package com.timeslot.resource.service;

import com.timeslot.common.api.ErrorCode;
import com.timeslot.common.exception.BusinessException;
import com.timeslot.resource.dto.MaintenanceResponse;
import com.timeslot.resource.dto.SaveMaintenanceRequest;
import com.timeslot.resource.mapper.ResourceMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RoomMaintenanceServiceTest {
    @Mock ResourceMapper mapper;

    private RoomMaintenanceService service;
    private final LocalDateTime start = LocalDateTime.of(2026, 9, 20, 9, 0);
    private final LocalDateTime end = LocalDateTime.of(2026, 9, 20, 18, 0);

    @BeforeEach
    void setUp() {
        service = new RoomMaintenanceService(mapper);
        when(mapper.findRoomById(1L)).thenReturn(room());
        doAnswer(invocation -> {
            ResourceMapper.MaintenanceWrite write = invocation.getArgument(0);
            write.setId(10L);
            when(mapper.findMaintenanceById(10L)).thenReturn(row(write));
            return 1;
        }).when(mapper).insertMaintenance(any(ResourceMapper.MaintenanceWrite.class));
    }

    private ResourceMapper.RoomRow room() {
        ResourceMapper.RoomRow room = new ResourceMapper.RoomRow();
        room.setId(1L);
        room.setRoomName("A301");
        return room;
    }

    private ResourceMapper.MaintenanceRow row(ResourceMapper.MaintenanceWrite write) {
        ResourceMapper.MaintenanceRow row = new ResourceMapper.MaintenanceRow();
        row.setId(write.getId());
        row.setRoomId(write.getRoomId());
        row.setReason(write.getReason());
        row.setStartTime(write.getStartTime());
        row.setEndTime(write.getEndTime());
        row.setStatus(write.getStatus());
        row.setCreatedBy(write.getCreatedBy());
        return row;
    }

    private SaveMaintenanceRequest request() {
        return new SaveMaintenanceRequest("投影仪检修", start, end);
    }

    @Test
    void createPlanPersistsPlannedRow() {
        MaintenanceResponse response = service.createPlan(1L, request(), 9L);

        assertEquals(10L, response.id());
        assertEquals("PLANNED", response.status());
        assertEquals(9L, response.createdBy());
        verify(mapper).insertMaintenance(any(ResourceMapper.MaintenanceWrite.class));
    }

    @Test
    void createPlanRejectsEndBeforeStart() {
        SaveMaintenanceRequest invalid = new SaveMaintenanceRequest("投影仪检修", end, start);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.createPlan(1L, invalid, 9L));

        assertEquals(ErrorCode.VALIDATION_ERROR, exception.getCode());
        verify(mapper, never()).insertMaintenance(any());
    }

    @Test
    void createPlanRejectsMissingRoom() {
        when(mapper.findRoomById(99L)).thenReturn(null);

        assertEquals(ErrorCode.RESOURCE_NOT_FOUND, assertThrows(BusinessException.class,
                () -> service.createPlan(99L, request(), 9L)).getCode());
    }

    @Test
    void listPlansReturnsRoomHistory() {
        when(mapper.listMaintenanceByRoom(1L)).thenReturn(List.of(plan(10L, "PLANNED")));

        assertEquals(1, service.listPlans(1L).size());
    }

    @Test
    void finishPlanMarksFinished() {
        ResourceMapper.MaintenanceRow planned = plan(10L, "PLANNED");
        when(mapper.findMaintenanceById(10L)).thenReturn(planned, plan(10L, "FINISHED"));

        MaintenanceResponse response = service.finishPlan(1L, 10L);

        assertEquals("FINISHED", response.status());
        verify(mapper).finishMaintenance(10L);
    }

    @Test
    void finishPlanRejectsPlanOfOtherRoom() {
        ResourceMapper.MaintenanceRow other = plan(10L, "PLANNED");
        other.setRoomId(2L);
        when(mapper.findMaintenanceById(10L)).thenReturn(other);

        assertEquals(ErrorCode.VALIDATION_ERROR, assertThrows(BusinessException.class,
                () -> service.finishPlan(1L, 10L)).getCode());
        verify(mapper, never()).finishMaintenance(anyLong());
    }

    @Test
    void finishPlanRejectsAlreadyFinished() {
        when(mapper.findMaintenanceById(10L)).thenReturn(plan(10L, "FINISHED"));

        assertEquals(ErrorCode.VALIDATION_ERROR, assertThrows(BusinessException.class,
                () -> service.finishPlan(1L, 10L)).getCode());
        verify(mapper, never()).finishMaintenance(anyLong());
    }

    private ResourceMapper.MaintenanceRow plan(Long id, String status) {
        ResourceMapper.MaintenanceRow row = new ResourceMapper.MaintenanceRow();
        row.setId(id);
        row.setRoomId(1L);
        row.setReason("投影仪检修");
        row.setStartTime(start);
        row.setEndTime(end);
        row.setStatus(status);
        row.setCreatedBy(9L);
        return row;
    }
}
