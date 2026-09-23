/**
 * 文件职责：提供系统预约开放时间窗口的 HTTP 查询和设置接口。
 * 方法：current 查询当前窗口；set 校验请求并更新窗口。
 */
package com.timeslot.common.bookingwindow;

import com.timeslot.common.api.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** Read is available to every logged-in user (the board needs it); writes are protected by /api/admin security. */
@RestController
public class BookingWindowController {
    private final BookingWindowService bookingWindowService;

    public BookingWindowController(BookingWindowService bookingWindowService) {
        this.bookingWindowService = bookingWindowService;
    }

    @GetMapping("/api/booking-window")
    public ApiResponse<BookingWindowResponse> current() {
        return ApiResponse.success(bookingWindowService.get());
    }

    @PutMapping("/api/admin/booking-window")
    public ApiResponse<BookingWindowResponse> set(@Valid @RequestBody SetBookingWindowRequest request) {
        return ApiResponse.success(bookingWindowService.set(request.startMinute(), request.endMinute()));
    }
}
