package com.timeslot.common.time;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;

@Mapper
public interface SystemTimeMapper {
    /** 初始化唯一的系统时间配置行；已有配置时不覆盖。 */
    @Insert("""
            INSERT IGNORE INTO system_time_config (id, fixed_time)
            VALUES (1, NULL)
            """)
    int ensureConfigRow();

    /** 读取系统固定时间；返回 NULL 时由业务逻辑使用真实时钟。 */
    @Select("""
            SELECT fixed_time
            FROM system_time_config
            WHERE id = 1
            """)
    LocalDateTime findFixedTime();

    /** 保存或更新系统固定时间，配置行不存在时同时创建。 */
    @Insert("""
            INSERT INTO system_time_config (id, fixed_time)
            VALUES (1, #{fixedTime,jdbcType=TIMESTAMP})
            ON DUPLICATE KEY UPDATE fixed_time = VALUES(fixed_time)
            """)
    int saveFixedTime(@Param("fixedTime") LocalDateTime fixedTime);
}
