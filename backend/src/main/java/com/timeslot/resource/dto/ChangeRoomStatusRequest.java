/**
 * 文件职责：管理员修改会议室状态接口的请求 DTO 及非空校验。
 * 接口：在 Controller、Service 和前端 API 之间传递结构化数据。
 * 方法：无显式业务方法；record 组件承载目标状态。
*/

package com.timeslot.resource.dto;

import jakarta.validation.constraints.NotBlank;

public record ChangeRoomStatusRequest(@NotBlank(message = "会议室状态不能为空") String status) {
}
