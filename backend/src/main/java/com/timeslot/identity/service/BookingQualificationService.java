/**
 * 文件职责：根据用户状态、信用分和限制期判断预约资格。
 * 接口：被 ReservationService 调用。
 */
package com.timeslot.identity.service;

import com.timeslot.identity.domain.BookingQualification;
import com.timeslot.identity.domain.CreditRules;
import com.timeslot.identity.domain.User;
import com.timeslot.identity.mapper.UserMapper;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;

/**
 * 预约资格判定 —— identity 域对外暴露的只读公开能力。
 * reservation 域在创建预约前调用 check(userId)，不得直接访问 identity 的 Mapper 或数据表。
 * 判定顺序：账号禁用 -> 限制期/黑名单 -> 信用不足 -> 正常。
 */
@Service
public class BookingQualificationService {
    private final UserMapper userMapper;
    private final Clock clock;

    public BookingQualificationService(UserMapper userMapper, Clock clock) {
        this.userMapper = userMapper;
        this.clock = clock;
    }

    public BookingQualification check(Long userId) {
        User user = userMapper.findById(userId);
        if (user == null) {
            return BookingQualification.deny(userId, "用户不存在", null, null);
        }
        if (!user.enabled()) {
            return BookingQualification.deny(userId, "账号已被禁用，请联系管理员", user.creditScore(), user.restrictedUntil());
        }
        LocalDateTime now = LocalDateTime.now(clock);
        if (user.restrictedUntil() != null && user.restrictedUntil().isAfter(now)) {
            return BookingQualification.deny(userId,
                    "账号处于限制期（黑名单），至 " + BookingQualification.format(user.restrictedUntil()),
                    user.creditScore(), user.restrictedUntil());
        }
        if (user.creditScore() == null || user.creditScore() < CreditRules.MIN_BOOKING_CREDIT_SCORE) {
            return BookingQualification.deny(userId,
                    "信用分不足（当前 " + user.creditScore() + " 分，需不低于 "
                            + CreditRules.MIN_BOOKING_CREDIT_SCORE + " 分）",
                    user.creditScore(), user.restrictedUntil());
        }
        return BookingQualification.allow(userId, user.creditScore());
    }
}
