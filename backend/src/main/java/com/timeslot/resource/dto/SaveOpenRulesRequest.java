/**
 * 文件职责：批量替换会议室开放规则的请求 DTO。
 * 接口：在 Controller、Service 和前端 API 之间传递结构化数据。
 * 方法：无显式业务方法；record 组件承载经过嵌套校验的规则列表。
*/

package com.timeslot.resource.dto;

import jakarta.validation.Valid;

import java.util.List;

public record SaveOpenRulesRequest(List<@Valid SaveOpenRuleRequest> rules) {
}
