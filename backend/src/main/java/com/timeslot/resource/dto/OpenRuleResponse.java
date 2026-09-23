/**
 * 文件职责：向前端返回会议室每周开放规则及规则记录标识。
 * 接口：在 Controller、Service 和前端 API 之间传递结构化数据。
 * 方法：无显式业务方法；record 访问器由 Java 自动生成。
*/

package com.timeslot.resource.dto;

import java.time.LocalTime;

public record OpenRuleResponse(Long id, Long roomId, Integer weekday, LocalTime openTime, LocalTime closeTime,
                               boolean enabled) {
}
