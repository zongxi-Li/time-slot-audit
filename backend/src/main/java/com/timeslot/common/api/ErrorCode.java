/**
 * 文件职责：定义后端统一错误码，供业务异常和全局异常处理器使用。
 * 接口：映射 HTTP 错误响应。
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
