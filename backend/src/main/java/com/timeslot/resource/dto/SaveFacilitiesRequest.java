/**
 * 文件职责：批量替换会议室设施清单的请求 DTO。
 * 接口：在 Controller、Service 和前端 API 之间传递结构化数据。
 * 方法：无显式业务方法；record 组件承载嵌套校验的设施列表。
*/

package com.timeslot.resource.dto;

import jakarta.validation.Valid;

import java.util.List;

public record SaveFacilitiesRequest(List<@Valid SaveFacilityRequest> facilities) {
}
