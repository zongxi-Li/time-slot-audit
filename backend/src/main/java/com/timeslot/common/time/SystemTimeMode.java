/**
 * 文件职责：标识业务时钟当前采用实时模式还是固定模拟时间模式。
 * 方法：无显式业务方法；枚举常量表示时钟运行模式。
 */
package com.timeslot.common.time;

public enum SystemTimeMode {
    REALTIME,
    FIXED
}
