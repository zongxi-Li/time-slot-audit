/**
 * 文件职责：管理员处理报修工单时提交的处理说明请求 DTO。
 * 接口：在 Controller、Service 和前端 API 之间传递结构化数据。
 * 方法：无显式业务方法；record 组件承载处理备注。
*/

package com.timeslot.resource.dto;

public record ResolveRepairTicketRequest(String remark) {
}
