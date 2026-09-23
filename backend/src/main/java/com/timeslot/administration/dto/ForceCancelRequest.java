/**
 * 文件职责：管理员强制取消预约接口的请求数据及输入校验。
 * 接口：在 Controller、Service 和前端 API 之间传递结构化数据。
 * 方法：无显式业务方法；record 组件承载取消原因。
*/

package com.timeslot.administration.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ForceCancelRequest(
        @NotBlank(message = "强制取消原因不能为空")
        @Size(max = 500, message = "强制取消原因不能超过500字")
        String reason) {
}
