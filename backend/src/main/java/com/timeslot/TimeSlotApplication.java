/**
 * 文件职责：Spring Boot 应用启动入口。
 * 接口：无直接 HTTP 接口。
 * 方法：main(args) 创建并启动 TimeSlot 后端应用上下文。
*/

package com.timeslot;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@SpringBootApplication
@EnableTransactionManagement
@MapperScan({
        "com.timeslot.administration.mapper",
        "com.timeslot.common.bookingwindow",
        "com.timeslot.common.time",
        "com.timeslot.identity.mapper",
        "com.timeslot.resource.mapper",
        "com.timeslot.reservation.mapper",
        "com.timeslot.meeting.mapper"
})
public class TimeSlotApplication {
    public static void main(String[] args) {
        SpringApplication.run(TimeSlotApplication.class, args);
    }
}
