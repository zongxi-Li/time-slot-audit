/**
 * 文件职责：定义 会议室资源 的请求或响应 DTO。
 * 接口：在 Controller、Service 和前端 API 之间传递结构化数据。
 */
package com.timeslot.resource.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * facilityId 为空表示整室报修；指定 facilityId 时设施名称以数据库为准，
 * 未指定设施时未填写名称则默认“整室”。
 */
public record CreateRepairTicketRequest(Long facilityId, String facilityName,
                                        @NotBlank(message = "故障描述不能为空") String issue) {
}
