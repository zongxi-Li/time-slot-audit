/**
 * 文件职责：新增会议参与人接口的请求 DTO。
 * 接口：在 Controller、Service 和前端 API 之间传递结构化数据。
 * 方法：无显式业务方法；record 组件承载用户 ID/名称等请求数据。
*/

package com.timeslot.meeting.dto;

/**
 * 新增参与人请求：按 userId 或 username 二选一（都提供时以 userId 为准）。
 * 前端无用户目录接口时可用 username 直接添加（如 zhangsan / lisi）。
 */
public record AddAttendeeRequest(Long userId, String username) {
}
