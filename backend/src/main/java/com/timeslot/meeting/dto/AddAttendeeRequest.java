/**
 * 文件职责：定义 会议执行 的请求或响应 DTO。
 * 接口：在 Controller、Service 和前端 API 之间传递结构化数据。
 */
package com.timeslot.meeting.dto;

/**
 * 新增参与人请求：按 userId 或 username 二选一（都提供时以 userId 为准）。
 * 前端无用户目录接口时可用 username 直接添加（如 zhangsan / lisi）。
 */
public record AddAttendeeRequest(Long userId, String username) {
}
