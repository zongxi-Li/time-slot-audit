/**
 * 文件职责：定义 管理员运营 的 MyBatis 数据访问接口，执行查询、插入和更新 SQL。
 * 接口：供本域 Service 使用，不直接暴露 HTTP。
 */
package com.timeslot.administration.mapper;

import com.timeslot.administration.dto.AdminReservationRow;
import com.timeslot.administration.dto.ApprovalRecordResponse;
import com.timeslot.administration.dto.AuditLogResponse;
import com.timeslot.administration.dto.PeakHourResponse;
import com.timeslot.administration.dto.RoomUsageResponse;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface AdministrationMapper {
    String RESERVATION_COLUMNS = """
            SELECT r.id, r.reservation_no, r.room_id, mr.room_name, r.user_id,
                   u.real_name AS user_name, r.title, r.start_time, r.end_time,
                   r.participant_count, r.status, r.remark, r.created_at
            FROM reservation r
            JOIN meeting_room mr ON mr.id = r.room_id
            JOIN sys_user u ON u.id = r.user_id
            """;

    @Select("""
            <script>
            """ + RESERVATION_COLUMNS + """
            <where>
              <if test="status != null and status != ''">r.status = #{status}</if>
            </where>
            ORDER BY r.created_at DESC, r.id DESC
            </script>
            """)
    List<AdminReservationRow> findReservations(@Param("status") String status);

    @Select(RESERVATION_COLUMNS + " WHERE r.id = #{id}")
    AdminReservationRow findReservationById(Long id);

    @Select("""
            SELECT ar.id, ar.reservation_id, ar.approver_id,
                   u.real_name AS approver_name, ar.action, ar.remark, ar.created_at
            FROM approval_record ar
            JOIN sys_user u ON u.id = ar.approver_id
            WHERE ar.reservation_id = #{reservationId}
            ORDER BY ar.created_at DESC, ar.id DESC
            """)
    List<ApprovalRecordResponse> findApprovalHistory(Long reservationId);

    @Insert("""
            INSERT INTO approval_record (reservation_id, approver_id, action, remark)
            VALUES (#{reservationId}, #{approverId}, #{action}, #{remark})
            """)
    int insertApprovalRecord(@Param("reservationId") Long reservationId,
                             @Param("approverId") Long approverId,
                             @Param("action") String action,
                             @Param("remark") String remark);

    @Insert("""
            INSERT INTO operation_log
              (user_id, operation_type, business_type, business_id, content, ip_address)
            VALUES
              (#{userId}, #{operationType}, #{businessType}, #{businessId}, #{content}, #{ipAddress})
            """)
    int insertOperationLog(@Param("userId") Long userId,
                           @Param("operationType") String operationType,
                           @Param("businessType") String businessType,
                           @Param("businessId") Long businessId,
                           @Param("content") String content,
                           @Param("ipAddress") String ipAddress);

    @Select("""
            <script>
            SELECT ol.id, ol.user_id, u.real_name AS operator_name, ol.operation_type,
                   ol.business_type, ol.business_id, ol.content, ol.ip_address, ol.created_at
            FROM operation_log ol
            JOIN sys_user u ON u.id = ol.user_id
            <where>
              <if test="operatorId != null">ol.user_id = #{operatorId}</if>
              <if test="businessType != null and businessType != ''">
                AND ol.business_type = #{businessType}
              </if>
              <if test="start != null">AND ol.created_at &gt;= #{start}</if>
              <if test="end != null">AND ol.created_at &lt; #{end}</if>
            </where>
            ORDER BY ol.created_at DESC, ol.id DESC
            LIMIT #{limit}
            </script>
            """)
    List<AuditLogResponse> findAuditLogs(@Param("operatorId") Long operatorId,
                                         @Param("businessType") String businessType,
                                         @Param("start") LocalDateTime start,
                                         @Param("end") LocalDateTime end,
                                         @Param("limit") int limit);

    @Select("""
            SELECT COUNT(*) FROM reservation
            WHERE start_time < #{end} AND end_time > #{start}
            """)
    long countReservations(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Select("""
            SELECT COUNT(*) FROM reservation
            WHERE status = 'CANCELLED' AND start_time < #{end} AND end_time > #{start}
            """)
    long countCancelledReservations(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Select("""
            SELECT r.room_id, mr.room_name, COUNT(*) AS booking_count,
                   ROUND(SUM(TIMESTAMPDIFF(MINUTE,
                       GREATEST(r.start_time, #{start}), LEAST(r.end_time, #{end}))) / 60.0, 2) AS used_hours
            FROM reservation r
            JOIN meeting_room mr ON mr.id = r.room_id
            WHERE r.status = 'CONFIRMED'
              AND r.start_time < #{end} AND r.end_time > #{start}
            GROUP BY r.room_id, mr.room_name
            ORDER BY used_hours DESC, booking_count DESC, r.room_id
            LIMIT #{limit}
            """)
    List<RoomUsageResponse> findPopularRooms(@Param("start") LocalDateTime start,
                                             @Param("end") LocalDateTime end,
                                             @Param("limit") int limit);

    @Select("""
            SELECT HOUR(start_time) AS hour, COUNT(*) AS booking_count
            FROM reservation
            WHERE status = 'CONFIRMED'
              AND start_time >= #{start} AND start_time < #{end}
            GROUP BY HOUR(start_time)
            ORDER BY booking_count DESC, hour
            """)
    List<PeakHourResponse> findPeakHours(@Param("start") LocalDateTime start,
                                         @Param("end") LocalDateTime end);
}
