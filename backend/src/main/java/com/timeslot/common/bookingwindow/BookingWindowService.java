/**
 * 文件职责：提供全局可预约时段的读取与管理员设置，供预约校验与管理端接口使用。
 * 接口：由 BookingWindowController 与预约域调用。
 */
package com.timeslot.common.bookingwindow;

import com.timeslot.common.api.ErrorCode;
import com.timeslot.common.exception.BusinessException;
import jakarta.annotation.PostConstruct;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BookingWindowService {
    private final BookingWindowMapper mapper;

    public BookingWindowService(BookingWindowMapper mapper) {
        this.mapper = mapper;
    }

    @PostConstruct
    void initialize() {
        mapper.ensureConfigRow();
    }

    public BookingWindowResponse get() {
        BookingWindowResponse window = mapper.findWindow();
        if (window == null) {
            // 行被外部删除时 fail-open 回落到默认 08:00-次日08:00，保证预约主流程可用。
            return new BookingWindowResponse(480, 1920);
        }
        return window;
    }

    @Transactional
    public BookingWindowResponse set(int startMinute, int endMinute) {
        if (startMinute % 60 != 0 || endMinute % 60 != 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, HttpStatus.BAD_REQUEST,
                    "可预约时段必须按整点设置");
        }
        if (endMinute <= startMinute || endMinute - startMinute > 24 * 60) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, HttpStatus.BAD_REQUEST,
                    "可预约时段长度必须在 1 小时到 24 小时之间");
        }
        mapper.saveWindow(startMinute, endMinute);
        return get();
    }
}
