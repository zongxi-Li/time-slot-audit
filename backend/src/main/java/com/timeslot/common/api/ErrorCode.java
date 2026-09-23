/**
 * 文件职责：集中定义业务错误码，供异常处理和 API 响应统一使用。
 * 接口：映射 HTTP 错误响应。
 * 方法：无显式业务方法；枚举常量代表可识别的错误类型。
*/

package com.timeslot.common.api;

public enum ErrorCode {
    SUCCESS,
    VALIDATION_ERROR,
    UNAUTHORIZED,
    FORBIDDEN,
    RESOURCE_NOT_FOUND,
    ROOM_UNAVAILABLE,
    ROOM_CAPACITY_EXCEEDED,
    ROOM_DELETE_BLOCKED,
    RESERVATION_TIME_CONFLICT,
    RESERVATION_INVALID_STATE,
    DUPLICATE_REQUEST,
    INTERNAL_ERROR
}
