/**
 * 文件职责：定义会议室领域状态，并隔离数据库状态编码。
 * 接口：供本域 Service、Mapper 和 DTO 转换使用。
 * 方法：fromDb 将数据库值解析为领域状态；toDb 将领域状态转换为数据库值。
*/

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

    public int toDb() {
        return switch (this) {
            case AVAILABLE -> 1;
            case MAINTENANCE -> 0;
            case DISABLED -> 2;
        };
    }
}
