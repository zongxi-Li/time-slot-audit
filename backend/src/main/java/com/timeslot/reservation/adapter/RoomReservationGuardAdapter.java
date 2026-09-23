/**
 * 文件职责：适配 reservation 领域对会议室占用情况的防护查询。
 * 接口：实现 RoomReservationGuardPort；只调用 reservation 域自己的 Mapper。
 * 方法：hasFutureActiveReservation 检查未来是否有有效预约；hasAnyReservation 检查是否存在关联预约。
 */

package com.timeslot.reservation.adapter;

import com.timeslot.reservation.mapper.ReservationMapper;
import com.timeslot.resource.spi.RoomReservationGuardPort;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class RoomReservationGuardAdapter implements RoomReservationGuardPort {
    private final ReservationMapper reservationMapper;

    public RoomReservationGuardAdapter(ReservationMapper reservationMapper) {
        this.reservationMapper = reservationMapper;
    }

    @Override
    public boolean hasFutureActiveReservation(Long roomId, LocalDateTime now) {
        return reservationMapper.countFutureActiveReservations(roomId, now) > 0;
    }

    @Override
    public boolean hasAnyReservation(Long roomId) {
        return reservationMapper.countAllReservationsByRoomId(roomId) > 0;
    }
}
