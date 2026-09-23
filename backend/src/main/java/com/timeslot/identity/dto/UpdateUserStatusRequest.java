/**
 * 文件职责：管理员启用或停用用户账号接口的请求 DTO。
 * 接口：在 Controller、Service 和前端 API 之间传递结构化数据。
 * 方法：无显式业务方法；record 组件承载目标状态及变更原因。
*/

package com.timeslot.identity.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateUserStatusRequest(
        @NotNull(message = "状态不能为空") @Min(value = 0, message = "状态仅支持 0-禁用 / 1-启用") @Max(value = 1, message = "状态仅支持 0-禁用 / 1-启用") Integer status,
        @Size(max = 500, message = "原因最长 500 字符") String reason) {
}
