package com.careerintelligence.util;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.util.Base64;

/**
 * Password hashing utility built entirely on the JDK's own crypto APIs
 * (PBKDF2WithHmacSHA256), so no third-party hashing library is required.
 *
 * The salt is generated per-user and stored separately (users.password_salt)
 * alongside the resulting hash (users.password_hash). Both are Base64 encoded
 * text, safe to store in VARCHAR columns.
 */
public final class PasswordUtil {

    private static final String ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final int SALT_LENGTH_BYTES = 16;
    private static final int HASH_LENGTH_BITS = 256;
    private static final int ITERATIONS = 65536;

    private PasswordUtil() {
    }

    /** Generates a new random, Base64-encoded salt. */
    public static String generateSalt() {
        SecureRandom random = new SecureRandom();
        byte[] salt = new byte[SALT_LENGTH_BYTES];
        random.nextBytes(salt);
        return Base64.getEncoder().encodeToString(salt);
    }

    /**
     * Hashes a plaintext password with the given Base64-encoded salt using
     * PBKDF2WithHmacSHA256, returning a Base64-encoded hash.
     */
    public static String hashPassword(String plainPassword, String base64Salt) {
        try {
            byte[] salt = Base64.getDecoder().decode(base64Salt);
            PBEKeySpec spec = new PBEKeySpec(
                    plainPassword.toCharArray(), salt, ITERATIONS, HASH_LENGTH_BITS);
            SecretKeyFactory factory = SecretKeyFactory.getInstance(ALGORITHM);
            byte[] hash = factory.generateSecret(spec).getEncoded();
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new IllegalStateException("Unable to hash password", e);
        }
    }

    /** Verifies a plaintext password against a stored hash + salt pair. */
    public static boolean verifyPassword(String plainPassword, String base64Salt, String expectedHash) {
        String actualHash = hashPassword(plainPassword, base64Salt);
        return slowEquals(actualHash, expectedHash);
    }

    /** Constant-time string comparison to reduce timing-attack surface. */
    private static boolean slowEquals(String a, String b) {
        if (a == null || b == null) {
            return false;
        }
        byte[] aBytes = a.getBytes();
        byte[] bBytes = b.getBytes();
        int diff = aBytes.length ^ bBytes.length;
        for (int i = 0; i < aBytes.length && i < bBytes.length; i++) {
            diff |= aBytes[i] ^ bBytes[i];
        }
        return diff == 0;
    }
}
