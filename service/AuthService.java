package com.careerintelligence.service;

import com.careerintelligence.dao.UserDAO;
import com.careerintelligence.dao.UserProfileDAO;
import com.careerintelligence.model.User;
import com.careerintelligence.model.UserProfile;
import com.careerintelligence.model.UserRole;
import com.careerintelligence.util.InputValidator;
import com.careerintelligence.util.LoginLockoutPolicy;
import com.careerintelligence.util.PasswordUtil;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Business logic for registration, login and profile management. Sits
 * between the console UI layer and the DAO layer so the UI never talks to
 * JDBC directly.
 */
public class AuthService {

    private final UserDAO userDAO = new UserDAO();
    private final UserProfileDAO userProfileDAO = new UserProfileDAO();

    /**
     * Result wrapper so the UI can distinguish between "invalid input",
     * "duplicate username/email" and "success" without exceptions for
     * control flow.
     */
    public static class AuthResult {
        private final boolean success;
        private final String message;
        private final User user;

        private AuthResult(boolean success, String message, User user) {
            this.success = success;
            this.message = message;
            this.user = user;
        }

        public static AuthResult ok(User user, String message) {
            return new AuthResult(true, message, user);
        }

        public static AuthResult fail(String message) {
            return new AuthResult(false, message, null);
        }

        public boolean isSuccess() {
            return success;
        }

        public String getMessage() {
            return message;
        }

        public User getUser() {
            return user;
        }
    }

    public AuthResult register(String username, String email, String password, String fullName, String phone) {
        try {
            if (!InputValidator.isValidUsername(username)) {
                return AuthResult.fail("Username must be 4-30 characters, letters/digits/underscore only.");
            }
            if (!InputValidator.isValidEmail(email)) {
                return AuthResult.fail("Please enter a valid email address.");
            }
            if (!InputValidator.isStrongPassword(password)) {
                return AuthResult.fail(InputValidator.passwordRequirementsMessage());
            }
            if (!InputValidator.isNonEmpty(fullName)) {
                return AuthResult.fail("Full name is required.");
            }
            if (!InputValidator.isValidPhone(phone)) {
                return AuthResult.fail("Please enter a valid phone number (7-15 digits) or leave it blank.");
            }
            if (userDAO.usernameExists(username)) {
                return AuthResult.fail("Username is already taken.");
            }
            if (userDAO.emailExists(email)) {
                return AuthResult.fail("An account with this email already exists.");
            }

            String salt = PasswordUtil.generateSalt();
            String hash = PasswordUtil.hashPassword(password, salt);

            User user = new User();
            user.setUsername(username);
            user.setEmail(email);
            user.setPasswordHash(hash);
            user.setPasswordSalt(salt);
            user.setFullName(fullName);
            user.setPhone(phone == null || phone.isBlank() ? null : phone);
            user.setRole(UserRole.USER);
            user.setActive(true);

            userDAO.create(user);

            // Create an empty profile row up-front so later profile features (resume, target role) can update in place.
            UserProfile profile = new UserProfile();
            profile.setUserId(user.getUserId());
            profile.setExperienceLevel("FRESHER");
            userProfileDAO.save(profile);

            return AuthResult.ok(user, "Registration successful! You can now log in.");
        } catch (SQLException e) {
            return AuthResult.fail("Database error during registration: " + e.getMessage());
        }
    }

    public AuthResult login(String usernameOrEmail, String password) {
        try {
            if (!InputValidator.isNonEmpty(usernameOrEmail) || !InputValidator.isNonEmpty(password)) {
                return AuthResult.fail("Username/email and password are required.");
            }
            Optional<User> maybeUser = userDAO.findByUsernameOrEmail(usernameOrEmail);
            if (maybeUser.isEmpty()) {
                return AuthResult.fail("Invalid credentials.");
            }
            User user = maybeUser.get();
            if (!user.isActive()) {
                return AuthResult.fail("This account has been deactivated. Contact an administrator.");
            }

            LocalDateTime now = LocalDateTime.now();
            if (LoginLockoutPolicy.isLocked(user.getLockedUntil(), now)) {
                long minutes = LoginLockoutPolicy.minutesRemaining(user.getLockedUntil(), now);
                return AuthResult.fail("This account is temporarily locked due to repeated failed login attempts. "
                        + "Try again in about " + minutes + " minute(s).");
            }

            boolean valid = PasswordUtil.verifyPassword(password, user.getPasswordSalt(), user.getPasswordHash());
            if (!valid) {
                int attempts = userDAO.incrementFailedLoginAttempts(user.getUserId());
                if (LoginLockoutPolicy.shouldLock(attempts)) {
                    userDAO.lockUntil(user.getUserId(), LoginLockoutPolicy.lockoutExpiry(now));
                    return AuthResult.fail("Too many failed attempts. This account is now locked for "
                            + LoginLockoutPolicy.LOCKOUT_MINUTES + " minutes.");
                }
                int remaining = LoginLockoutPolicy.MAX_ATTEMPTS - attempts;
                return AuthResult.fail("Invalid credentials. " + remaining + " attempt(s) remaining before "
                        + "this account is temporarily locked.");
            }

            userDAO.resetFailedLogins(user.getUserId());
            userDAO.updateLastLogin(user.getUserId());
            return AuthResult.ok(user, "Login successful. Welcome back, " + user.getFullName() + "!");
        } catch (SQLException e) {
            return AuthResult.fail("Database error during login: " + e.getMessage());
        }
    }

    public AuthResult updateProfile(User user, String fullName, String phone, String email) {
        try {
            if (!InputValidator.isNonEmpty(fullName)) {
                return AuthResult.fail("Full name is required.");
            }
            if (!InputValidator.isValidEmail(email)) {
                return AuthResult.fail("Please enter a valid email address.");
            }
            if (!InputValidator.isValidPhone(phone)) {
                return AuthResult.fail("Please enter a valid phone number or leave it blank.");
            }
            if (!email.equalsIgnoreCase(user.getEmail()) && userDAO.emailExists(email)) {
                return AuthResult.fail("Another account already uses this email.");
            }
            user.setFullName(fullName);
            user.setPhone(phone == null || phone.isBlank() ? null : phone);
            user.setEmail(email);
            userDAO.updateBasicInfo(user);
            return AuthResult.ok(user, "Profile updated successfully.");
        } catch (SQLException e) {
            return AuthResult.fail("Database error during profile update: " + e.getMessage());
        }
    }

    public AuthResult changePassword(User user, String currentPassword, String newPassword) {
        try {
            boolean valid = PasswordUtil.verifyPassword(currentPassword, user.getPasswordSalt(), user.getPasswordHash());
            if (!valid) {
                return AuthResult.fail("Current password is incorrect.");
            }
            if (!InputValidator.isStrongPassword(newPassword)) {
                return AuthResult.fail(InputValidator.passwordRequirementsMessage());
            }
            String newSalt = PasswordUtil.generateSalt();
            String newHash = PasswordUtil.hashPassword(newPassword, newSalt);
            userDAO.updatePassword(user.getUserId(), newHash, newSalt);
            user.setPasswordHash(newHash);
            user.setPasswordSalt(newSalt);
            return AuthResult.ok(user, "Password changed successfully.");
        } catch (SQLException e) {
            return AuthResult.fail("Database error while changing password: " + e.getMessage());
        }
    }

    public Optional<UserProfile> getProfile(Long userId) {
        try {
            return userProfileDAO.findByUserId(userId);
        } catch (SQLException e) {
            System.err.println("Could not load profile: " + e.getMessage());
            return Optional.empty();
        }
    }

    public AuthResult updateCareerProfile(Long userId, String targetRole, String targetCompany, String experienceLevel, String bio) {
        try {
            UserProfile profile = userProfileDAO.findByUserId(userId).orElse(new UserProfile());
            profile.setUserId(userId);
            profile.setTargetRole(targetRole);
            profile.setTargetCompany(targetCompany);
            profile.setExperienceLevel(experienceLevel);
            profile.setBio(bio);
            userProfileDAO.save(profile);
            return AuthResult.ok(null, "Career profile updated.");
        } catch (SQLException e) {
            return AuthResult.fail("Database error updating career profile: " + e.getMessage());
        }
    }
}
