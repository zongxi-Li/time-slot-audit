package com.timeslot.identity.domain;

public record User(Long id, String username, String password, String realName, String role, Integer status) {
    public boolean enabled() {
        return status != null && status == 1;
    }
}
