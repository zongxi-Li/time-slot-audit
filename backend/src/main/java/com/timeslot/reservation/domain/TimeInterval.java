/**
 * 文件职责：表示经过起止时间合法性校验的时间区间。
 * 接口：供本域 Service、Mapper 和 DTO 转换使用。
 * 方法：构造时校验时间边界；overlaps 判断本区间与另一区间是否重叠。
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
