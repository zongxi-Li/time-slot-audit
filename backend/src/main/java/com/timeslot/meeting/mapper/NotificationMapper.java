/**
 * 文件职责：定义 会议执行 的 MyBatis 数据访问接口，执行查询、插入和更新 SQL。
 * 接口：供本域 Service 使用，不直接暴露 HTTP。
 */
package com.timeslot.meeting.mapper;

import com.timeslot.meeting.domain.Notification;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

/** meeting 域个人通知数据访问，写操作仅作用于 notification 表。 */
@Mapper
public interface NotificationMapper {

    /** 幂等写入：uk_notification_dedup 命中时忽略，返回 0 表示重复业务事实。 */
    @Insert("""
            INSERT IGNORE INTO notification
              (user_id, type, title, content, reservation_id, dedup_key)
            VALUES
              (#{userId}, #{type}, #{title}, #{content}, #{reservationId}, #{dedupKey})
            """)
    int insertIgnore(Notification notification);

    @Select("""
            SELECT id, user_id, type, title, content, reservation_id, dedup_key,
                   is_read, read_at, created_at
            FROM notification
            WHERE user_id = #{userId}
              AND (#{unreadOnly} = 0 OR is_read = 0)
            ORDER BY created_at DESC, id DESC
            LIMIT 100
            """)
    List<Notification> findByUser(@Param("userId") Long userId, @Param("unreadOnly") boolean unreadOnly);

    @Select("SELECT COUNT(*) FROM notification WHERE user_id = #{userId} AND is_read = 0")
    int countUnread(Long userId);

    /** 归属校验在 SQL 内完成，非本人通知影响行数为 0。 */
    @Update("""
            UPDATE notification
            SET is_read = 1, read_at = #{readAt}
            WHERE id = #{id} AND user_id = #{userId} AND is_read = 0
            """)
    int markRead(@Param("id") Long id, @Param("userId") Long userId, @Param("readAt") LocalDateTime readAt);

    @Update("""
            UPDATE notification
            SET is_read = 1, read_at = #{readAt}
            WHERE user_id = #{userId} AND is_read = 0
            """)
    int markAllRead(@Param("userId") Long userId, @Param("readAt") LocalDateTime readAt);
}
