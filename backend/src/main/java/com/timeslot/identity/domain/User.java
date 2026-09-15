package com.timeslot.identity.domain;

import java.time.LocalDateTime;

/**
 * 用户聚合读模型：部门名称通过 LEFT JOIN 冗余查询，不在库中存储。
 * 预约资格三要素：status（账号状态）、creditScore（信用分）、restrictedUntil（限制截止时间）。
 */
public record User(Long id, String username, String password, String realName, String email, String phone,
                   String role, Integer status, Long departmentId, Integer creditScore,
                   LocalDateTime restrictedUntil, String departmentName) {
    public boolean enabled() {
        return status != null && status == 1;
    }
}
