/**
 * 文件职责：管理员设置模拟业务时间的请求 DTO。
 * 方法：无显式业务方法；record 组件承载目标本地时间并声明非空校验。
 */
package com.timeslot.common.time;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

public record SetSystemTimeRequest(@NotNull LocalDateTime currentTime) {
}
