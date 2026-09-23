/**
 * 文件职责：定义管理端审批日志可记录的操作类型。
 * 接口：供本域 Service、Mapper 和 DTO 转换使用。
 * 方法：无显式业务方法；枚举常量表示审批、驳回、强制取消等动作。
*/

package com.timeslot.administration.domain;

public enum ApprovalAction {
    APPROVE,
    REJECT
}
