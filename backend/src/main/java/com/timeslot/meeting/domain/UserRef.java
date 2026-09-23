/**
 * 文件职责：保存 meeting 领域所需的最小用户引用信息，避免直接依赖 identity 实体。
 * 接口：供本域 Service、Mapper 和 DTO 转换使用。
 * 方法：无显式业务方法；访问器用于读写引用字段。
*/

package com.timeslot.meeting.domain;

/** 跨域只读引用的用户最小信息（来源 sys_user，仅用于参与人解析与展示）。 */
public class UserRef {
    private Long userId;
    private String username;
    private String realName;

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getRealName() { return realName; }
    public void setRealName(String realName) { this.realName = realName; }
}
