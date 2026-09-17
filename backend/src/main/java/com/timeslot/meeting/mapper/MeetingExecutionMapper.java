/**
 * 文件职责：定义 会议执行 的 MyBatis 数据访问接口，执行查询、插入和更新 SQL。
 * 接口：供本域 Service 使用，不直接暴露 HTTP。
 */
package com.timeslot.meeting.mapper;

import com.timeslot.meeting.domain.Attendee;
import com.timeslot.meeting.domain.MeetingExecution;
import com.timeslot.meeting.domain.UserRef;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

/**
 * meeting 域执行数据访问。写操作仅作用于 reservation_attendee；
 * 对 sys_user / reservation / meeting_room 只做只读 JOIN 用于解析与展示，
 * 预约业务事实（状态、时间、归属）一律通过 reservation 域公开查询服务获取。
 */
@Mapper
public interface MeetingExecutionMapper {
    String ATTENDEE_COLUMNS = """
            a.id, a.reservation_id, a.user_id, a.attendee_role, a.attendance_status,
            a.check_in_at, a.check_out_at, a.created_at, u.username, u.real_name
            FROM reservation_attendee a
            JOIN sys_user u ON u.id = a.user_id
            """;

    // ---------------------------------------------------------------------
    // 参与人写入（仅 reservation_attendee）
    // ---------------------------------------------------------------------

    /** 幂等插入组织者行（INSERT IGNORE，重复触达无副作用）。 */
    @Insert("""
            INSERT IGNORE INTO reservation_attendee (reservation_id, user_id, attendee_role)
            VALUES (#{reservationId}, #{userId}, 'ORGANIZER')
            """)
    int insertOrganizerIgnore(@Param("reservationId") Long reservationId, @Param("userId") Long userId);

    /** 正式插入参与人行；唯一键 uk_attendee_reservation_user 是防重最终防线。 */
    @Insert("""
            INSERT INTO reservation_attendee (reservation_id, user_id, attendee_role)
            VALUES (#{reservationId}, #{userId}, 'ATTENDEE')
            """)
    int insertAttendee(@Param("reservationId") Long reservationId, @Param("userId") Long userId);

    @Delete("DELETE FROM reservation_attendee WHERE id = #{id}")
    int deleteById(Long id);

    /** 仅 EXPECTED 行允许状态推进，SQL 条件兜底防止并发越权转换。 */
    @Update("""
            UPDATE reservation_attendee
            SET attendance_status = 'CHECKED_IN', check_in_at = #{time}
            WHERE id = #{id} AND attendance_status = 'EXPECTED'
            """)
    int markCheckedIn(@Param("id") Long id, @Param("time") LocalDateTime time);

    @Update("""
            UPDATE reservation_attendee
            SET attendance_status = 'CHECKED_OUT', check_out_at = #{time}
            WHERE id = #{id} AND attendance_status = 'CHECKED_IN'
            """)
    int markCheckedOut(@Param("id") Long id, @Param("time") LocalDateTime time);

    /** 批量判定 No-Show：仅命中 EXPECTED，重复执行影响行数为 0，天然幂等。 */
    @Update("""
            UPDATE reservation_attendee
            SET attendance_status = 'NO_SHOW'
            WHERE reservation_id = #{reservationId} AND attendance_status = 'EXPECTED'
            """)
    int markNoShowForExpected(Long reservationId);

    // ---------------------------------------------------------------------
    // 参与人查询
    // ---------------------------------------------------------------------

    @Select("""
            SELECT
            """ + ATTENDEE_COLUMNS + """
            WHERE a.reservation_id = #{reservationId}
            ORDER BY a.id
            """)
    List<Attendee> findByReservationId(Long reservationId);

    @Select("""
            SELECT
            """ + ATTENDEE_COLUMNS + """
            WHERE a.reservation_id = #{reservationId} AND a.user_id = #{userId}
            """)
    Attendee findRow(@Param("reservationId") Long reservationId, @Param("userId") Long userId);

    @Select("SELECT COUNT(*) FROM reservation_attendee WHERE reservation_id = #{reservationId}")
    int countByReservationId(Long reservationId);

    @Select("""
            SELECT a.reservation_id, r.reservation_no, r.title, r.room_id, mr.room_name,
                   r.status AS reservation_status, r.start_time, r.end_time,
                   a.attendee_role, a.attendance_status, a.check_in_at, a.check_out_at
            FROM reservation_attendee a
            JOIN reservation r ON r.id = a.reservation_id
            JOIN meeting_room mr ON mr.id = r.room_id
            WHERE a.user_id = #{userId}
            ORDER BY r.start_time DESC
            """)
    List<MeetingExecution> findMyMeetings(Long userId);

    // ---------------------------------------------------------------------
    // 跨域只读解析（identity：参与人身份解析，不写 sys_user）
    // ---------------------------------------------------------------------

    @Select("SELECT id AS user_id, username, real_name FROM sys_user WHERE id = #{userId}")
    UserRef findUserById(Long userId);

    @Select("SELECT id AS user_id, username, real_name FROM sys_user WHERE username = #{username}")
    UserRef findUserByUsername(String username);
}
