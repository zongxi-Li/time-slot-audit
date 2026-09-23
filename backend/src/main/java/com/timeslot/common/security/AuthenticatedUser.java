/**
 * 文件职责：保存当前认证用户标识，并适配 Spring Security 的 UserDetails。
 * 接口：供 CurrentUserProvider 和业务 Service 使用。
 * 方法：getAuthorities/getUsername/getPassword 向 Spring Security 提供权限和主体信息。
 */

package com.timeslot.common.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

public record AuthenticatedUser(Long userId, String username, String role) implements UserDetails {
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role));
    }

    @Override
    public String getPassword() {
        return "";
    }

    @Override
    public String getUsername() {
        return username;
    }
}
