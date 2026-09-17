/**
 * 文件职责：验证 ReservationStateMachine 的合法/非法状态迁移完整矩阵。
 * 接口：使用 JUnit，不属于运行时接口。
 */
package com.timeslot.reservation.domain;

import com.timeslot.common.api.ErrorCode;
import com.timeslot.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ReservationStateMachineTest {

    // —— 创建初始状态：不伪造 null -> XXX 迁移 ——

    @Test
    void initialStatusFollowsRoomApprovalRule() {
        assertEquals(ReservationStatus.PENDING, ReservationStateMachine.initialStatus(true));
        assertEquals(ReservationStatus.CONFIRMED, ReservationStateMachine.initialStatus(false));
    }

    // —— 合法迁移 ——

    @Test
    void pendingApproveMovesToConfirmed() {
        assertEquals(ReservationStatus.CONFIRMED,
                ReservationStateMachine.transition(ReservationStatus.PENDING, ReservationEvent.APPROVE));
    }

    @Test
    void pendingRejectMovesToRejected() {
        assertEquals(ReservationStatus.REJECTED,
                ReservationStateMachine.transition(ReservationStatus.PENDING, ReservationEvent.REJECT));
    }

    @Test
    void pendingOwnerCancelMovesToCancelled() {
        assertEquals(ReservationStatus.CANCELLED,
                ReservationStateMachine.transition(ReservationStatus.PENDING, ReservationEvent.OWNER_CANCEL));
    }

    @Test
    void confirmedOwnerCancelMovesToCancelled() {
        assertEquals(ReservationStatus.CANCELLED,
                ReservationStateMachine.transition(ReservationStatus.CONFIRMED, ReservationEvent.OWNER_CANCEL));
    }

    @Test
    void pendingForceCancelMovesToCancelled() {
        assertEquals(ReservationStatus.CANCELLED,
                ReservationStateMachine.transition(ReservationStatus.PENDING, ReservationEvent.FORCE_CANCEL));
    }

    @Test
    void confirmedForceCancelMovesToCancelled() {
        assertEquals(ReservationStatus.CANCELLED,
                ReservationStateMachine.transition(ReservationStatus.CONFIRMED, ReservationEvent.FORCE_CANCEL));
    }

    // —— RESCHEDULE：目标状态按新会议室审批规则重算 ——

    @Test
    void reschedulePendingToApprovalRoomStaysPending() {
        assertEquals(ReservationStatus.PENDING, ReservationStateMachine.transition(
                ReservationStatus.PENDING, ReservationEvent.RESCHEDULE, true));
    }

    @Test
    void reschedulePendingToNormalRoomBecomesConfirmed() {
        assertEquals(ReservationStatus.CONFIRMED, ReservationStateMachine.transition(
                ReservationStatus.PENDING, ReservationEvent.RESCHEDULE, false));
    }

    @Test
    void rescheduleConfirmedToApprovalRoomBackToPending() {
        assertEquals(ReservationStatus.PENDING, ReservationStateMachine.transition(
                ReservationStatus.CONFIRMED, ReservationEvent.RESCHEDULE, true));
    }

    @Test
    void rescheduleConfirmedToNormalRoomStaysConfirmed() {
        assertEquals(ReservationStatus.CONFIRMED, ReservationStateMachine.transition(
                ReservationStatus.CONFIRMED, ReservationEvent.RESCHEDULE, false));
    }

    // —— 非法迁移：统一抛 RESERVATION_INVALID_STATE ——

    @Test
    void confirmedCannotBeApprovedAgain() {
        assertIllegal(ReservationStatus.CONFIRMED, ReservationEvent.APPROVE);
    }

    @Test
    void confirmedCannotBeRejected() {
        assertIllegal(ReservationStatus.CONFIRMED, ReservationEvent.REJECT);
    }

    @Test
    void rejectedIsTerminalForAllEvents() {
        for (ReservationEvent event : ReservationEvent.values()) {
            if (event == ReservationEvent.RESCHEDULE) {
                assertIllegal(ReservationStatus.REJECTED, event, false);
            } else {
                assertIllegal(ReservationStatus.REJECTED, event);
            }
        }
    }

    @Test
    void cancelledIsTerminalForAllEvents() {
        for (ReservationEvent event : ReservationEvent.values()) {
            if (event == ReservationEvent.RESCHEDULE) {
                assertIllegal(ReservationStatus.CANCELLED, event, true);
            } else {
                assertIllegal(ReservationStatus.CANCELLED, event);
            }
        }
    }

    @Test
    void missingStateIsRejected() {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> ReservationStateMachine.transition(null, ReservationEvent.APPROVE));
        assertEquals(ErrorCode.RESERVATION_INVALID_STATE, exception.getCode());
    }

    @Test
    void rescheduleRequiresApprovalFlagOverload() {
        assertThrows(IllegalArgumentException.class,
                () -> ReservationStateMachine.transition(ReservationStatus.PENDING, ReservationEvent.RESCHEDULE));
    }

    private void assertIllegal(ReservationStatus current, ReservationEvent event) {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> ReservationStateMachine.transition(current, event));
        assertEquals(ErrorCode.RESERVATION_INVALID_STATE, exception.getCode());
    }

    private void assertIllegal(ReservationStatus current, ReservationEvent event, boolean approvalRequired) {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> ReservationStateMachine.transition(current, event, approvalRequired));
        assertEquals(ErrorCode.RESERVATION_INVALID_STATE, exception.getCode());
    }
}
