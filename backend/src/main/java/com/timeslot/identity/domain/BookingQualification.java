package com.timeslot.identity.domain;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 预约资格判定结论（identity 域对 reservation 域的公开只读契约）。
 * eligible=false 时 reason 必须给出面向用户的原因。
 */
public record BookingQualification(Long userId, boolean eligible, String reason, Integer creditScore,
                                   LocalDateTime restrictedUntil) {
    private static final DateTimeFormatter DISPLAY = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    public static BookingQualification allow(Long userId, Integer creditScore) {
        return new BookingQualification(userId, true, null, creditScore, null);
    }

    public static BookingQualification deny(Long userId, String reason, Integer creditScore,
                                            LocalDateTime restrictedUntil) {
        return new BookingQualification(userId, false, reason, creditScore, restrictedUntil);
    }

    public String formattedRestrictedUntil() {
        return format(restrictedUntil);
    }

    public static String format(LocalDateTime restrictedUntil) {
        return restrictedUntil == null ? null : DISPLAY.format(restrictedUntil);
    }
}
