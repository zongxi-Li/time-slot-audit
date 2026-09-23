/**
 * 文件职责：reservation 领域用于登记新预约初始参与人的抽象端口。
 * 接口：由 reservation 域在创建预约时调用，实现由 meeting 域提供。
 *
 * 设计动机：reservation_attendee 属于 meeting 执行域，且 meeting 已单向依赖 reservation；
 * 若 reservation 直接写参与人表会跨域写他人领域数据并形成模块级循环依赖。
 * 与 {@link ReservationNotificationPort} 同构：本模块仅持接口，实现由适配器注入。
 * 方法：attachInitialAttendees 将预约及参与人 ID 列表交由 meeting 领域适配实现。
 */

package com.timeslot.reservation.spi;

import com.timeslot.reservation.domain.Reservation;

import java.util.List;

public interface ReservationAttendeePort {

    /**
     * 为一场新预约批量登记初始参与人（不含创建人，组织者行由实现方幂等补齐）。
     * 实现方负责：用户存在性校验、申报人数上限校验（含组织者）、逐人「被加入会议」通知；
     * 在调用方事务内执行，任一用户校验失败则整场预约创建回滚。
     */
    void attachInitialAttendees(Reservation reservation, List<Long> userIds);
}
