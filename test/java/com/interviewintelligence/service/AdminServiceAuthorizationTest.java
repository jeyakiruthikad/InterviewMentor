package com.careerintelligence.service;

import com.careerintelligence.model.User;
import com.careerintelligence.model.UserRole;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the server-side authorization guard in
 * {@link AdminService#assertAdmin}: every admin-only method calls this
 * before touching any data, so a non-admin (or missing) actor can never
 * reach an admin operation even if a future UI bug skipped its own role
 * check. No database or MySQL connection is required - run with: mvn test
 */
class AdminServiceAuthorizationTest {

    private static User userWithRole(UserRole role) {
        User u = new User();
        u.setUserId(1L);
        u.setRole(role);
        return u;
    }

    @Test
    void adminIsAllowed() {
        assertDoesNotThrow(() -> AdminService.assertAdmin(userWithRole(UserRole.ADMIN)));
    }

    @Test
    void regularUserIsRejected() {
        assertThrows(AdminService.UnauthorizedException.class,
                () -> AdminService.assertAdmin(userWithRole(UserRole.USER)));
    }

    @Test
    void nullActorIsRejected() {
        assertThrows(AdminService.UnauthorizedException.class, () -> AdminService.assertAdmin(null));
    }
}
