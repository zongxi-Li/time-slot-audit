package com.timeslot.administration.dto;

import java.time.LocalDateTime;

/** Persistence projection kept separate from the API model's approval history. */
public record AdminReservationRow(
        Long id,
        String reservationNo,
        Long roomId,
        String roomName,
        Long userId,
        String userName,
        String title,
        LocalDateTime startTime,
        LocalDateTime endTime,
        Integer participantCount,
        String status,
        String remark,
        LocalDateTime createdAt) {

    public AdminReservationResponse toResponse() {
        return new AdminReservationResponse(id, reservationNo, roomId, roomName, userId, userName, title,
                startTime, endTime, participantCount, status, remark, createdAt, java.util.List.of());
    }
}
