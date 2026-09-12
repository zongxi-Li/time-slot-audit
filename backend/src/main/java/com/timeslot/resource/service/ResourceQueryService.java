package com.timeslot.resource.service;

import com.timeslot.common.api.ErrorCode;
import com.timeslot.common.exception.BusinessException;
import com.timeslot.resource.domain.MeetingRoom;
import com.timeslot.resource.domain.MeetingRoomStatus;
import com.timeslot.resource.domain.RoomCategory;
import com.timeslot.resource.domain.RoomOpenRule;
import com.timeslot.resource.mapper.ResourceMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@Service
public class ResourceQueryService {
    private final ResourceMapper mapper;

    public ResourceQueryService(ResourceMapper mapper) {
        this.mapper = mapper;
    }

    public List<MeetingRoom> listRooms() {
        return mapper.listRooms().stream().map(this::toRoom).toList();
    }

    /** Must be called inside the reservation transaction; the mapper performs SELECT ... FOR UPDATE. */
    public MeetingRoom lockRoom(Long roomId) {
        ResourceMapper.RoomRow row = mapper.findRoomForUpdate(roomId);
        if (row == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, HttpStatus.NOT_FOUND, "会议室不存在");
        }
        return toRoom(row);
    }

    public RoomCategory getCategory(Long categoryId) {
        ResourceMapper.CategoryRow row = mapper.findCategory(categoryId);
        if (row == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, HttpStatus.NOT_FOUND, "会议室分类不存在");
        }
        return new RoomCategory(row.getId(), row.getCategoryName(), row.getApprovalRequired() == 1,
                row.getMaxDurationMinutes(), row.getAdvanceDays());
    }

    public RoomOpenRule getOpenRule(Long roomId, int weekday) {
        ResourceMapper.OpenRuleRow row = mapper.findOpenRule(roomId, weekday);
        return row == null ? null : new RoomOpenRule(row.getOpenTime(), row.getCloseTime(), row.getEnabled() == 1);
    }

    private MeetingRoom toRoom(ResourceMapper.RoomRow row) {
        List<String> facilities = row.getFacilitiesCsv() == null || row.getFacilitiesCsv().isBlank()
                ? Collections.emptyList()
                : Arrays.stream(row.getFacilitiesCsv().split(",")).toList();
        return new MeetingRoom(row.getId(), row.getCategoryId(), row.getRoomName(), row.getLocation(), row.getCapacity(),
                MeetingRoomStatus.fromDb(row.getStatus()), row.getCategoryName(), facilities);
    }
}
