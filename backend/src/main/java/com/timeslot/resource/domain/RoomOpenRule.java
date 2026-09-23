/**
 * 文件职责：表示会议室某一星期几的开放起止时间规则。
 * 接口：供本域 Service、Mapper 和 DTO 转换使用。
 * 方法：无显式业务方法；record 组件定义开放时段和启用状态。
 */

package com.timeslot.resource.domain;

import java.time.LocalTime;

public record RoomOpenRule(LocalTime openTime, LocalTime closeTime, boolean enabled) {
}
