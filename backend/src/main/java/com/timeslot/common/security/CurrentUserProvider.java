/**
 * 文件职责：从 Spring Security 上下文获取当前用户。
 * 接口：供各业务 Service 读取用户 ID 和角色。
 */
package com.timeslot.common.security;

import com.timeslot.common.api.ErrorCode;
import com.timeslot.common.exception.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class CurrentUserProvider {
    public AuthenticatedUser getRequired() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof AuthenticatedUser user) {
            return user;
        }
        throw new BusinessException(ErrorCode.UNAUTHORIZED, HttpStatus.UNAUTHORIZED, "请先登录");
    }
}
