/**
 * 文件职责：会议室列表和基础资料接口的响应 DTO。
 * 接口：在 Controller、Service 和前端 API 之间传递结构化数据。
 * 方法：from 将 MeetingRoom 领域对象转换为基础响应。
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
