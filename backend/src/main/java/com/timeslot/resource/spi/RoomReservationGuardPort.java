/**
 * 文件职责：会议室资源管理调用 reservation 领域检查占用情况的端口。
 * 接口：由 reservation 域 adapter 实现；resource 不直接访问 reservation 表或 Mapper。
 * 方法：hasFutureActiveReservation 检查未来有效预约；hasAnyReservation 检查是否存在预约记录。
 */

package com.timeslot.resource.spi;

import java.time.LocalDateTime;

public interface RoomReservationGuardPort {
    boolean hasFutureActiveReservation(Long roomId, LocalDateTime now);

    boolean hasAnyReservation(Long roomId);
}
