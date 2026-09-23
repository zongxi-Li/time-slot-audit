/**
 * 文件职责：表示用户账户及部门、信用分、限制期限等身份领域数据。
 * 接口：供本域 Service、Mapper 和 DTO 转换使用。
 * 方法：enabled 判断用户账号是否处于可用状态；record 访问器由 Java 自动生成。
*/

package com.timeslot.identity.domain;

import java.time.LocalDateTime;

/**
 * 用户聚合读模型：部门名称通过 LEFT JOIN 冗余查询，不在库中存储。
 * 预约资格三要素：status（账号状态）、creditScore（信用分）、restrictedUntil（限制截止时间）。
 */
public record User(Long id, 
    String username, 
    String password, 
    String realName, 
    String email, 
    String phone,           
    String role,
    Integer status, 
    Long departmentId, 
    Integer creditScore,              
    LocalDateTime restrictedUntil, 
    String departmentName) {
    public boolean enabled() {
        return status != null && status == 1;
    }
}
