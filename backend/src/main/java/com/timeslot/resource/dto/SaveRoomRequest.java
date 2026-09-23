/**
 * 文件职责：管理员创建或修改会议室资料的请求 DTO 及字段校验。
 * 接口：在 Controller、Service 和前端 API 之间传递结构化数据。
 * 方法：无显式业务方法；record 组件承载名称、位置、容量、分类和描述等房间属性。
*/

package com.timeslot.resource.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record SaveRoomRequest(
        @NotBlank(message = "会议室名称不能为空") String name,
        @NotNull(message = "会议室分类不能为空") Long categoryId,
        String location,
        @NotNull(message = "容纳人数不能为空") @Positive(message = "容纳人数必须为正整数") Integer capacity,
        String description) {
}
