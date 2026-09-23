/**
 * 文件职责：管理会议室维护计划及维护期间的资源占用信息。
 * 接口：由 RoomAdminController 调用。
 * 方法：createPlan 新建计划；listPlans 查询计划；finishPlan 完成计划；requirePlan/requireRoom 校验关联记录；toResponse 转换响应。
*/

package com.timeslot.resource.service;

import com.timeslot.common.api.ErrorCode;
import com.timeslot.common.exception.BusinessException;
import com.timeslot.resource.dto.MaintenanceResponse;
import com.timeslot.resource.dto.SaveMaintenanceRequest;
import com.timeslot.resource.mapper.ResourceMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class RoomMaintenanceService {
    private final ResourceMapper mapper;

    public RoomMaintenanceService(ResourceMapper mapper) {
        this.mapper = mapper;
    }

    @Transactional
    public MaintenanceResponse createPlan(Long roomId, SaveMaintenanceRequest request, Long createdBy) {
        requireRoom(roomId);
        if (!request.endTime().isAfter(request.startTime())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, HttpStatus.BAD_REQUEST, "维护结束时间必须晚于开始时间");
        }
        ResourceMapper.MaintenanceWrite write = new ResourceMapper.MaintenanceWrite();
        write.setRoomId(roomId);
        write.setReason(request.reason().trim());
        write.setStartTime(request.startTime());
        write.setEndTime(request.endTime());
        write.setStatus("PLANNED");
        write.setCreatedBy(createdBy);
        mapper.insertMaintenance(write);
        return getPlanResponse(write.getId());
    }

    public List<MaintenanceResponse> listPlans(Long roomId) {
        requireRoom(roomId);
        return mapper.listMaintenanceByRoom(roomId).stream().map(this::toResponse).toList();
    }

    @Transactional
    public MaintenanceResponse finishPlan(Long roomId, Long planId) {
        requireRoom(roomId);
        ResourceMapper.MaintenanceRow row = requirePlan(planId);
        if (!row.getRoomId().equals(roomId)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, HttpStatus.BAD_REQUEST, "维护计划不属于该会议室");
        }
        if (!"PLANNED".equals(row.getStatus())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, HttpStatus.BAD_REQUEST, "该维护计划已结束");
        }
        mapper.finishMaintenance(planId);
        return getPlanResponse(planId);
    }

    private MaintenanceResponse getPlanResponse(Long planId) {
        return toResponse(requirePlan(planId));
    }

    private ResourceMapper.MaintenanceRow requirePlan(Long planId) {
        ResourceMapper.MaintenanceRow row = mapper.findMaintenanceById(planId);
        if (row == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, HttpStatus.NOT_FOUND, "维护计划不存在");
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

    private MaintenanceResponse toResponse(ResourceMapper.MaintenanceRow row) {
        return new MaintenanceResponse(row.getId(), row.getRoomId(), row.getReason(), row.getStartTime(),
                row.getEndTime(), row.getStatus(), row.getCreatedBy(), row.getCreatedAt());
    }
}
