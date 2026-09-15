package com.timeslot;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@SpringBootApplication
@EnableTransactionManagement
@MapperScan({
        "com.timeslot.administration.mapper",
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
