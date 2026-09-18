package com.timeslot.common.time;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;

@Mapper
public interface SystemTimeMapper {
    @Insert("""
            INSERT IGNORE INTO system_time_config (id, fixed_time)
            VALUES (1, NULL)
            """)
    int ensureConfigRow();

    @Select("""
            SELECT fixed_time
            FROM system_time_config
            WHERE id = 1
            """)
    LocalDateTime findFixedTime();

    @Insert("""
            INSERT INTO system_time_config (id, fixed_time)
            VALUES (1, #{fixedTime,jdbcType=TIMESTAMP})
            ON DUPLICATE KEY UPDATE fixed_time = VALUES(fixed_time)
            """)
    int saveFixedTime(@Param("fixedTime") LocalDateTime fixedTime);
}
