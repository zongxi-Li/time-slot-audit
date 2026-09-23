/**
 * 文件职责：会议室详情接口的组合响应，包含基础属性及关联资源信息。
 * 接口：在 Controller、Service 和前端 API 之间传递结构化数据。
 * 方法：无显式业务方法；record 组件定义详情展示字段。
*/

package com.timeslot.resource.dto;

import java.util.List;

public record RoomDetailResponse(Long id, String name, String location, Integer capacity, String status,
                                 Long categoryId, String category, String description,
                                 List<FacilityResponse> facilities, List<OpenRuleResponse> openRules) {
}
