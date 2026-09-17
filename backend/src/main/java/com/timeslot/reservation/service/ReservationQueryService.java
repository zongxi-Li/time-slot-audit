/**
 * 文件职责：提供预约域对外的只读查询，避免其他领域直接访问预约表。
 * 接口：被 meeting 和 administration 调用。
 */
package com.timeslot.reservation.service;

import com.timeslot.common.api.ErrorCode;
import com.timeslot.common.exception.BusinessException;
import com.timeslot.reservation.domain.Reservation;
import com.timeslot.reservation.domain.ReservationStatus;
import com.timeslot.reservation.mapper.ReservationMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * reservation 域对外只读查询服务（跨域集成点）。
 *
 * meeting / administration 等域需要预约业务事实（状态、时间、归属、申报人数）时，
 * 必须通过本服务获取，禁止直接读取 reservation 表或 ReservationMapper。
 * 本类只读，不提供任何修改预约状态的能力。
 */
@Service
public class ReservationQueryService {
    private final ReservationMapper reservationMapper;

    public ReservationQueryService(ReservationMapper reservationMapper) {
        this.reservationMapper = reservationMapper;
    }

    /** 按ID获取预约，不存在时抛出 RESOURCE_NOT_FOUND。 */
    @Transactional(readOnly = true)
    public Reservation requireReservation(Long id) {
        Reservation reservation = reservationMapper.findById(id);
        if (reservation == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "预约不存在");
        }
        return reservation;
    }

    /**
     * 查询在 [from, to) 内开始且已确认（CONFIRMED）的预约，用于“即将开始”提醒。
     * 复用日历查询的冲突占用集合语义（PENDING/CONFIRMED），仅保留 CONFIRMED。
     */
    @Transactional(readOnly = true)
    public List<Reservation> findConfirmedStartingBetween(LocalDateTime from, LocalDateTime to) {
        return reservationMapper.findCalendar(from, to, null).stream()
                .filter(r -> r.getStatus() == ReservationStatus.CONFIRMED)
                .filter(r -> !r.getStartTime().isBefore(from))
                .toList();
    }

    /**
     * 查询在回扫窗口内已结束（end_time <= now）且仍为 CONFIRMED 的预约，
     * 用于预约结束后的 No-Show 判定。回扫窗口保证调度重启后可补判。
     */
    @Transactional(readOnly = true)
    public List<Reservation> findConfirmedEndedBefore(LocalDateTime now, int lookbackDays) {
        return reservationMapper.findCalendar(now.minusDays(lookbackDays), now, null).stream()
                .filter(r -> r.getStatus() == ReservationStatus.CONFIRMED)
                .filter(r -> !r.getEndTime().isAfter(now))
                .toList();
    }
}
