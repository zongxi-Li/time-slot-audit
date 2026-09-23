/**
 * 文件职责：用户通知数据的 MyBatis 查询和状态更新接口。
 * 接口：供本域 Service 使用，不直接暴露 HTTP。
 * 方法：insertIgnore 写入通知并避免重复；findByUser 查询用户通知；countUnread 统计未读数；markRead/markAllRead 更新单条或全部已读状态。
*/

package com.timeslot.meeting.mapper;

import com.timeslot.meeting.domain.Notification;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
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

    /* is_read 驼峰映射后的属性名是 isRead，与实体的 read 对不上，MyBatis 会静默跳过导致 read 恒为 false，需显式映射 */
    /** 查询用户通知，可选仅看未读项，最多返回最近 100 条。 */
    @Results(@Result(property = "read", column = "is_read"))
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

    /** 统计指定用户尚未阅读的通知数量。 */
    @Select("SELECT COUNT(*) FROM notification WHERE user_id = #{userId} AND is_read = 0")
    int countUnread(Long userId);

    /** 将指定用户的一条未读通知标记为已读；非本人通知不会被更新。 */
    @Update("""
            UPDATE notification
            SET is_read = 1, read_at = #{readAt}
            WHERE id = #{id} AND user_id = #{userId} AND is_read = 0
            """)
    int markRead(@Param("id") Long id, @Param("userId") Long userId, @Param("readAt") LocalDateTime readAt);

    /** 将指定用户的全部未读通知标记为已读。 */
    @Update("""
            UPDATE notification
            SET is_read = 1, read_at = #{readAt}
            WHERE user_id = #{userId} AND is_read = 0
            """)
    int markAllRead(@Param("userId") Long userId, @Param("readAt") LocalDateTime readAt);
}
