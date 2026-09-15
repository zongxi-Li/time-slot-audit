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
            SELECT COUNT(*)
            FROM reservation
            WHERE room_id = #{roomId}
              AND status IN ('PENDING', 'CONFIRMED')
              AND start_time < #{endTime}
              AND end_time > #{startTime}
            """)
    int countConflicts(@Param("roomId") Long roomId, @Param("startTime") LocalDateTime startTime,
                       @Param("endTime") LocalDateTime endTime);

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

    @Update("""
            UPDATE reservation
            SET status = #{status}, cancel_reason = #{reason}
            WHERE id = #{id}
            """)
    int updateStatus(@Param("id") Long id, @Param("status") String status, @Param("reason") String reason);
}
