package com.timeslot.reservation.dto;

import java.time.LocalDateTime;

/** One active (PENDING/CONFIRMED) occupancy slice of a room, read model for free-slot composition. */
public class OccupancyInterval {
    private Long roomId;
    private LocalDateTime startTime;
    private LocalDateTime endTime;

    public OccupancyInterval() {
    }

    public OccupancyInterval(Long roomId, LocalDateTime startTime, LocalDateTime endTime) {
        this.roomId = roomId;
        this.startTime = startTime;
        this.endTime = endTime;
    }

    public Long getRoomId() { return roomId; }
    public void setRoomId(Long roomId) { this.roomId = roomId; }
    public LocalDateTime getStartTime() { return startTime; }
    public void setStartTime(LocalDateTime startTime) { this.startTime = startTime; }
    public LocalDateTime getEndTime() { return endTime; }
    public void setEndTime(LocalDateTime endTime) { this.endTime = endTime; }
}
