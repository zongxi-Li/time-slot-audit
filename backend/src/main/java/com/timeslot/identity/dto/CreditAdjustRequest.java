/**
 * 文件职责：管理员调整用户信用分接口的请求 DTO。
 * 接口：在 Controller、Service 和前端 API 之间传递结构化数据。
 * 方法：无显式业务方法；record 组件承载分值变化和原因并执行输入校验。
*/

package com.timeslot.identity.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreditAdjustRequest(
        @NotNull(message = "信用分变化量不能为空") Integer creditChange,
        @NotBlank(message = "调整原因必填") @Size(max = 500) String reason) {
}
