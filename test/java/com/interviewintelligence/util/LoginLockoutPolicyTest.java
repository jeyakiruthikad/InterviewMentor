package com.careerintelligence.util;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the pure, database-free brute-force lockout math in
 * {@link LoginLockoutPolicy}, used by {@code service.AuthService#login}.
 * No database or MySQL connection is required - run with: mvn test
 */
class LoginLockoutPolicyTest {

    private final LocalDateTime now = LocalDateTime.of(2026, 1, 1, 12, 0);

    @Test
    void nullLockedUntilMeansNotLocked() {
        assertFalse(LoginLockoutPolicy.isLocked(null, now));
    }

    @Test
    void futureLockedUntilMeansLocked() {
        assertTrue(LoginLockoutPolicy.isLocked(now.plusMinutes(5), now));
    }

    @Test
    void pastLockedUntilMeansNotLocked() {
        assertFalse(LoginLockoutPolicy.isLocked(now.minusMinutes(5), now));
    }

    @Test
    void shouldLockAtOrAboveThreshold() {
        assertFalse(LoginLockoutPolicy.shouldLock(LoginLockoutPolicy.MAX_ATTEMPTS - 1));
        assertTrue(LoginLockoutPolicy.shouldLock(LoginLockoutPolicy.MAX_ATTEMPTS));
        assertTrue(LoginLockoutPolicy.shouldLock(LoginLockoutPolicy.MAX_ATTEMPTS + 1));
    }

    @Test
    void lockoutExpiryAddsConfiguredMinutes() {
        assertEquals(now.plusMinutes(LoginLockoutPolicy.LOCKOUT_MINUTES), LoginLockoutPolicy.lockoutExpiry(now));
    }

    @Test
    void minutesRemainingIsZeroWhenNotLocked() {
        assertEquals(0, LoginLockoutPolicy.minutesRemaining(null, now));
        assertEquals(0, LoginLockoutPolicy.minutesRemaining(now.minusMinutes(1), now));
    }

    @Test
    void minutesRemainingRoundsUpWhileLocked() {
        long minutes = LoginLockoutPolicy.minutesRemaining(now.plusSeconds(61), now);
        assertTrue(minutes >= 1 && minutes <= 2);
    }
}
