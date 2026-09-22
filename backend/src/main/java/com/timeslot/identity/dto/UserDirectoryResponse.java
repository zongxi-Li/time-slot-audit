/**
 * 文件职责：定义 身份与用户 的请求或响应 DTO。
 * 接口：在 Controller、Service 和前端 API 之间传递结构化数据。
 */
package com.timeslot.identity.dto;

/**
 * 用户目录条目（GET /api/users/directory）：登录用户选择会议参与人时的最小信息集，
 * 不含邮箱、手机号、信用分等敏感或治理字段，部门名称用于下拉分组展示。
 */
public record UserDirectoryResponse(Long id, String username, String realName,
                                    Long departmentId, String departmentName) {
}
