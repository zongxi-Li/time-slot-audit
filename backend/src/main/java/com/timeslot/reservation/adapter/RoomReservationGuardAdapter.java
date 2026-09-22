/**
 * 文件职责：将 resource 域的会议室删除保护端口适配到 reservation 域查询。
 * 接口：实现 RoomReservationGuardPort；只调用 reservation 域自己的 Mapper。
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
