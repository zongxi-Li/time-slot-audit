/**
 * 文件职责：预约生命周期状态迁移矩阵的唯一权威实现。
 * 接口：供 reservation 域 Service 调用；业务 Guard（身份、时间、原因、冲突）不在此处。
 */
package com.timeslot.reservation.domain;

import com.timeslot.common.api.ErrorCode;
import com.timeslot.common.exception.BusinessException;

/**
 * 轻量状态机：整个项目中“当前状态能否迁移、目标状态是什么”只在这里维护。
 * REJECTED 和 CANCELLED 是终态，任何事件都不可再迁移。
 * 创建预约不伪造 null -> XXX 迁移，初始状态由 {@link #initialStatus(boolean)} 计算。
 */
public final class ReservationStateMachine {
    private ReservationStateMachine() {
    }

    /** 创建预约的初始状态：受控分类写 PENDING（立即占用时间资源），否则 CONFIRMED。 */
    public static ReservationStatus initialStatus(boolean approvalRequired) {
        return approvalRequired ? ReservationStatus.PENDING : ReservationStatus.CONFIRMED;
    }

    /**
     * 不依赖新会议室审批规则的事件（APPROVE / REJECT / OWNER_CANCEL / FORCE_CANCEL）。
     */
    public static ReservationStatus transition(ReservationStatus current, ReservationEvent event) {
        if (event == ReservationEvent.RESCHEDULE) {
            throw new IllegalArgumentException("RESCHEDULE 必须使用三参 transition 并提供 approvalRequired");
        }
        return transition(current, event, false);
    }

    /**
     * 唯一合法迁移矩阵：
     *
     * <pre>
     * APPROVE:       PENDING               -> CONFIRMED
     * REJECT:        PENDING               -> REJECTED
     * OWNER_CANCEL:  PENDING / CONFIRMED   -> CANCELLED
     * FORCE_CANCEL:  PENDING / CONFIRMED   -> CANCELLED
     * RESCHEDULE:    PENDING / CONFIRMED   -> PENDING（新会议室需审批）或 CONFIRMED（无需审批）
     * </pre>
     *
     * {@code approvalRequired} 仅在 RESCHEDULE 时读取，表示修改后目标会议室的分类规则。
     * 非法迁移统一抛 RESERVATION_INVALID_STATE，由调用方原样返回给前端。
     */
    public static ReservationStatus transition(ReservationStatus current, ReservationEvent event, boolean approvalRequired) {
        if (current == null) {
            throw new BusinessException(ErrorCode.RESERVATION_INVALID_STATE, "预约状态缺失，不可" + event.label());
        }
        return switch (event) {
            case APPROVE -> requirePending(current, event, ReservationStatus.CONFIRMED);
            case REJECT -> requirePending(current, event, ReservationStatus.REJECTED);
            case OWNER_CANCEL, FORCE_CANCEL -> requireActive(current, event, ReservationStatus.CANCELLED);
            case RESCHEDULE -> requireActive(current, event,
                    approvalRequired ? ReservationStatus.PENDING : ReservationStatus.CONFIRMED);
        };
    }

    private static ReservationStatus requirePending(ReservationStatus current, ReservationEvent event,
                                                    ReservationStatus target) {
        if (current != ReservationStatus.PENDING) {
            throw illegal(current, event);
        }
        return target;
    }

    private static ReservationStatus requireActive(ReservationStatus current, ReservationEvent event,
                                                   ReservationStatus target) {
        if (current != ReservationStatus.PENDING && current != ReservationStatus.CONFIRMED) {
            throw illegal(current, event);
        }
        return target;
    }

    private static BusinessException illegal(ReservationStatus current, ReservationEvent event) {
        return new BusinessException(ErrorCode.RESERVATION_INVALID_STATE,
                "当前预约状态不可" + event.label() + "（" + current + "）");
    }
}
