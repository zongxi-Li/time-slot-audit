/**
 * 文件职责：提供 会议室资源 HTTP 接口，将请求交给 Service 处理并返回统一响应。
 * 接口：POST /api/admin/rooms；
 *        PUT /api/admin/rooms/{roomId}；
 *        POST /api/admin/rooms/{roomId}/status；
 *        PUT /api/admin/rooms/{roomId}/open-rules；
 *        PUT /api/admin/rooms/{roomId}/facilities；
 *        …。
 */
package com.timeslot.resource.controller;

import com.timeslot.common.api.ApiResponse;
import com.timeslot.common.security.CurrentUserProvider;
import com.timeslot.resource.dto.ChangeRoomStatusRequest;
import com.timeslot.resource.dto.FacilityResponse;
import com.timeslot.resource.dto.MaintenanceResponse;
import com.timeslot.resource.dto.OpenRuleResponse;
import com.timeslot.resource.dto.RoomResponse;
import com.timeslot.resource.dto.SaveFacilitiesRequest;
import com.timeslot.resource.dto.SaveMaintenanceRequest;
import com.timeslot.resource.dto.SaveOpenRulesRequest;
import com.timeslot.resource.dto.SaveRoomRequest;
import com.timeslot.resource.service.RoomAdminService;
import com.timeslot.resource.service.RoomMaintenanceService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

import java.util.List;

@RestController
@RequestMapping("/api/admin/rooms")
public class RoomAdminController {
    private final RoomAdminService roomAdminService;
    private final RoomMaintenanceService roomMaintenanceService;
    private final CurrentUserProvider currentUserProvider;

    public RoomAdminController(RoomAdminService roomAdminService, RoomMaintenanceService roomMaintenanceService,
                               CurrentUserProvider currentUserProvider) {
        this.roomAdminService = roomAdminService;
        this.roomMaintenanceService = roomMaintenanceService;
        this.currentUserProvider = currentUserProvider;
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

    @PutMapping("/{roomId}/open-rules")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<List<OpenRuleResponse>> replaceOpenRules(@PathVariable Long roomId,
                                                                @Valid @RequestBody SaveOpenRulesRequest request) {
        return ApiResponse.success(roomAdminService.replaceOpenRules(roomId, request));
    }

    @PutMapping("/{roomId}/facilities")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<List<FacilityResponse>> replaceFacilities(@PathVariable Long roomId,
                                                                 @Valid @RequestBody SaveFacilitiesRequest request) {
        return ApiResponse.success(roomAdminService.replaceFacilities(roomId, request));
    }

    @PostMapping("/{roomId}/maintenance")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<MaintenanceResponse> createMaintenance(@PathVariable Long roomId,
                                                              @Valid @RequestBody SaveMaintenanceRequest request) {
        Long adminId = currentUserProvider.getRequired().userId();
        return ApiResponse.success(roomMaintenanceService.createPlan(roomId, request, adminId));
    }

    @GetMapping("/{roomId}/maintenance")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<List<MaintenanceResponse>> listMaintenance(@PathVariable Long roomId) {
        return ApiResponse.success(roomMaintenanceService.listPlans(roomId));
    }

    @PostMapping("/{roomId}/maintenance/{planId}/finish")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<MaintenanceResponse> finishMaintenance(@PathVariable Long roomId, @PathVariable Long planId) {
        return ApiResponse.success(roomMaintenanceService.finishPlan(roomId, planId));
    }
}
