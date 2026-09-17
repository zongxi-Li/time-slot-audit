/**
 * 文件职责：定义 预约核心 的实体、值对象或枚举。
 * 接口：供本域 Service、Mapper 和 DTO 转换使用。
 */
package com.timeslot.reservation.domain;

import java.time.LocalDateTime;

public record TimeInterval(LocalDateTime start, LocalDateTime end) {
    public TimeInterval {
        if (start == null || end == null || !start.isBefore(end)) {
            throw new IllegalArgumentException("start must be before end");
        }
    }

    public boolean overlaps(TimeInterval other) {
        return start.isBefore(other.end()) && end.isAfter(other.start());
    }
}
