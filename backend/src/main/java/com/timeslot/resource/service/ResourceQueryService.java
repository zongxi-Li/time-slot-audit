/**
 * 文件职责：为用户侧提供会议室列表、条件检索和空闲时段查询。
 * 接口：被 RoomController 和管理服务调用。
 * 方法：listRooms 查询全部可展示会议室；searchRooms 按地点/容量/设施筛选；findAvailableRooms 按日期时段查空闲房间；其余方法校验容量并规范化可选条件。
*/

package com.timeslot.resource.service;

import com.timeslot.common.api.ErrorCode;
import com.timeslot.common.exception.BusinessException;
import com.timeslot.resource.domain.MeetingRoom;
import com.timeslot.resource.domain.MeetingRoomStatus;
import com.timeslot.resource.domain.RoomCategory;
import com.timeslot.resource.mapper.ResourceMapper;
import com.timeslot.resource.mapper.RoomQueryMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@Service
public class ResourceQueryService {
    private final ResourceMapper mapper;
    private final RoomQueryMapper roomQueryMapper;

    public ResourceQueryService(ResourceMapper mapper, RoomQueryMapper roomQueryMapper) {
        this.mapper = mapper;
        this.roomQueryMapper = roomQueryMapper;
    }

    public List<MeetingRoom> listRooms() {
        return mapper.listRooms().stream().map(ResourceQueryService::toRoom).toList();
    }

    /**
     * 按位置模糊、最小容量、设施名筛选会议室台账；任一条件为空白即视为未提供。
     */
    public List<MeetingRoom> searchRooms(String location, Integer minCapacity, String facility) {
        requirePositiveCapacity(minCapacity);
        return roomQueryMapper
                .searchRooms(blankToNull(location), minCapacity, blankToNull(facility), null)
                .stream().map(ResourceQueryService::toRoom).toList();
    }

    /**
     * 查询指定日期与时段内空闲的会议室：仅统计 AVAILABLE 状态，且扣除该时段存在
     * 有效预约（PENDING/CONFIRMED）的会议室，占用判定与预约冲突检测同语义（半开区间）。
     * 结束时间不晚于开始时间按次日结束计算，与预约的跨天约定一致。
     */
    public List<MeetingRoom> findAvailableRooms(LocalDate date, LocalTime startTime, LocalTime endTime,
                                                String location, Integer minCapacity, String facility) {
        if (date == null || startTime == null || endTime == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, HttpStatus.BAD_REQUEST,
                    "查询日期与起止时间不能为空");
        }
        requirePositiveCapacity(minCapacity);
        LocalDate endDate = endTime.isAfter(startTime) ? date : date.plusDays(1);
        LocalDateTime start = date.atTime(startTime);
        LocalDateTime end = endDate.atTime(endTime);
        List<Long> occupiedRoomIds = roomQueryMapper.findOccupiedRoomIds(start, end);
        return roomQueryMapper
                .searchRooms(blankToNull(location), minCapacity, blankToNull(facility),
                        MeetingRoomStatus.AVAILABLE.toDb())
                .stream()
                .filter(row -> !occupiedRoomIds.contains(row.getId()))
                .map(ResourceQueryService::toRoom)
                .toList();
    }

    private void requirePositiveCapacity(Integer minCapacity) {
        if (minCapacity != null && minCapacity < 1) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, HttpStatus.BAD_REQUEST,
                    "最小容量必须为正整数");
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
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
