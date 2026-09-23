/**
 * 文件职责：从当前安全上下文取得已认证用户。
 * 接口：供各业务 Service 读取用户 ID 和角色。
 * 方法：getRequired 返回当前 AuthenticatedUser；不存在认证用户时按实现约定报错。
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
