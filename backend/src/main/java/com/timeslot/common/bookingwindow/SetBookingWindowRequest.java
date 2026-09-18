package com.timeslot.common.bookingwindow;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/** 管理员设置可预约时段；跨字段规则（整点、不超过 24 小时）由 Service 校验。 */
public record SetBookingWindowRequest(
        @Min(0) @Max(1439) int startMinute,
        @Min(60) @Max(2880) int endMinute) {
}
