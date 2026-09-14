package com.timeslot.resource.controller;

import com.timeslot.common.api.ApiResponse;
import com.timeslot.resource.dto.RoomDetailResponse;
import com.timeslot.resource.dto.RoomResponse;
import com.timeslot.resource.service.ResourceQueryService;
import com.timeslot.resource.service.RoomAdminService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/rooms")
public class RoomController {
    private final ResourceQueryService resourceQueryService;
    private final RoomAdminService roomAdminService;

    public RoomController(ResourceQueryService resourceQueryService, RoomAdminService roomAdminService) {
        this.resourceQueryService = resourceQueryService;
        this.roomAdminService = roomAdminService;
    }

    @GetMapping
    public ApiResponse<List<RoomResponse>> listRooms() {
        return ApiResponse.success(resourceQueryService.listRooms().stream().map(RoomResponse::from).toList());
    }

    @GetMapping("/{roomId}")
    public ApiResponse<RoomDetailResponse> getRoom(@PathVariable Long roomId) {
        return ApiResponse.success(roomAdminService.getRoomDetail(roomId));
    }
}
