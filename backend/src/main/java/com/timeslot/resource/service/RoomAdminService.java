package com.timeslot.resource.service;

import com.timeslot.common.api.ErrorCode;
import com.timeslot.common.exception.BusinessException;
import com.timeslot.resource.domain.MeetingRoomStatus;
import com.timeslot.resource.dto.ChangeRoomStatusRequest;
import com.timeslot.resource.dto.RoomResponse;
import com.timeslot.resource.dto.SaveRoomRequest;
import com.timeslot.resource.mapper.ResourceMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;

@Service
public class RoomAdminService {
    private final ResourceMapper mapper;

    public RoomAdminService(ResourceMapper mapper) {
        this.mapper = mapper;
    }

    @Transactional
    public RoomResponse createRoom(SaveRoomRequest request) {
        ResourceMapper.CategoryRow category = requireCategory(request.categoryId());
        requireCapacityInRange(category, request.capacity());
        requireRoomNameFree(request.name(), null);
        ResourceMapper.RoomWrite room = new ResourceMapper.RoomWrite();
        room.setCategoryId(request.categoryId());
        room.setRoomName(request.name().trim());
        room.setLocation(request.location());
        room.setCapacity(request.capacity());
        room.setStatus(MeetingRoomStatus.AVAILABLE.toDb());
        room.setDescription(request.description());
        try {
            mapper.insertRoom(room);
        } catch (DuplicateKeyException exception) {
            throw duplicateRoomName();
        }
        return getRoomResponse(room.getId());
    }

    @Transactional
    public RoomResponse updateRoom(Long roomId, SaveRoomRequest request) {
        requireRoom(roomId);
        ResourceMapper.CategoryRow category = requireCategory(request.categoryId());
        requireCapacityInRange(category, request.capacity());
        requireRoomNameFree(request.name(), roomId);
        ResourceMapper.RoomWrite room = new ResourceMapper.RoomWrite();
        room.setId(roomId);
        room.setCategoryId(request.categoryId());
        room.setRoomName(request.name().trim());
        room.setLocation(request.location());
        room.setCapacity(request.capacity());
        room.setDescription(request.description());
        try {
            mapper.updateRoom(room);
        } catch (DuplicateKeyException exception) {
            throw duplicateRoomName();
        }
        return getRoomResponse(roomId);
    }

    @Transactional
    public RoomResponse changeStatus(Long roomId, ChangeRoomStatusRequest request) {
        requireRoom(roomId);
        MeetingRoomStatus status = parseStatus(request.status());
        mapper.updateRoomStatus(roomId, status.toDb());
        return getRoomResponse(roomId);
    }

    public RoomResponse getRoomResponse(Long roomId) {
        ResourceMapper.RoomRow row = requireRoom(roomId);
        ResourceMapper.CategoryRow category = mapper.findCategory(row.getCategoryId());
        List<String> facilities = mapper.listFacilitiesByRoom(roomId).stream()
                .map(ResourceMapper.FacilityRow::getFacilityName).toList();
        return new RoomResponse(row.getId(), row.getRoomName(), row.getLocation(), row.getCapacity(),
                MeetingRoomStatus.fromDb(row.getStatus()).name(), category == null ? null : category.getCategoryName(),
                facilities, row.getDescription());
    }

    private ResourceMapper.RoomRow requireRoom(Long roomId) {
        ResourceMapper.RoomRow row = mapper.findRoomById(roomId);
        if (row == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, HttpStatus.NOT_FOUND, "会议室不存在");
        }
        return row;
    }

    private ResourceMapper.CategoryRow requireCategory(Long categoryId) {
        ResourceMapper.CategoryRow row = mapper.findCategory(categoryId);
        if (row == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, HttpStatus.NOT_FOUND, "会议室分类不存在");
        }
        return row;
    }

    private void requireCapacityInRange(ResourceMapper.CategoryRow category, Integer capacity) {
        if (capacity < category.getMinCapacity() || capacity > category.getMaxCapacity()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, HttpStatus.BAD_REQUEST,
                    "会议室容量必须在分类容量范围 [" + category.getMinCapacity() + ", " + category.getMaxCapacity() + "] 内");
        }
    }

    private void requireRoomNameFree(String roomName, Long selfId) {
        ResourceMapper.RoomRow existing = mapper.findRoomByName(roomName.trim());
        if (existing != null && !existing.getId().equals(selfId)) {
            throw duplicateRoomName();
        }
    }

    private BusinessException duplicateRoomName() {
        return new BusinessException(ErrorCode.VALIDATION_ERROR, HttpStatus.BAD_REQUEST, "会议室名称已存在");
    }

    private MeetingRoomStatus parseStatus(String status) {
        try {
            return MeetingRoomStatus.valueOf(status.trim().toUpperCase());
        } catch (IllegalArgumentException exception) {
            List<String> allowed = Arrays.stream(MeetingRoomStatus.values()).map(Enum::name).toList();
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, HttpStatus.BAD_REQUEST,
                    "会议室状态无效，仅允许 " + allowed);
        }
    }
}
