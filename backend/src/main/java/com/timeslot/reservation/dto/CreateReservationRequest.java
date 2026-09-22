/**
 * 文件职责：定义 预约核心 的请求或响应 DTO。
 * 接口：在 Controller、Service 和前端 API 之间传递结构化数据。
 */
package com.timeslot.reservation.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;

/**
 * {@code repeatWeeks} 为选填的周期性会议（按周重复）周数：缺省或 1 表示单次会议；
 * 2..8 表示从首次开始时刻起每周同一时段批量创建 N 场预约，逐周做冲突与规则校验。
 *
 * <p>{@code attendeeIds} 为选填的初始参与人 userId 列表（不含创建人，组织者行自动补齐）：
 * 可空；每一场（含周期性展开的每一周）都会登记同一批参与人，人数上限受申报人数约束，
 * 校验与落库由 meeting 域经 {@code ReservationAttendeePort} 完成。
 */
public record CreateReservationRequest(
        @NotBlank @Size(max = 100) String requestId,
        @NotNull Long roomId,
        @NotBlank @Size(max = 100) String title,
        @NotNull LocalDateTime startTime,
        @NotNull LocalDateTime endTime,
        @NotNull @Min(1) Integer participantCount,
        @Size(max = 500) String remark,
        @Min(1) @Max(8) Integer repeatWeeks,
        List<Long> attendeeIds
) {
}
