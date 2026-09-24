/**
 * 文件职责：读取和维护系统级预约开放时间范围。
 * 接口：由 BookingWindowController 与预约域调用。
 * 方法：initialize 确保单例配置行存在；get 获取当前配置；set 校验起止分钟并保存新配置。
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
            // 行被外部删除时回落到默认 06:00-次日06:00，保证预约主流程可用。
            return new BookingWindowResponse(360, 1800);
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
