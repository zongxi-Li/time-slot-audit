/**
 * 文件职责：预约开放时间窗口的 MyBatis 数据访问接口。
 * 方法：ensureConfigRow 确保单例配置行存在；findWindow 读取预约开放窗口；saveWindow 写入或更新窗口。
 */
package com.timeslot.common.bookingwindow;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface BookingWindowMapper {

    /** 初始化唯一的预约时间窗口配置行；已有设置时保持不变。 */
    @Insert("""
            INSERT IGNORE INTO booking_window_config (id, start_minute, end_minute)
            VALUES (1, 360, 1800)
            """)
    int ensureConfigRow();

    /** 读取当前可预约时间窗口的起止分钟数。 */
    @Select("""
            SELECT start_minute, end_minute
            FROM booking_window_config
            WHERE id = 1
            """)
    BookingWindowResponse findWindow();

    /** 更新预约时间窗口的开始和结束分钟数。 */
    @Update("""
            UPDATE booking_window_config
            SET start_minute = #{startMinute}, end_minute = #{endMinute}
            WHERE id = 1
            """)
    int saveWindow(@Param("startMinute") int startMinute, @Param("endMinute") int endMinute);
}
