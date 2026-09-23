/**
 * 文件职责：管理员设置或解除用户预约限制的请求 DTO。
 * 接口：在 Controller、Service 和前端 API 之间传递结构化数据。
 * 方法：无显式业务方法；record 组件承载限制期限、状态或原因等请求字段。
*/

package com.timeslot.identity.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

/**
 * 设置/解除限制（黑名单）请求。
 * restrictedUntil 为空表示解除限制；非空表示设置限制，必须为未来时间（Service 校验）。
 * 人工操作必须填写原因。
 */
public record RestrictRequest(
        @NotBlank(message = "操作原因必填") @Size(max = 500) String reason,
        LocalDateTime restrictedUntil) {
}
