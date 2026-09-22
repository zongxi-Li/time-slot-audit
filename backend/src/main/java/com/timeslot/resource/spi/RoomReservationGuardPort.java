/**
 * 文件职责：resource 域删除会议室时查询 reservation 引用的最小只读端口。
 * 接口：由 reservation 域 adapter 实现；resource 不直接访问 reservation 表或 Mapper。
 */
package com.timeslot.resource.spi;

import java.time.LocalDateTime;

public interface RoomReservationGuardPort {
    boolean hasFutureActiveReservation(Long roomId, LocalDateTime now);

    boolean hasAnyReservation(Long roomId);
}
