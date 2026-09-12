package com.timeslot.resource.controller;

import com.timeslot.common.api.ApiResponse;
import com.timeslot.resource.dto.RoomResponse;
import com.timeslot.resource.service.ResourceQueryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/rooms")
public class RoomController {
    private final ResourceQueryService resourceQueryService;

    public RoomController(ResourceQueryService resourceQueryService) {
        this.resourceQueryService = resourceQueryService;
    }

    @GetMapping
    public ApiResponse<List<RoomResponse>> listRooms() {
        return ApiResponse.success(resourceQueryService.listRooms().stream().map(RoomResponse::from).toList());
    }
}
