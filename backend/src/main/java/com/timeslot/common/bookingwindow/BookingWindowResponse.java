package com.timeslot.common.bookingwindow;

/**
 * 全局可预约时段（管理员可调）。分钟数相对预约日期 00:00 计；
 * endMinute 超过 1440 表示延伸到次日（如 480-1920 = 08:00 至次日 08:00）。
 */
public record BookingWindowResponse(int startMinute, int endMinute) {

    public String startLabel() {
        return label(startMinute);
    }

    public String endLabel() {
        return label(endMinute);
    }

    private static String label(int minute) {
        int hour = minute / 60;
        String hm = String.format("%02d:%02d", hour % 24, minute % 60);
        return hour >= 24 ? "次日 " + hm : hm;
    }
}
