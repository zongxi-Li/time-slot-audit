package com.timeslot.resource.service;

import com.timeslot.resource.domain.MeetingRoom;
import com.timeslot.resource.domain.MeetingRoomStatus;
import com.timeslot.resource.domain.RoomCategory;
import com.timeslot.resource.mapper.ResourceMapper;
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
        return mapper.listRooms().stream().map(ResourceQueryService::toRoom).toList();
    }

    static MeetingRoom toRoom(ResourceMapper.RoomRow row) {
        List<String> facilities = row.getFacilitiesCsv() == null || row.getFacilitiesCsv().isBlank()
                ? Collections.emptyList()
                : Arrays.stream(row.getFacilitiesCsv().split(",")).toList();
        return new MeetingRoom(row.getId(), row.getCategoryId(), row.getRoomName(), row.getLocation(), row.getCapacity(),
                MeetingRoomStatus.fromDb(row.getStatus()), row.getCategoryName(), facilities, row.getDescription());
    }

    static RoomCategory toCategory(ResourceMapper.CategoryRow row) {
        return new RoomCategory(row.getId(), row.getCategoryName(), row.getMinCapacity(),
                row.getMaxCapacity(), row.getApprovalRequired() == 1, row.getMaxDurationMinutes(), row.getAdvanceDays(),
                row.getDescription());
    }
}
