/**
 * 文件职责：管理员驳回预约接口的请求数据及输入校验。
 * 接口：在 Controller、Service 和前端 API 之间传递结构化数据。
 * 方法：无显式业务方法；record 组件承载驳回原因。
*/

package com.timeslot.administration.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RejectReservationRequest(
        @NotBlank(message = "驳回原因不能为空")
        @Size(max = 500, message = "驳回原因不能超过500字")
        String reason) {
}
