/**
 * 文件职责：定义 会议室资源 的实体、值对象或枚举。
 * 接口：供本域 Service、Mapper 和 DTO 转换使用。
 */
package com.timeslot.resource.domain;

import java.util.List;

public record MeetingRoom(Long id, Long categoryId, String name, String location, Integer capacity,
                          MeetingRoomStatus status, String category, List<String> facilities, String description) {
}
