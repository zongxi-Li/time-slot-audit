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
    @Select("""
            SELECT r.id, r.request_id, r.reservation_no, r.room_id, r.user_id,
                   mr.room_name, u.real_name AS user_name, r.title, r.start_time, r.end_time,
                   r.participant_count, r.status, r.remark
            FROM reservation r
            JOIN meeting_room mr ON mr.id = r.room_id
            JOIN sys_user u ON u.id = r.user_id
            WHERE r.user_id = #{userId} AND r.request_id = #{requestId}
            """)
    Reservation findByUserIdAndRequestId(@Param("userId") Long userId, @Param("requestId") String requestId);

    @Select("""
            SELECT r.id, r.request_id, r.reservation_no, r.room_id, r.user_id,
                   mr.room_name, u.real_name AS user_name, r.title, r.start_time, r.end_time,
                   r.participant_count, r.status, r.remark
            FROM reservation r
            JOIN meeting_room mr ON mr.id = r.room_id
            JOIN sys_user u ON u.id = r.user_id
            WHERE r.id = #{id}
            """)
    Reservation findById(Long id);

    @Select("""
            SELECT r.id, r.request_id, r.reservation_no, r.room_id, r.user_id,
                   mr.room_name, u.real_name AS user_name, r.title, r.start_time, r.end_time,
                   r.participant_count, r.status, r.remark
            FROM reservation r
            JOIN meeting_room mr ON mr.id = r.room_id
            JOIN sys_user u ON u.id = r.user_id
            WHERE r.id = #{id}
            FOR UPDATE
            """)
    Reservation findByIdForUpdate(Long id);

    @Select("""
            SELECT COUNT(*)
            FROM reservation
            WHERE room_id = #{roomId}
              AND status IN ('PENDING', 'CONFIRMED')
              AND start_time < #{endTime}
              AND end_time > #{startTime}
            """)
    int countConflicts(@Param("roomId") Long roomId, @Param("startTime") LocalDateTime startTime,
                       @Param("endTime") LocalDateTime endTime);

    @Select("""
            SELECT COUNT(*)
            FROM reservation
            WHERE room_id = #{roomId}
              AND status IN ('PENDING', 'CONFIRMED')
              AND id != #{excludeId}
              AND start_time < #{endTime}
              AND end_time > #{startTime}
            """)
    int countConflictsExcluding(@Param("roomId") Long roomId, @Param("startTime") LocalDateTime startTime,
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
                status = #{reservation.status}
            WHERE id = #{reservation.id}
              AND status = #{expectedStatus}
            """)
    int updateScheduleAndStatus(@Param("reservation") Reservation reservation,
                                @Param("expectedStatus") String expectedStatus);

    @Insert("""
            INSERT INTO reservation
              (request_id, reservation_no, room_id, user_id, title, start_time, end_time,
               participant_count, status, remark)
            VALUES
              (#{requestId}, #{reservationNo}, #{roomId}, #{userId}, #{title}, #{startTime}, #{endTime},
               #{participantCount}, #{status}, #{remark})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Reservation reservation);

    @Select("""
            SELECT r.id, r.request_id, r.reservation_no, r.room_id, r.user_id,
                   mr.room_name, u.real_name AS user_name, r.title, r.start_time, r.end_time,
                   r.participant_count, r.status, r.remark
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
                   r.participant_count, r.status, r.remark
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
            SET status = #{targetStatus}
            WHERE id = #{id}
              AND status = #{expectedStatus}
            """)
    int transitionStatusExpected(@Param("id") Long id, @Param("expectedStatus") String expectedStatus,
                                 @Param("targetStatus") String targetStatus);

    /** 取消类迁移（OWNER_CANCEL / FORCE_CANCEL）：status 与 cancel_reason 同一条 UPDATE 写入。 */
    @Update("""
            UPDATE reservation
            SET status = #{targetStatus}, cancel_reason = #{reason}
            WHERE id = #{id}
              AND status = #{expectedStatus}
            """)
    int cancelExpected(@Param("id") Long id, @Param("expectedStatus") String expectedStatus,
                       @Param("targetStatus") String targetStatus, @Param("reason") String reason);
}
