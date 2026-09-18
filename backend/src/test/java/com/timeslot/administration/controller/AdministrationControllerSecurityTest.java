/**
 * 文件职责：验证 AdministrationControllerSecurityTest 相关业务、接口安全或边界条件。
 * 接口：使用 JUnit、Mockito 或 Spring 测试工具，不属于运行时接口。
 */
package com.timeslot.administration.controller;

import com.timeslot.administration.service.AdministrationService;
import com.timeslot.administration.mapper.AdministrationMapper;
import com.timeslot.common.security.JwtService;
import com.timeslot.identity.mapper.DepartmentMapper;
import com.timeslot.identity.mapper.UserMapper;
import com.timeslot.identity.mapper.UserViolationMapper;
import com.timeslot.meeting.mapper.MeetingExecutionMapper;
import com.timeslot.meeting.mapper.NotificationMapper;
import com.timeslot.reservation.mapper.ReservationMapper;
import com.timeslot.resource.mapper.ResourceMapper;
import com.timeslot.common.time.SystemTimeMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import jakarta.annotation.Resource;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdministrationController.class)
@Import(AdministrationControllerSecurityTest.MethodSecurityConfiguration.class)
class AdministrationControllerSecurityTest {
    @Resource MockMvc mockMvc;
    @MockBean AdministrationService service;
    @MockBean JwtService jwtService;
    @MockBean AdministrationMapper administrationMapper;
    @MockBean UserMapper userMapper;
    @MockBean DepartmentMapper departmentMapper;
    @MockBean UserViolationMapper userViolationMapper;
    @MockBean MeetingExecutionMapper meetingExecutionMapper;
    @MockBean NotificationMapper notificationMapper;
    @MockBean ReservationMapper reservationMapper;
    @MockBean ResourceMapper resourceMapper;
    @MockBean SystemTimeMapper systemTimeMapper;

    @Test
    @WithMockUser(roles = "USER")
    void userCannotAccessAdminApi() throws Exception {
        mockMvc.perform(get("/api/admin/reservations").param("status", "PENDING"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminCanAccessAdminApi() throws Exception {
        when(service.reservations("PENDING")).thenReturn(List.of());

        mockMvc.perform(get("/api/admin/reservations").param("status", "PENDING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"));
    }

    @TestConfiguration
    @EnableMethodSecurity
    static class MethodSecurityConfiguration {
    }
}
