package com.timeslot.identity.domain;

import java.time.LocalDateTime;

public record Department(Long id, String deptName, String description, LocalDateTime createdAt) {
}
