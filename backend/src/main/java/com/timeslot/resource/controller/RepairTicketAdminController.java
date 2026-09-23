/**
 * 文件职责：管理员查询和处理会议室报修工单的 HTTP 接口。
 * 接口：GET /api/admin/repair-tickets；
 *        POST /api/admin/repair-tickets/{ticketId}/resolve。
 * 方法：list 查询工单；resolve 处理指定工单。
*/

package com.timeslot.resource.controller;

import com.timeslot.common.api.ApiResponse;
import com.timeslot.resource.dto.RepairTicketResponse;
import com.timeslot.resource.dto.ResolveRepairTicketRequest;
import com.timeslot.resource.service.RepairTicketService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/repair-tickets")
public class RepairTicketAdminController {
    private final RepairTicketService repairTicketService;

    public RepairTicketAdminController(RepairTicketService repairTicketService) {
        this.repairTicketService = repairTicketService;
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<List<RepairTicketResponse>> list(@RequestParam(required = false) Long roomId) {
        return ApiResponse.success(repairTicketService.list(roomId));
    }

    @PostMapping("/{ticketId}/resolve")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<RepairTicketResponse> resolve(@PathVariable Long ticketId,
                                                     @RequestBody(required = false) ResolveRepairTicketRequest request) {
        return ApiResponse.success(repairTicketService.resolve(ticketId, request));
    }
}
