package com.timeslot.resource.domain;

import com.timeslot.common.api.ErrorCode;
import com.timeslot.common.exception.BusinessException;

public enum MeetingRoomStatus {
    AVAILABLE,
    MAINTENANCE,
    DISABLED;

    public static MeetingRoomStatus fromDb(Integer value) {
        return switch (value == null ? -1 : value) {
            case 1 -> AVAILABLE;
            case 0 -> MAINTENANCE;
            case 2 -> DISABLED;
            default -> throw new BusinessException(ErrorCode.INTERNAL_ERROR, "会议室状态数据无效");
        };
    }
}
