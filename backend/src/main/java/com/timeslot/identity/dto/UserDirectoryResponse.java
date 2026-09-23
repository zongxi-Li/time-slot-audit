/**
 * 文件职责：用于用户选择器/目录查询的精简用户响应。
 * 接口：在 Controller、Service 和前端 API 之间传递结构化数据。
 * 方法：无显式业务方法；record 组件承载目录展示所需的标识和姓名信息。
 */

package com.timeslot.identity.dto;

/**
 * 用户目录条目（GET /api/users/directory）：登录用户选择会议参与人时的最小信息集，
 * 不含邮箱、手机号、信用分等敏感或治理字段，部门名称用于下拉分组展示。
 */
public record UserDirectoryResponse(Long id, String username, String realName,
                                    Long departmentId, String departmentName) {
}
