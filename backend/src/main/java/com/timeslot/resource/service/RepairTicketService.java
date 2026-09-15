package com.timeslot.resource.service;

import com.timeslot.common.api.ErrorCode;
import com.timeslot.common.exception.BusinessException;
import com.timeslot.common.security.AuthenticatedUser;
import com.timeslot.resource.dto.CreateRepairTicketRequest;
import com.timeslot.resource.dto.RepairTicketResponse;
import com.timeslot.resource.dto.ResolveRepairTicketRequest;
import com.timeslot.resource.mapper.ResourceMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class RepairTicketService {
    private final ResourceMapper mapper;

    public RepairTicketService(ResourceMapper mapper) {
        this.mapper = mapper;
    }

    @Transactional
    public RepairTicketResponse create(Long roomId, CreateRepairTicketRequest request, AuthenticatedUser reporter) {
        requireRoom(roomId);
        String facilityName;
        Long facilityId = request.facilityId();
        if (facilityId != null) {
            ResourceMapper.FacilityRow facility = mapper.findFacilityById(facilityId);
            if (facility == null) {
                throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, HttpStatus.NOT_FOUND, "设施不存在");
            }
            if (!facility.getRoomId().equals(roomId)) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, HttpStatus.BAD_REQUEST, "设施不属于该会议室");
            }
            facilityName = facility.getFacilityName();
        } else {
            facilityName = request.facilityName() == null || request.facilityName().isBlank()
                    ? "整室" : request.facilityName().trim();
        }
        ResourceMapper.RepairWrite write = new ResourceMapper.RepairWrite();
        write.setRoomId(roomId);
        write.setFacilityId(facilityId);
        write.setFacilityName(facilityName);
        write.setIssue(request.issue().trim());
        write.setStatus("OPEN");
        write.setReporterId(reporter.userId());
        write.setReporterName(reporter.username());
        mapper.insertRepairTicket(write);
        return toResponse(requireTicket(write.getId()));
    }

    public List<RepairTicketResponse> list(Long roomId) {
        if (roomId != null) {
            requireRoom(roomId);
        }
        return mapper.listRepairTickets(roomId).stream().map(this::toResponse).toList();
    }

    @Transactional
    public RepairTicketResponse resolve(Long ticketId, ResolveRepairTicketRequest request) {
        ResourceMapper.RepairTicketRow row = requireTicket(ticketId);
        if (!"OPEN".equals(row.getStatus())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, HttpStatus.BAD_REQUEST, "该报修工单已处理");
        }
        String remark = request == null || request.remark() == null || request.remark().isBlank()
                ? null : request.remark().trim();
        if (mapper.resolveRepairTicket(ticketId, remark) == 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, HttpStatus.BAD_REQUEST, "该报修工单已处理");
        }
        return toResponse(requireTicket(ticketId));
    }

    private ResourceMapper.RepairTicketRow requireTicket(Long ticketId) {
        ResourceMapper.RepairTicketRow row = mapper.findRepairTicketById(ticketId);
        if (row == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, HttpStatus.NOT_FOUND, "报修工单不存在");
        }
        return row;
    }

    private ResourceMapper.RoomRow requireRoom(Long roomId) {
        ResourceMapper.RoomRow row = mapper.findRoomById(roomId);
        if (row == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, HttpStatus.NOT_FOUND, "会议室不存在");
        }
        return row;
    }

    private RepairTicketResponse toResponse(ResourceMapper.RepairTicketRow row) {
        return new RepairTicketResponse(row.getId(), row.getRoomId(), row.getRoomName(), row.getFacilityId(),
                row.getFacilityName(), row.getIssue(), row.getStatus(), row.getReporterId(), row.getReporterName(),
                row.getCreatedAt(), row.getResolvedAt(), row.getResolveRemark());
    }
}
