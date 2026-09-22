/**
 * 文件职责：定义 预约核心 的 MyBatis 数据访问接口，执行查询、插入和更新 SQL。
 * 接口：供本域 Service 使用，不直接暴露 HTTP。
 */
package com.timeslot.reservation.mapper;

import com.timeslot.reservation.domain.Reservation;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface ReservationMapper {
    /** 未结束的有效预约数（PENDING/CONFIRMED 且 end_time > now）：供会议室删除保护使用。 */
    @Select("""
            SELECT COUNT(*)
            FROM reservation
            WHERE room_id = #{roomId}
              AND status IN ('PENDING', 'CONFIRMED')
              AND end_time > #{now}
            """)
    int countFutureActiveReservations(@Param("roomId") Long roomId, @Param("now") LocalDateTime now);

    /** 全状态预约数：历史预约也必须阻止会议室物理删除，以保持使用记录与 FK 完整性。 */
    @Select("""
            SELECT COUNT(*)
            FROM reservation
            WHERE room_id = #{roomId}
            """)
    int countAllReservationsByRoomId(@Param("roomId") Long roomId);

    @Select("""
            SELECT r.id, r.request_id, r.reservation_no, r.room_id, r.user_id,
                   mr.room_name, u.real_name AS user_name, r.title, r.start_time, r.end_time,
                   r.participant_count, r.status, r.remark, r.version
            FROM reservation r
            JOIN meeting_room mr ON mr.id = r.room_id
            JOIN sys_user u ON u.id = r.user_id
            WHERE r.user_id = #{userId} AND r.request_id = #{requestId}
            """)
    Reservation findByUserIdAndRequestId(@Param("userId") Long userId, @Param("requestId") String requestId);

    @Select("""
            SELECT r.id, r.request_id, r.reservation_no, r.room_id, r.user_id,
                   mr.room_name, u.real_name AS user_name, r.title, r.start_time, r.end_time,
                   r.participant_count, r.status, r.remark, r.version
            FROM reservation r
            JOIN meeting_room mr ON mr.id = r.room_id
            JOIN sys_user u ON u.id = r.user_id
            WHERE r.id = #{id}
            """)
    Reservation findById(Long id);

    @Select("""
            SELECT r.id, r.request_id, r.reservation_no, r.room_id, r.user_id,
                   mr.room_name, u.real_name AS user_name, r.title, r.start_time, r.end_time,
                   r.participant_count, r.status, r.remark, r.version
            FROM reservation r
            JOIN meeting_room mr ON mr.id = r.room_id
            JOIN sys_user u ON u.id = r.user_id
            WHERE r.id = #{id}
            FOR UPDATE
            """)
    Reservation findByIdForUpdate(Long id);

    /**
     * 查询与给定时段重叠的第一条有效预约（PENDING/CONFIRMED）。半开区间判定：
     * start_time < endTime AND end_time > startTime，结束等于开始不算冲突。
     * 冲突提示必须说明冲突会议主题与占用时段，因此查出整行而非 COUNT；
     * {@code excludeId} 供改期排除自身，创建路径传 NULL。
     */
    @Select("""
            SELECT r.id, r.request_id, r.reservation_no, r.room_id, r.user_id,
                   mr.room_name, u.real_name AS user_name, r.title, r.start_time, r.end_time,
                   r.participant_count, r.status, r.remark, r.version
            FROM reservation r
            JOIN meeting_room mr ON mr.id = r.room_id
            JOIN sys_user u ON u.id = r.user_id
            WHERE r.room_id = #{roomId}
              AND r.status IN ('PENDING', 'CONFIRMED')
              AND r.start_time < #{endTime}
              AND r.end_time > #{startTime}
              AND (#{excludeId} IS NULL OR r.id != #{excludeId})
            ORDER BY r.start_time
            LIMIT 1
            """)
    Reservation findFirstConflict(@Param("roomId") Long roomId, @Param("startTime") LocalDateTime startTime,
                                  @Param("endTime") LocalDateTime endTime, @Param("excludeId") Long excludeId);

    /**
     * 改期必须把预约字段和重算后的状态在同一个 UPDATE 中原子持久化；
     * expected-state 条件保证并发下状态被他人变更时 affectedRows == 0，调用方必须 fail-closed。
     */
    @Update("""
            UPDATE reservation
            SET room_id = #{reservation.roomId}, title = #{reservation.title},
                start_time = #{reservation.startTime}, end_time = #{reservation.endTime},
                participant_count = #{reservation.participantCount}, remark = #{reservation.remark},
                status = #{reservation.status}, version = version + 1
            WHERE id = #{reservation.id}
              AND status = #{expectedStatus}
              AND version = #{expectedVersion}
            """)
    int updateScheduleAndStatus(@Param("reservation") Reservation reservation,
                                @Param("expectedStatus") String expectedStatus,
                                @Param("expectedVersion") int expectedVersion);

    @Insert("""
            INSERT INTO reservation
              (request_id, reservation_no, room_id, user_id, title, start_time, end_time,
               participant_count, status, remark, version)
            VALUES
              (#{requestId}, #{reservationNo}, #{roomId}, #{userId}, #{title}, #{startTime}, #{endTime},
               #{participantCount}, #{status}, #{remark}, #{version})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Reservation reservation);

    @Select("""
            SELECT r.id, r.request_id, r.reservation_no, r.room_id, r.user_id,
                   mr.room_name, u.real_name AS user_name, r.title, r.start_time, r.end_time,
                   r.participant_count, r.status, r.remark, r.version
            FROM reservation r
            JOIN meeting_room mr ON mr.id = r.room_id
            JOIN sys_user u ON u.id = r.user_id
            WHERE r.user_id = #{userId}
            ORDER BY r.start_time DESC
            """)
    List<Reservation> findByUserId(Long userId);

    @Select("""
            SELECT r.id, r.request_id, r.reservation_no, r.room_id, r.user_id,
                   mr.room_name, u.real_name AS user_name, r.title, r.start_time, r.end_time,
                   r.participant_count, r.status, r.remark, r.version
            FROM reservation r
            JOIN meeting_room mr ON mr.id = r.room_id
            JOIN sys_user u ON u.id = r.user_id
            WHERE r.status IN ('PENDING', 'CONFIRMED')
              AND r.start_time < #{endTime}
              AND r.end_time > #{startTime}
              AND (#{roomId} IS NULL OR r.room_id = #{roomId})
            ORDER BY r.start_time, r.room_id
            """)
    List<Reservation> findCalendar(@Param("startTime") LocalDateTime startTime,
                                   @Param("endTime") LocalDateTime endTime,
                                   @Param("roomId") Long roomId);

    /**
     * 审批类状态迁移（APPROVE / REJECT）：只更新 status，绝不触碰 cancel_reason——
     * 驳回理由属于 administration 的 approval_record.remark，取消原因才写 cancel_reason。
     */
    @Update("""
            UPDATE reservation
            SET status = #{targetStatus}, version = version + 1
            WHERE id = #{id}
              AND status = #{expectedStatus}
            """)
    int transitionStatusExpected(@Param("id") Long id, @Param("expectedStatus") String expectedStatus,
                                 @Param("targetStatus") String targetStatus);

    /** 取消类迁移（OWNER_CANCEL / FORCE_CANCEL）：status 与 cancel_reason 同一条 UPDATE 写入。 */
    @Update("""
            UPDATE reservation
            SET status = #{targetStatus}, cancel_reason = #{reason}, version = version + 1
            WHERE id = #{id}
              AND status = #{expectedStatus}
            """)
    int cancelExpected(@Param("id") Long id, @Param("expectedStatus") String expectedStatus,
                       @Param("targetStatus") String targetStatus, @Param("reason") String reason);

    /**
     * 审批超时候选：已结束（end_time <= now）仍处于 PENDING 的预约。
     * 不设回扫下限：调度中断造成的历史积压也要一次清完，此后每轮增量趋近于零。
     */
    @Select("""
            SELECT r.id, r.request_id, r.reservation_no, r.room_id, r.user_id,
                   mr.room_name, u.real_name AS user_name, r.title, r.start_time, r.end_time,
                   r.participant_count, r.status, r.remark, r.version
            FROM reservation r
            JOIN meeting_room mr ON mr.id = r.room_id
            JOIN sys_user u ON u.id = r.user_id
            WHERE r.status = 'PENDING'
              AND r.end_time <= #{now}
            ORDER BY r.end_time
            """)
    List<Reservation> findPendingEndedBefore(@Param("now") LocalDateTime now);

    /**
     * 审批超时自动失效：状态机 REJECT（PENDING -> REJECTED）的系统调度版。
     * status + end_time 双重守卫：重复调度幂等，且不会误伤候选读取后刚被改期到未来的预约；
     * 命中返回 1，被并发操作抢先或已改期则返回 0，调用方静默跳过。
     */
    @Update("""
            UPDATE reservation
            SET status = 'REJECTED', version = version + 1
            WHERE id = #{id}
              AND status = 'PENDING'
              AND end_time <= #{now}
            """)
    int expirePendingEnded(@Param("id") Long id, @Param("now") LocalDateTime now);
}
