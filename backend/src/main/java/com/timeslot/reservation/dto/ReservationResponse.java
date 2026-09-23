/**
 * 文件职责：预约查询、创建和修改接口共用的响应 DTO。
 * 接口：在 Controller、Service 和前端 API 之间传递结构化数据。
 * 方法：from 将 Reservation 领域数据转换为对外响应；record 组件定义返回字段。
 */

package com.timeslot.reservation.dto;

import com.timeslot.reservation.domain.Reservation;

import java.time.Clock;
import java.time.LocalDateTime;

public record ReservationResponse(Long id, String requestId, String reservationNo, Long roomId, String roomName,
                                  Long userId, String userName, String title, LocalDateTime startTime,
                                  LocalDateTime endTime, Integer participantCount, String status,
                                  String displayStatus, String remark, Integer version) {
    public static ReservationResponse from(Reservation reservation, Clock clock) {
        String display = switch (reservation.getStatus()) {
            case PENDING -> "PENDING";
            case REJECTED -> "REJECTED";
            case CANCELLED -> "CANCELLED";
            case CONFIRMED -> {
                LocalDateTime now = LocalDateTime.now(clock);
                if (now.isBefore(reservation.getStartTime())) yield "UPCOMING";
                if (now.isBefore(reservation.getEndTime())) yield "IN_USE";
                yield "COMPLETED";
            }
        };
        return new ReservationResponse(reservation.getId(), reservation.getRequestId(), reservation.getReservationNo(),
                reservation.getRoomId(), reservation.getRoomName(), reservation.getUserId(), reservation.getUserName(),
                reservation.getTitle(), reservation.getStartTime(), reservation.getEndTime(), reservation.getParticipantCount(),
                reservation.getStatus().name(), display, reservation.getRemark(), reservation.getVersion());
    }
}
