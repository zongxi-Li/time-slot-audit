/**
 * 文件职责：实现会议室、设施、开放时间和分类的管理员维护。
 * 接口：由资源管理 Controller 调用。
 */
package com.timeslot.resource.service;

import com.timeslot.common.api.ErrorCode;
import com.timeslot.common.exception.BusinessException;
import com.timeslot.resource.domain.MeetingRoomStatus;
import com.timeslot.resource.dto.ChangeRoomStatusRequest;
import com.timeslot.resource.dto.FacilityResponse;
import com.timeslot.resource.dto.OpenRuleResponse;
import com.timeslot.resource.dto.RoomDetailResponse;
import com.timeslot.resource.dto.RoomResponse;
import com.timeslot.resource.dto.SaveFacilitiesRequest;
import com.timeslot.resource.dto.SaveFacilityRequest;
import com.timeslot.resource.dto.SaveOpenRuleRequest;
import com.timeslot.resource.dto.SaveOpenRulesRequest;
import com.timeslot.resource.dto.SaveRoomRequest;
import com.timeslot.resource.mapper.ResourceMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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

    public RoomDetailResponse getRoomDetail(Long roomId) {
        ResourceMapper.RoomRow row = requireRoom(roomId);
        ResourceMapper.CategoryRow category = mapper.findCategory(row.getCategoryId());
        return new RoomDetailResponse(row.getId(), row.getRoomName(), row.getLocation(), row.getCapacity(),
                MeetingRoomStatus.fromDb(row.getStatus()).name(), row.getCategoryId(),
                category == null ? null : category.getCategoryName(), row.getDescription(),
                listFacilities(roomId), listOpenRules(roomId));
    }

    public List<OpenRuleResponse> listOpenRules(Long roomId) {
        requireRoom(roomId);
        return mapper.listOpenRulesByRoom(roomId).stream().map(this::toOpenRuleResponse).toList();
    }

    @Transactional
    public List<OpenRuleResponse> replaceOpenRules(Long roomId, SaveOpenRulesRequest request) {
        requireRoom(roomId);
        List<SaveOpenRuleRequest> rules = request.rules() == null ? List.of() : request.rules();
        Set<Integer> weekdays = new HashSet<>();
        for (SaveOpenRuleRequest rule : rules) {
            if (rule.closeTime() == null || rule.openTime() == null || !rule.closeTime().isAfter(rule.openTime())) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, HttpStatus.BAD_REQUEST,
                        "开放规则关闭时间必须晚于开放时间（当前模型不支持跨日开放）");
            }
            if (!weekdays.add(rule.weekday())) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, HttpStatus.BAD_REQUEST,
                        "同一个星期不能配置多条开放规则");
            }
        }
        mapper.deleteOpenRulesByRoom(roomId);
        if (!rules.isEmpty()) {
            List<ResourceMapper.OpenRuleWrite> writes = rules.stream().map(rule -> {
                ResourceMapper.OpenRuleWrite write = new ResourceMapper.OpenRuleWrite();
                write.setWeekday(rule.weekday());
                write.setOpenTime(rule.openTime());
                write.setCloseTime(rule.closeTime());
                write.setEnabled(rule.enabled() == null || rule.enabled() ? 1 : 0);
                return write;
            }).toList();
            mapper.insertOpenRules(roomId, writes);
        }
        return listOpenRules(roomId);
    }

    public List<FacilityResponse> listFacilities(Long roomId) {
        requireRoom(roomId);
        return mapper.listFacilitiesByRoom(roomId).stream().map(this::toFacilityResponse).toList();
    }

    @Transactional
    public List<FacilityResponse> replaceFacilities(Long roomId, SaveFacilitiesRequest request) {
        requireRoom(roomId);
        List<SaveFacilityRequest> facilities = request.facilities() == null ? List.of() : request.facilities();
        Set<String> names = new HashSet<>();
        for (SaveFacilityRequest facility : facilities) {
            if (!names.add(facility.name().trim())) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, HttpStatus.BAD_REQUEST,
                        "同一会议室的设施名称不能重复");
            }
        }
        mapper.deleteFacilitiesByRoom(roomId);
        if (!facilities.isEmpty()) {
            List<ResourceMapper.FacilityWrite> writes = facilities.stream().map(facility -> {
                ResourceMapper.FacilityWrite write = new ResourceMapper.FacilityWrite();
                write.setFacilityName(facility.name().trim());
                write.setQuantity(facility.quantity());
                write.setDescription(facility.description());
                return write;
            }).toList();
            mapper.insertFacilities(roomId, writes);
        }
        return listFacilities(roomId);
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

    private OpenRuleResponse toOpenRuleResponse(ResourceMapper.OpenRuleRow row) {
        return new OpenRuleResponse(row.getId(), row.getRoomId(), row.getWeekday(), row.getOpenTime(),
                row.getCloseTime(), row.getEnabled() != null && row.getEnabled() == 1);
    }

    private FacilityResponse toFacilityResponse(ResourceMapper.FacilityRow row) {
        return new FacilityResponse(row.getId(), row.getRoomId(), row.getFacilityName(), row.getQuantity(),
                row.getDescription());
    }

    private ResourceMapper.CategoryRow requireCategory(Long categoryId) {
        ResourceMapper.CategoryRow row = mapper.findCategory(categoryId);
        if (row == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, HttpStatus.NOT_FOUND, "会议室分类不存在");
        }
        return row;
    }

    private void requireCapacityInRange(ResourceMapper.CategoryRow category, Integer capacity) {
        Integer min = category.getMinCapacity();
        Integer max = category.getMaxCapacity();
        if (min != null && max != null && (capacity < min || capacity > max)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, HttpStatus.BAD_REQUEST,
                    "会议室容量必须在分类容量范围 [" + min + ", " + max + "] 内");
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
