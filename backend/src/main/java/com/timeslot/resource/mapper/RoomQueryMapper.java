/**
 * 文件职责：定义 会议室条件筛选与时段空闲查询 的 MyBatis 数据访问接口，执行只读查询 SQL。
 * 接口：供 ResourceQueryService 使用，不直接暴露 HTTP。
 */
package com.timeslot.resource.mapper;

import com.timeslot.resource.mapper.ResourceMapper.RoomRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 独立于 ResourceMapper 的只读查询入口：ResourceMapper 承担台账写路径，
 * 这里集中承载列表筛选与空闲判定的查询语义，互不影响。
 */
@Mapper
public interface RoomQueryMapper {

    /**
     * 按 位置模糊 / 最小容量 / 设施名 / 状态 筛选会议室，任一条件传 NULL 即忽略该条件。
     * 设施用 EXISTS 子查询匹配（room_facility 一行一设施），避免依赖 GROUP_CONCAT 后再过滤；
     * facilities_csv 仍按台账口径聚合，供行转域对象复用。
     */
    @Select("""
            <script>
            SELECT r.id, r.category_id, r.room_name, r.location, r.capacity, r.status, r.description,
                   c.category_name,
                   GROUP_CONCAT(f.facility_name ORDER BY f.id SEPARATOR ',') AS facilities_csv
            FROM meeting_room r
            JOIN room_category c ON c.id = r.category_id
            LEFT JOIN room_facility f ON f.room_id = r.id
            WHERE (#{location} IS NULL OR r.location LIKE CONCAT('%', #{location}, '%'))
              AND (#{minCapacity} IS NULL OR r.capacity &gt;= #{minCapacity})
              AND (#{facility} IS NULL OR EXISTS (
                    SELECT 1 FROM room_facility ff
                    WHERE ff.room_id = r.id AND ff.facility_name LIKE CONCAT('%', #{facility}, '%')))
              AND (#{status} IS NULL OR r.status = #{status})
            GROUP BY r.id, r.category_id, r.room_name, r.location, r.capacity, r.status, r.description, c.category_name
            ORDER BY r.room_name
            </script>
            """)
    List<RoomRow> searchRooms(@Param("location") String location,
                              @Param("minCapacity") Integer minCapacity,
                              @Param("facility") String facility,
                              @Param("status") Integer status);

    /**
     * 与给定时段重叠的有效预约（PENDING/CONFIRMED）占用的会议室 id 集合。
     * 半开区间判定与预约冲突查询同语义：start_time &lt; endTime AND end_time &gt; startTime，
     * 结束时间等于另一预约的开始时间不算占用。
     */
    @Select("""
            SELECT DISTINCT room_id
            FROM reservation
            WHERE status IN ('PENDING', 'CONFIRMED')
              AND start_time < #{endTime}
              AND end_time > #{startTime}
            """)
    List<Long> findOccupiedRoomIds(@Param("startTime") LocalDateTime startTime,
                                   @Param("endTime") LocalDateTime endTime);
}
