package com.careerintelligence.util;

import java.util.regex.Pattern;

/**
 * Centralised input validation for registration, login and profile forms.
 */
public final class InputValidator {

    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

    private static final Pattern USERNAME_PATTERN =
            Pattern.compile("^[A-Za-z0-9_]{4,30}$");

    // At least 8 chars, 1 uppercase, 1 lowercase, 1 digit, 1 special char
    private static final Pattern STRONG_PASSWORD_PATTERN =
            Pattern.compile("^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&#^()_+=\\-]).{8,64}$");

    private static final Pattern PHONE_PATTERN =
            Pattern.compile("^[+]?[0-9]{7,15}$");

    private InputValidator() {
    }

    public static boolean isValidEmail(String email) {
        return email != null && EMAIL_PATTERN.matcher(email.trim()).matches();
    }

    public static boolean isValidUsername(String username) {
        return username != null && USERNAME_PATTERN.matcher(username.trim()).matches();
    }

    public static boolean isStrongPassword(String password) {
        return password != null && STRONG_PASSWORD_PATTERN.matcher(password).matches();
    }

    public static boolean isValidPhone(String phone) {
        if (phone == null || phone.isBlank()) {
            return true; // phone is optional
        }
        return PHONE_PATTERN.matcher(phone.trim()).matches();
    }

    public static boolean isNonEmpty(String value) {
        return value != null && !value.trim().isEmpty();
    }

    public static String passwordRequirementsMessage() {
        return "Password must be 8-64 characters long and include at least one uppercase letter, "
                + "one lowercase letter, one digit and one special character (@$!%*?&#^()_+=-).";
    }
}
