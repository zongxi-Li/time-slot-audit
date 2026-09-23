/**
 * 文件职责：向管理端返回单条预约审批历史记录。
 * 接口：在 Controller、Service 和前端 API 之间传递结构化数据。
 * 方法：无显式业务方法；record 组件定义审批人、动作、原因和时间等返回字段。
*/

package com.timeslot.administration.dto;

import java.time.LocalDateTime;

public record ApprovalRecordResponse(
        Long id,
        Long reservationId,
        Long approverId,
        String approverName,
        String action,
        String remark,
        LocalDateTime createdAt) {
}
