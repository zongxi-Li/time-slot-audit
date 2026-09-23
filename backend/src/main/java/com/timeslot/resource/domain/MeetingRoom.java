/**
 * 文件职责：表示会议室及其分类、位置、容量和开放状态等领域数据。
 * 接口：供本域 Service、Mapper 和 DTO 转换使用。
 * 方法：无显式业务方法；record 组件定义会议室属性。
*/

package com.timeslot.resource.domain;

import java.util.List;

public record MeetingRoom(Long id, Long categoryId, String name, String location, Integer capacity,
                          MeetingRoomStatus status, String category, List<String> facilities, String description) {
}
