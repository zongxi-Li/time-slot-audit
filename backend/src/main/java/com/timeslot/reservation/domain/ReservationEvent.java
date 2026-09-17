/**
 * 文件职责：定义预约生命周期的业务事件，与展示状态无关。
 * 接口：供 ReservationStateMachine 计算合法目标状态使用。
 */
package com.timeslot.reservation.domain;

public enum ReservationEvent {
    /** 管理员审批通过：PENDING -> CONFIRMED。 */
    APPROVE("审批"),
    /** 管理员驳回：PENDING -> REJECTED（驳回理由属于 administration 的 approval_record）。 */
    REJECT("驳回"),
    /** 预约人主动取消：PENDING/CONFIRMED -> CANCELLED。 */
    OWNER_CANCEL("取消"),
    /** 管理员强制取消：PENDING/CONFIRMED -> CANCELLED。 */
    FORCE_CANCEL("强制取消"),
    /** 修改/改期：PENDING/CONFIRMED 按新会议室审批规则重算为 PENDING 或 CONFIRMED。 */
    RESCHEDULE("修改");

    private final String label;

    ReservationEvent(String label) {
        this.label = label;
    }

    /** 用于非法迁移时的用户可读提示，例如“当前预约状态不可取消”。 */
    public String label() {
        return label;
    }
}
