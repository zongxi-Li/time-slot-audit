package com.timeslot.resource.controller;

import com.timeslot.common.api.ApiResponse;
import com.timeslot.resource.dto.ChangeRoomStatusRequest;
import com.timeslot.resource.dto.RoomResponse;
import com.timeslot.resource.dto.SaveRoomRequest;
import com.timeslot.resource.service.RoomAdminService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/admin/rooms")
public class RoomAdminController {
    private final RoomAdminService roomAdminService;

    public RoomAdminController(RoomAdminService roomAdminService) {
        this.roomAdminService = roomAdminService;
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<RoomResponse> createRoom(@Valid @RequestBody SaveRoomRequest request) {
        return ApiResponse.success(roomAdminService.createRoom(request));
    }

    @PutMapping("/{roomId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<RoomResponse> updateRoom(@PathVariable Long roomId, @Valid @RequestBody SaveRoomRequest request) {
        return ApiResponse.success(roomAdminService.updateRoom(roomId, request));
    }

    @PostMapping("/{roomId}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<RoomResponse> changeStatus(@PathVariable Long roomId,
                                                  @Valid @RequestBody ChangeRoomStatusRequest request) {
        return ApiResponse.success(roomAdminService.changeStatus(roomId, request));
    }
}
