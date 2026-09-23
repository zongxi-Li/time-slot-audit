/**
 * 文件职责：提供业务时钟查询、设置固定时间和恢复实时钟的管理接口。
 * 方法：current 查询时钟状态；set 设置模拟时间；reset 恢复实时系统时间。
 */
package com.timeslot.common.time;

import com.timeslot.common.api.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** Read is available to every logged-in user; writes are protected by /api/admin security. */
@RestController
public class SystemTimeController {
    private final SystemTimeService systemTimeService;

    public SystemTimeController(SystemTimeService systemTimeService) {
        this.systemTimeService = systemTimeService;
    }

    @GetMapping("/api/system-time")
    public ApiResponse<SystemTimeResponse> current() {
        return ApiResponse.success(systemTimeService.snapshot());
    }

    @PutMapping("/api/admin/system-time")
    public ApiResponse<SystemTimeResponse> set(@Valid @RequestBody SetSystemTimeRequest request) {
        return ApiResponse.success(systemTimeService.setFixedTime(request.currentTime()));
    }

    @DeleteMapping("/api/admin/system-time")
    public ApiResponse<SystemTimeResponse> reset() {
        return ApiResponse.success(systemTimeService.resetToRealtime());
    }
}
