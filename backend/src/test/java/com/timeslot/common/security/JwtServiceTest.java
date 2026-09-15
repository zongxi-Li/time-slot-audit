package com.timeslot.common.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtServiceTest {
    private static final String VALID_SECRET = "unit-test-secret-key-with-at-least-32-bytes!!";

    @Test
    void issuesAndParsesTokenRoundtrip() {
        JwtService service = new JwtService(VALID_SECRET, 3600);
        AuthenticatedUser principal = new AuthenticatedUser(7L, "zhangsan", "USER");

        String token = service.issue(principal);
        AuthenticatedUser parsed = service.parse(token);

        assertEquals(7L, parsed.userId());
        assertEquals("zhangsan", parsed.getUsername());
        assertEquals("USER", parsed.role());
    }

    @Test
    void blankSecretFailsFast() {
        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> new JwtService("   ", 3600));

        assertTrue(exception.getMessage().contains("JWT_SECRET"));
    }

    @Test
    void shortSecretFailsFast() {
        assertThrows(IllegalStateException.class, () -> new JwtService("short-secret", 3600));
    }
}
