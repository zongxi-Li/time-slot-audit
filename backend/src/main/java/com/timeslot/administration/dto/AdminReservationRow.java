/**
 * 文件职责：承接管理员预约列表的数据库查询投影。
 * 接口：在 Controller、Service 和前端 API 之间传递结构化数据。
 * 方法：toResponse 将查询行转换为对外返回的 AdminReservationResponse；record 访问器由 Java 自动生成。
*/

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
