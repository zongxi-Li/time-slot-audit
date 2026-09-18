package com.timeslot.common.bookingwindow;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface BookingWindowMapper {

    @Insert("""
            INSERT IGNORE INTO booking_window_config (id, start_minute, end_minute)
            VALUES (1, 480, 1920)
            """)
    int ensureConfigRow();

    @Select("""
            SELECT start_minute, end_minute
            FROM booking_window_config
            WHERE id = 1
            """)
    BookingWindowResponse findWindow();

    @Update("""
            UPDATE booking_window_config
            SET start_minute = #{startMinute}, end_minute = #{endMinute}
            WHERE id = 1
            """)
    int saveWindow(@Param("startMinute") int startMinute, @Param("endMinute") int endMinute);
}
