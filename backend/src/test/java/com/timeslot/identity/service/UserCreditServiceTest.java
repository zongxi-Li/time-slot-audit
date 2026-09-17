/**
 * 文件职责：验证 UserCreditServiceTest 相关业务、接口安全或边界条件。
 * 接口：使用 JUnit、Mockito 或 Spring 测试工具，不属于运行时接口。
 */
package com.timeslot.identity.service;

import com.timeslot.common.api.ErrorCode;
import com.timeslot.common.exception.BusinessException;
import com.timeslot.common.security.AuthenticatedUser;
import com.timeslot.common.security.CurrentUserProvider;
import com.timeslot.identity.domain.User;
import com.timeslot.identity.domain.UserViolation;
import com.timeslot.identity.dto.CreditAdjustRequest;
import com.timeslot.identity.dto.RestrictRequest;
import com.timeslot.identity.dto.UserResponse;
import com.timeslot.identity.dto.ViolationResponse;
import com.timeslot.identity.mapper.UserMapper;
import com.timeslot.identity.mapper.UserViolationMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UserCreditServiceTest {
    private static final Long ADMIN_ID = 1L;
    private static final Long USER_ID = 2L;

    @Mock UserMapper userMapper;
    @Mock UserViolationMapper userViolationMapper;
    @Mock CurrentUserProvider currentUserProvider;

    private UserCreditService service;
    private final Clock clock = Clock.fixed(Instant.parse("2026-09-15T02:00:00Z"), ZoneId.of("Asia/Shanghai"));
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 15, 10, 0);

    @BeforeEach
    void setUp() {
        service = new UserCreditService(userMapper, userViolationMapper, currentUserProvider, clock);
        when(currentUserProvider.getRequired()).thenReturn(new AuthenticatedUser(ADMIN_ID, "admin", "ADMIN"));
    }

    private User user(int creditScore, LocalDateTime restrictedUntil) {
        return new User(USER_ID, "lisi", "hash", "李四", "lisi@timeslot.demo", null,
                "USER", 1, 1L, creditScore, restrictedUntil, "信息中心");
    }

    private UserViolation record(String type, int creditChange, String reason, Long operatorId) {
        return new UserViolation(10L, USER_ID, type, creditChange, reason, operatorId, NOW, "系统管理员");
    }

    @Test
    void adjustCreditDeductsPointsAndRecordsReason() {
        when(userMapper.findById(USER_ID)).thenReturn(user(100, null)).thenReturn(user(50, null));

        UserResponse response = service.adjustCredit(USER_ID, new CreditAdjustRequest(-50, "会议无故缺席"));

        assertEquals(50, response.creditScore());
        verify(userMapper).updateCreditScore(USER_ID, 50);
        verify(userViolationMapper).insert(USER_ID, "CREDIT_DEDUCT", -50, "会议无故缺席", ADMIN_ID);
        verify(userMapper, never()).updateRestrictedUntil(anyLong(), any());
    }

    @Test
    void adjustCreditRejectsZeroChange() {
        when(userMapper.findById(USER_ID)).thenReturn(user(100, null));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.adjustCredit(USER_ID, new CreditAdjustRequest(0, "无效调整")));

        assertEquals(ErrorCode.VALIDATION_ERROR, exception.getCode());
        verify(userMapper, never()).updateCreditScore(anyLong(), anyInt());
    }

    @Test
    void adjustCreditClampsAtZeroAndRecordsAppliedChange() {
        when(userMapper.findById(USER_ID)).thenReturn(user(30, null)).thenReturn(user(0, NOW.plusDays(30)));

        service.adjustCredit(USER_ID, new CreditAdjustRequest(-100, "多次违约"));

        verify(userMapper).updateCreditScore(USER_ID, 0);
        verify(userViolationMapper).insert(USER_ID, "CREDIT_DEDUCT", -30, "多次违约", ADMIN_ID);
    }

    @Test
    void lowScoreTriggersSystemBlacklistFor30Days() {
        when(userMapper.findById(USER_ID)).thenReturn(user(70, null)).thenReturn(user(35, NOW.plusDays(30)));

        service.adjustCredit(USER_ID, new CreditAdjustRequest(-35, "恶意占用会议室"));

        verify(userMapper).updateRestrictedUntil(USER_ID, NOW.plusDays(30));
        verify(userViolationMapper).insert(eq(USER_ID), eq("BLACKLIST_SET"), eq(0),
                startsWith("系统自动"), isNull());
    }

    @Test
    void scoreAboveAutoThresholdDoesNotRestrict() {
        when(userMapper.findById(USER_ID)).thenReturn(user(70, null)).thenReturn(user(50, null));

        service.adjustCredit(USER_ID, new CreditAdjustRequest(-20, "迟到且未提前告知"));

        verify(userMapper, never()).updateRestrictedUntil(anyLong(), any());
        verify(userViolationMapper, never()).insert(anyLong(), eq("BLACKLIST_SET"), anyInt(), anyString(), any());
    }

    @Test
    void recoveredScoreAutoReleasesSystemRestriction() {
        when(userMapper.findById(USER_ID))
                .thenReturn(user(50, NOW.plusDays(25)))
                .thenReturn(user(70, null));
        when(userViolationMapper.findLatestByType(USER_ID, "BLACKLIST_SET"))
                .thenReturn(record("BLACKLIST_SET", 0, "系统自动：信用分低于 40 分", null));

        service.adjustCredit(USER_ID, new CreditAdjustRequest(20, "补充服务时长"));

        verify(userMapper).updateRestrictedUntil(USER_ID, null);
        verify(userViolationMapper).insert(eq(USER_ID), eq("BLACKLIST_RELEASE"), eq(0),
                startsWith("系统自动"), isNull());
    }

    @Test
    void recoveredScoreKeepsManualRestriction() {
        when(userMapper.findById(USER_ID)).thenReturn(user(50, NOW.plusDays(25)));
        when(userViolationMapper.findLatestByType(USER_ID, "BLACKLIST_SET"))
                .thenReturn(record("BLACKLIST_SET", 0, "多次违约，人工加入限制", ADMIN_ID));

        service.adjustCredit(USER_ID, new CreditAdjustRequest(20, "补充服务时长"));

        verify(userMapper, never()).updateRestrictedUntil(anyLong(), any());
        verify(userViolationMapper, never()).insert(anyLong(), eq("BLACKLIST_RELEASE"), anyInt(), anyString(), any());
    }

    @Test
    void setRestrictionRecordsManualBlacklist() {
        LocalDateTime until = NOW.plusDays(7);
        when(userMapper.findById(USER_ID)).thenReturn(user(80, null)).thenReturn(user(80, until));

        service.setRestriction(USER_ID, new RestrictRequest("多次违约，限制预约一周", until));

        verify(userMapper).updateRestrictedUntil(USER_ID, until);
        verify(userViolationMapper).insert(USER_ID, "BLACKLIST_SET", 0, "多次违约，限制预约一周", ADMIN_ID);
    }

    @Test
    void setRestrictionRejectsPastDeadline() {
        when(userMapper.findById(USER_ID)).thenReturn(user(80, null));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.setRestriction(USER_ID, new RestrictRequest("过期时间", NOW.minusDays(1))));

        assertEquals(ErrorCode.VALIDATION_ERROR, exception.getCode());
        verify(userMapper, never()).updateRestrictedUntil(anyLong(), any());
    }

    @Test
    void setRestrictionRejectsSelfOperation() {
        when(userMapper.findById(ADMIN_ID)).thenReturn(new User(ADMIN_ID, "admin", "hash", "系统管理员",
                null, null, "ADMIN", 1, null, 100, null, null));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.setRestriction(ADMIN_ID, new RestrictRequest("自封黑名单", NOW.plusDays(1))));

        assertEquals(ErrorCode.FORBIDDEN, exception.getCode());
        verify(userMapper, never()).updateRestrictedUntil(anyLong(), any());
    }

    @Test
    void releaseRemovesOnlyActiveRestriction() {
        when(userMapper.findById(USER_ID))
                .thenReturn(user(80, NOW.plusDays(5)))
                .thenReturn(user(80, null));

        service.setRestriction(USER_ID, new RestrictRequest("已沟通整改，解除限制", null));

        verify(userMapper).updateRestrictedUntil(USER_ID, null);
        verify(userViolationMapper).insert(USER_ID, "BLACKLIST_RELEASE", 0, "已沟通整改，解除限制", ADMIN_ID);
    }

    @Test
    void releaseIsNoopWhenNotRestricted() {
        when(userMapper.findById(USER_ID)).thenReturn(user(80, null));

        service.setRestriction(USER_ID, new RestrictRequest("解除限制", null));

        verify(userMapper, never()).updateRestrictedUntil(anyLong(), any());
        verify(userViolationMapper, never()).insert(anyLong(), anyString(), anyInt(), anyString(), any());
    }

    @Test
    void listViolationsRequiresExistingUserAndMapsOperatorName() {
        when(userMapper.findById(USER_ID)).thenReturn(user(80, null));
        when(userViolationMapper.findByUserId(USER_ID))
                .thenReturn(List.of(record("CREDIT_DEDUCT", -20, "预约后未到场", ADMIN_ID)));

        List<ViolationResponse> violations = service.listViolations(USER_ID);

        assertEquals(1, violations.size());
        assertEquals("CREDIT_DEDUCT", violations.get(0).violationType());
        assertEquals("系统管理员", violations.get(0).operatorName());

        when(userMapper.findById(99L)).thenReturn(null);
        assertEquals(ErrorCode.RESOURCE_NOT_FOUND, assertThrows(BusinessException.class,
                () -> service.listViolations(99L)).getCode());
    }
}
