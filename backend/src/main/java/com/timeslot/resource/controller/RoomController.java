/**
 * 文件职责：提供 会议室资源 HTTP 接口，将请求交给 Service 处理并返回统一响应。
 * 接口：GET /api/rooms；
 *        GET /api/rooms/{roomId}；
 *        POST /api/rooms/{roomId}/repair-tickets。
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

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

    @GetMapping
    public ApiResponse<List<RoomResponse>> listRooms() {
        return ApiResponse.success(resourceQueryService.listRooms().stream().map(RoomResponse::from).toList());
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
