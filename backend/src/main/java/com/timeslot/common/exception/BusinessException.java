package com.timeslot.common.exception;

import com.timeslot.common.api.ErrorCode;
import org.springframework.http.HttpStatus;

public class BusinessException extends RuntimeException {
    private final ErrorCode code;
    private final HttpStatus status;

    public BusinessException(ErrorCode code, HttpStatus status, String message) {
        super(message);
        this.code = code;
        this.status = status;
    }

    public BusinessException(ErrorCode code, String message) {
        this(code, defaultStatus(code), message);
    }

    private static HttpStatus defaultStatus(ErrorCode code) {
        return switch (code) {
            case UNAUTHORIZED -> HttpStatus.UNAUTHORIZED;
            case FORBIDDEN -> HttpStatus.FORBIDDEN;
            case RESOURCE_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case ROOM_UNAVAILABLE, RESERVATION_TIME_CONFLICT, RESERVATION_INVALID_STATE, DUPLICATE_REQUEST -> HttpStatus.CONFLICT;
            case VALIDATION_ERROR, ROOM_CAPACITY_EXCEEDED -> HttpStatus.BAD_REQUEST;
            default -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }

    public ErrorCode getCode() {
        return code;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
