/**
 * 文件职责：管理端预约详情/列表响应，携带预约信息及审批历史。
 * 接口：在 Controller、Service 和前端 API 之间传递结构化数据。
 * 方法：withHistory 在保留预约字段的基础上生成带审批记录的响应；record 访问器由 Java 自动生成。
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
