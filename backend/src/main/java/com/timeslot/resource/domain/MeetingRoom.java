package com.timeslot.resource.domain;

import java.util.List;

public record MeetingRoom(Long id, Long categoryId, String name, String location, Integer capacity,
                          MeetingRoomStatus status, String category, List<String> facilities) {
}
