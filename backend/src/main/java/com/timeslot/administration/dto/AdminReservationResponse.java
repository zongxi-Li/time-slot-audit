/**
 * 文件职责：定义 管理员运营 的请求或响应 DTO。
 * 接口：在 Controller、Service 和前端 API 之间传递结构化数据。
 */
package com.timeslot.administration.dto;

import java.time.LocalDateTime;
import java.util.List;

public record AdminReservationResponse(
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
        LocalDateTime createdAt,
        List<ApprovalRecordResponse> approvalHistory) {

    public AdminReservationResponse withHistory(List<ApprovalRecordResponse> history) {
        return new AdminReservationResponse(id, reservationNo, roomId, roomName, userId, userName, title,
                startTime, endTime, participantCount, status, remark, createdAt, history);
    }
}
