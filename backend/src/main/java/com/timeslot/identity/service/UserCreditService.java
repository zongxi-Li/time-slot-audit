/**
 * 文件职责：管理用户信用分、预约限制和违规记录。
 * 接口：被管理员用户管理服务调用。
 * 方法：listViolations 查询违规；adjustCredit 调整信用分；setRestriction 设置限制；applyAutoBlacklistRules 应用自动限制规则；requireUser 校验用户存在。
*/

package com.timeslot.identity.service;

import com.timeslot.common.api.ErrorCode;
import com.timeslot.common.exception.BusinessException;
import com.timeslot.common.security.CurrentUserProvider;
import com.timeslot.identity.domain.BookingQualification;
import com.timeslot.identity.domain.CreditRules;
import com.timeslot.identity.domain.User;
import com.timeslot.identity.domain.UserViolation;
import com.timeslot.identity.domain.ViolationType;
import com.timeslot.identity.dto.CreditAdjustRequest;
import com.timeslot.identity.dto.RestrictRequest;
import com.timeslot.identity.dto.UserResponse;
import com.timeslot.identity.dto.ViolationResponse;
import com.timeslot.identity.mapper.UserMapper;
import com.timeslot.identity.mapper.UserViolationMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 信用/违规/黑名单规则：
 * - 管理员人工调整信用必须记录原因；
 * - 人工调整后联动自动黑名单：分数低于 AUTO_BLACKLIST_THRESHOLD 自动限制（系统记录），
 *   系统自动限制在信用恢复后自动解除；人工黑名单只能人工解除；
 * - 账号启停的违规记录由 UserAdminService 写入。
 */
@Service
public class UserCreditService {
    private final UserMapper userMapper;
    private final UserViolationMapper userViolationMapper;
    private final CurrentUserProvider currentUserProvider;
    private final Clock clock;

    public UserCreditService(UserMapper userMapper, UserViolationMapper userViolationMapper,
                             CurrentUserProvider currentUserProvider, Clock clock) {
        this.userMapper = userMapper;
        this.userViolationMapper = userViolationMapper;
        this.currentUserProvider = currentUserProvider;
        this.clock = clock;
    }

    public List<ViolationResponse> listViolations(Long userId) {
        requireUser(userId);
        return userViolationMapper.findByUserId(userId).stream().map(ViolationResponse::from).toList();
    }

    @Transactional
    public UserResponse adjustCredit(Long userId, CreditAdjustRequest request) {
        User user = requireUser(userId);
        if (request.creditChange() == 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, HttpStatus.BAD_REQUEST, "信用分变化量不能为 0");
        }
        int current = user.creditScore() == null ? CreditRules.DEFAULT_CREDIT_SCORE : user.creditScore();
        int newScore = Math.max(0, current + request.creditChange());
        int appliedChange = newScore - current;
        Long operator = currentUserProvider.getRequired().userId();

        userMapper.updateCreditScore(userId, newScore);
        userViolationMapper.insert(userId,
                request.creditChange() > 0 ? ViolationType.CREDIT_REWARD : ViolationType.CREDIT_DEDUCT,
                appliedChange, request.reason().trim(), operator);
        applyAutoBlacklistRules(userId, user, newScore);
        return UserResponse.from(userMapper.findById(userId));
    }

    @Transactional
    public UserResponse setRestriction(Long userId, RestrictRequest request) {
        User user = requireUser(userId);
        Long operator = currentUserProvider.getRequired().userId();
        if (userId.equals(operator)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, HttpStatus.FORBIDDEN, "不能操作自己的账号");
        }
        LocalDateTime now = LocalDateTime.now(clock);
        if (request.restrictedUntil() == null) {
            if (user.restrictedUntil() == null || !user.restrictedUntil().isAfter(now)) {
                return UserResponse.from(user);
            }
            userMapper.updateRestrictedUntil(userId, null);
            userViolationMapper.insert(userId, ViolationType.BLACKLIST_RELEASE, 0, request.reason().trim(), operator);
            return UserResponse.from(userMapper.findById(userId));
        }
        if (!request.restrictedUntil().isAfter(now)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, HttpStatus.BAD_REQUEST, "限制截止时间必须晚于当前时间");
        }
        userMapper.updateRestrictedUntil(userId, request.restrictedUntil());
        userViolationMapper.insert(userId, ViolationType.BLACKLIST_SET, 0, request.reason().trim(), operator);
        return UserResponse.from(userMapper.findById(userId));
    }

    private void applyAutoBlacklistRules(Long userId, User before, int newScore) {
        boolean currentlyRestricted = before.restrictedUntil() != null
                && before.restrictedUntil().isAfter(LocalDateTime.now(clock));
        if (!currentlyRestricted && newScore < CreditRules.AUTO_BLACKLIST_THRESHOLD) {
            LocalDateTime until = LocalDateTime.now(clock).plusDays(CreditRules.AUTO_BLACKLIST_DAYS);
            userMapper.updateRestrictedUntil(userId, until);
            userViolationMapper.insert(userId, ViolationType.BLACKLIST_SET, 0,
                    ViolationType.AUTO_REASON_PREFIX + "：信用分低于 " + CreditRules.AUTO_BLACKLIST_THRESHOLD
                            + " 分，自动限制预约 " + CreditRules.AUTO_BLACKLIST_DAYS + " 天（至 "
                            + BookingQualification.format(until) + "）",
                    null);
            return;
        }
        if (currentlyRestricted && newScore >= CreditRules.MIN_BOOKING_CREDIT_SCORE) {
            UserViolation latestSet = userViolationMapper.findLatestByType(userId, ViolationType.BLACKLIST_SET);
            if (latestSet != null && latestSet.autoTriggered()) {
                userMapper.updateRestrictedUntil(userId, null);
                userViolationMapper.insert(userId, ViolationType.BLACKLIST_RELEASE, 0,
                        ViolationType.AUTO_REASON_PREFIX + "：信用分恢复至 " + newScore + " 分，自动解除限制",
                        null);
            }
        }
    }

    private User requireUser(Long userId) {
        User user = userMapper.findById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, HttpStatus.NOT_FOUND, "用户不存在");
        }
        return user;
    }
}
