/**
 * 文件职责：用户侧会议室查询、空闲查询和报修提交 HTTP 接口。
 * 接口：GET /api/rooms（支持 location/minCapacity/facility 条件筛选）；
 *        GET /api/rooms/available（指定日期与时段内空闲会议室）；
 *        GET /api/rooms/{roomId}；
 *        POST /api/rooms/{roomId}/repair-tickets。
 * 方法：listRooms 查询会议室；availableRooms 按条件查询可用会议室；getRoom 查询详情；createRepairTicket 提交报修。
*/

package com.timeslot.resource.controller;

import com.timeslot.common.api.ApiResponse;
import com.timeslot.common.security.CurrentUserProvider;
import com.timeslot.resource.dto.CreateRepairTicketRequest;
import com.timeslot.resource.dto.RepairTicketResponse;
import com.timeslot.resource.dto.RoomDetailResponse;
import com.timeslot.resource.dto.RoomResponse;
import com.timeslot.resource.service.RepairTicketService;
import com.timeslot.resource.service.ResourceQueryService;
import com.timeslot.resource.service.RoomAdminService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@RestController
@RequestMapping("/api/rooms")
public class RoomController {
    private final ResourceQueryService resourceQueryService;
    private final RoomAdminService roomAdminService;
    private final RepairTicketService repairTicketService;
    private final CurrentUserProvider currentUserProvider;

    public RoomController(ResourceQueryService resourceQueryService, RoomAdminService roomAdminService,
                          RepairTicketService repairTicketService, CurrentUserProvider currentUserProvider) {
        this.resourceQueryService = resourceQueryService;
        this.roomAdminService = roomAdminService;
        this.repairTicketService = repairTicketService;
        this.currentUserProvider = currentUserProvider;
    }

    /** 台账列表；location/facility 模糊匹配，minCapacity 为容量下限，缺省即全量。 */
    @GetMapping
    public ApiResponse<List<RoomResponse>> listRooms(
            @RequestParam(required = false) String location,
            @RequestParam(required = false) Integer minCapacity,
            @RequestParam(required = false) String facility) {
        return ApiResponse.success(resourceQueryService.searchRooms(location, minCapacity, facility)
                .stream().map(RoomResponse::from).toList());
    }

    /**
     * 指定日期与时段内空闲的会议室（仅可预约状态、无重叠有效预约）；
     * date 为 yyyy-MM-dd，起止时间为 HH:mm，结束不晚于开始按次日结束计算。
     */
    @GetMapping("/available")
    public ApiResponse<List<RoomResponse>> availableRooms(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.TIME) LocalTime startTime,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.TIME) LocalTime endTime,
            @RequestParam(required = false) String location,
            @RequestParam(required = false) Integer minCapacity,
            @RequestParam(required = false) String facility) {
        return ApiResponse.success(
                resourceQueryService.findAvailableRooms(date, startTime, endTime, location, minCapacity, facility)
                        .stream().map(RoomResponse::from).toList());
    }

    @GetMapping("/{roomId}")
    public ApiResponse<RoomDetailResponse> getRoom(@PathVariable Long roomId) {
        return ApiResponse.success(roomAdminService.getRoomDetail(roomId));
    }

    @PostMapping("/{roomId}/repair-tickets")
    public ApiResponse<RepairTicketResponse> createRepairTicket(@PathVariable Long roomId,
                                                                @Valid @RequestBody CreateRepairTicketRequest request) {
        return ApiResponse.success(repairTicketService.create(roomId, request, currentUserProvider.getRequired()));
    }
}
