/**
 * 文件职责：向前端返回会议室设施的标识、名称、数量和说明。
 * 接口：在 Controller、Service 和前端 API 之间传递结构化数据。
 * 方法：无显式业务方法；record 访问器由 Java 自动生成。
*/

package com.timeslot.resource.dto;

public record FacilityResponse(Long id, Long roomId, String name, Integer quantity, String description) {
}
