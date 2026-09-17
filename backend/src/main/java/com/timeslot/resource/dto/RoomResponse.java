/**
 * 文件职责：定义 会议室资源 的请求或响应 DTO。
 * 接口：在 Controller、Service 和前端 API 之间传递结构化数据。
 */
package com.timeslot.resource.dto;

import com.timeslot.resource.domain.MeetingRoom;

import java.util.List;

public record RoomResponse(Long id, String name, String location, Integer capacity, String status,
                           String category, List<String> facilities, String description) {
    public static RoomResponse from(MeetingRoom room) {
        return new RoomResponse(room.id(), room.name(), room.location(), room.capacity(), room.status().name(),
                room.category(), room.facilities(), room.description());
    }
}
