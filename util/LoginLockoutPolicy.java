package com.careerintelligence.util;

import java.time.LocalDateTime;

/**
 * Pure, database-free brute-force login lockout policy used by
 * {@code service.AuthService}: after {@link #MAX_ATTEMPTS} consecutive
 * failed logins, the account is locked for {@link #LOCKOUT_MINUTES}
 * minutes. Kept as a standalone, static utility (rather than inline in
 * AuthService) purely so the threshold/duration math is directly unit
 * testable without a database - see {@code LoginLockoutPolicyTest}.
 */
public final class LoginLockoutPolicy {

    /** Consecutive failed attempts allowed before the account is locked. */
    public static final int MAX_ATTEMPTS = 5;
    /** How long an account stays locked once {@link #MAX_ATTEMPTS} is reached. */
    public static final int LOCKOUT_MINUTES = 15;

    private LoginLockoutPolicy() {
    }

    /** True if {@code lockedUntil} is a non-null instant still in the future relative to {@code now}. */
    public static boolean isLocked(LocalDateTime lockedUntil, LocalDateTime now) {
        return lockedUntil != null && lockedUntil.isAfter(now);
    }

    /** True if this many failed attempts (after incrementing) should trigger a new lockout. */
    public static boolean shouldLock(int failedAttemptsAfterIncrement) {
        return failedAttemptsAfterIncrement >= MAX_ATTEMPTS;
    }

    /** The instant a new lockout should expire, given the current time. */
    public static LocalDateTime lockoutExpiry(LocalDateTime now) {
        return now.plusMinutes(LOCKOUT_MINUTES);
    }

    /** Minutes remaining until {@code lockedUntil}, rounded up, floored at 1 for display purposes. */
    public static long minutesRemaining(LocalDateTime lockedUntil, LocalDateTime now) {
        if (lockedUntil == null || !lockedUntil.isAfter(now)) {
            return 0;
        }
        long seconds = java.time.Duration.between(now, lockedUntil).getSeconds();
        return Math.max(1, (seconds + 59) / 60);
    }
}
