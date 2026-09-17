/**
 * 文件职责：定义 会议室资源 的请求或响应 DTO。
 * 接口：在 Controller、Service 和前端 API 之间传递结构化数据。
 */
package com.timeslot.resource.dto;

import java.util.List;

public record RoomDetailResponse(Long id, String name, String location, Integer capacity, String status,
                                 Long categoryId, String category, String description,
                                 List<FacilityResponse> facilities, List<OpenRuleResponse> openRules) {
}
