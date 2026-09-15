package com.timeslot.resource.service;

import com.timeslot.common.api.ErrorCode;
import com.timeslot.common.exception.BusinessException;
import com.timeslot.common.security.AuthenticatedUser;
import com.timeslot.resource.dto.CreateRepairTicketRequest;
import com.timeslot.resource.dto.RepairTicketResponse;
import com.timeslot.resource.dto.ResolveRepairTicketRequest;
import com.timeslot.resource.mapper.ResourceMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RepairTicketServiceTest {
    @Mock ResourceMapper mapper;

    private RepairTicketService service;
    private final AuthenticatedUser reporter = new AuthenticatedUser(5L, "zhangsan", "USER");

    @BeforeEach
    void setUp() {
        service = new RepairTicketService(mapper);
        when(mapper.findRoomById(1L)).thenReturn(room());
        ResourceMapper.FacilityRow projector = new ResourceMapper.FacilityRow();
        projector.setId(30L);
        projector.setRoomId(1L);
        projector.setFacilityName("投影仪");
        when(mapper.findFacilityById(30L)).thenReturn(projector);
        doAnswer(invocation -> {
            ResourceMapper.RepairWrite write = invocation.getArgument(0);
            write.setId(100L);
            when(mapper.findRepairTicketById(100L)).thenReturn(row(write, "OPEN"));
            return 1;
        }).when(mapper).insertRepairTicket(any(ResourceMapper.RepairWrite.class));
    }

    private ResourceMapper.RoomRow room() {
        ResourceMapper.RoomRow room = new ResourceMapper.RoomRow();
        room.setId(1L);
        room.setRoomName("A301");
        return room;
    }

    private ResourceMapper.RepairTicketRow row(ResourceMapper.RepairWrite write, String status) {
        ResourceMapper.RepairTicketRow row = new ResourceMapper.RepairTicketRow();
        row.setId(write.getId());
        row.setRoomId(write.getRoomId());
        row.setRoomName("A301");
        row.setFacilityId(write.getFacilityId());
        row.setFacilityName(write.getFacilityName());
        row.setIssue(write.getIssue());
        row.setStatus(status);
        row.setReporterId(write.getReporterId());
        row.setReporterName(write.getReporterName());
        return row;
    }

    private ResourceMapper.RepairTicketRow ticket(Long id, String status) {
        ResourceMapper.RepairTicketRow row = new ResourceMapper.RepairTicketRow();
        row.setId(id);
        row.setRoomId(1L);
        row.setRoomName("A301");
        row.setFacilityId(30L);
        row.setFacilityName("投影仪");
        row.setIssue("无法开机");
        row.setStatus(status);
        row.setReporterId(5L);
        row.setReporterName("zhangsan");
        return row;
    }

    @Test
    void createWithFacilityUsesSnapshotNameFromDatabase() {
        RepairTicketResponse response = service.create(1L,
                new CreateRepairTicketRequest(30L, null, " 无法开机 "), reporter);

        assertEquals(100L, response.id());
        assertEquals("投影仪", response.facilityName());
        assertEquals("OPEN", response.status());
        assertEquals(5L, response.reporterId());
        verify(mapper).insertRepairTicket(any(ResourceMapper.RepairWrite.class));
    }

    @Test
    void createWholeRoomDefaultsFacilityName() {
        RepairTicketResponse response = service.create(1L,
                new CreateRepairTicketRequest(null, "  ", "灯管闪烁"), reporter);

        assertEquals("整室", response.facilityName());
        assertEquals(null, response.facilityId());
    }

    @Test
    void createRejectsFacilityOfOtherRoom() {
        ResourceMapper.FacilityRow other = new ResourceMapper.FacilityRow();
        other.setId(31L);
        other.setRoomId(2L);
        other.setFacilityName("白板");
        when(mapper.findFacilityById(31L)).thenReturn(other);

        assertEquals(ErrorCode.VALIDATION_ERROR, assertThrows(BusinessException.class,
                () -> service.create(1L, new CreateRepairTicketRequest(31L, null, "故障"), reporter)).getCode());
        verify(mapper, never()).insertRepairTicket(any());
    }

    @Test
    void createRejectsUnknownFacility() {
        when(mapper.findFacilityById(99L)).thenReturn(null);

        assertEquals(ErrorCode.RESOURCE_NOT_FOUND, assertThrows(BusinessException.class,
                () -> service.create(1L, new CreateRepairTicketRequest(99L, null, "故障"), reporter)).getCode());
    }

    @Test
    void listFiltersByRoomWhenProvided() {
        when(mapper.listRepairTickets(1L)).thenReturn(List.of(ticket(100L, "OPEN")));

        assertEquals(1, service.list(1L).size());
        assertEquals(0, service.list(null).size());
        verify(mapper).listRepairTickets(isNull());
    }

    @Test
    void resolveRecordsRemarkAndReturnsResolvedTicket() {
        when(mapper.findRepairTicketById(100L)).thenReturn(ticket(100L, "OPEN"));
        when(mapper.resolveRepairTicket(eq(100L), eq("已更换灯泡"))).thenReturn(1);
        ResourceMapper.RepairTicketRow resolved = ticket(100L, "RESOLVED");
        resolved.setResolveRemark("已更换灯泡");
        when(mapper.findRepairTicketById(100L)).thenReturn(ticket(100L, "OPEN"), resolved);

        RepairTicketResponse response = service.resolve(100L, new ResolveRepairTicketRequest("已更换灯泡"));

        assertEquals("RESOLVED", response.status());
        assertEquals("已更换灯泡", response.resolveRemark());
    }

    @Test
    void resolveRejectsAlreadyResolved() {
        when(mapper.findRepairTicketById(100L)).thenReturn(ticket(100L, "RESOLVED"));

        assertEquals(ErrorCode.VALIDATION_ERROR, assertThrows(BusinessException.class,
                () -> service.resolve(100L, new ResolveRepairTicketRequest("备注"))).getCode());
        verify(mapper, never()).resolveRepairTicket(anyLong(), any());
    }

    @Test
    void resolveDetectsConcurrentResolution() {
        when(mapper.findRepairTicketById(100L)).thenReturn(ticket(100L, "OPEN"));
        when(mapper.resolveRepairTicket(eq(100L), any())).thenReturn(0);

        assertEquals(ErrorCode.VALIDATION_ERROR, assertThrows(BusinessException.class,
                () -> service.resolve(100L, new ResolveRepairTicketRequest("备注"))).getCode());
    }
}
