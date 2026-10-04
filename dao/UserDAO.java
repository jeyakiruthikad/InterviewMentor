package com.careerintelligence.dao;

import com.careerintelligence.database.DatabaseConnection;
import com.careerintelligence.model.User;
import com.careerintelligence.model.UserRole;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Data-access object for the `users` table. Every method opens and closes
 * its own JDBC connection (try-with-resources) so the class is trivially
 * thread-safe and needs no external connection-pool dependency.
 */
public class UserDAO {

    public boolean usernameExists(String username) throws SQLException {
        String sql = "SELECT 1 FROM users WHERE username = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    public boolean emailExists(String email) throws SQLException {
        String sql = "SELECT 1 FROM users WHERE email = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, email);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    /** Inserts a new user and returns the same instance populated with its generated userId. */
    public User create(User user) throws SQLException {
        String sql = "INSERT INTO users (username, email, password_hash, password_salt, full_name, phone, role, is_active) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, user.getUsername());
            ps.setString(2, user.getEmail());
            ps.setString(3, user.getPasswordHash());
            ps.setString(4, user.getPasswordSalt());
            ps.setString(5, user.getFullName());
            ps.setString(6, user.getPhone());
            ps.setString(7, user.getRole() == null ? "USER" : user.getRole().name());
            ps.setBoolean(8, user.isActive());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    user.setUserId(keys.getLong(1));
                }
            }
        }
        return user;
    }

    public Optional<User> findByUsernameOrEmail(String usernameOrEmail) throws SQLException {
        String sql = "SELECT * FROM users WHERE username = ? OR email = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, usernameOrEmail);
            ps.setString(2, usernameOrEmail);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
            }
        }
        return Optional.empty();
    }

    public Optional<User> findById(Long userId) throws SQLException {
        String sql = "SELECT * FROM users WHERE user_id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
            }
        }
        return Optional.empty();
    }

    public void updateLastLogin(Long userId) throws SQLException {
        String sql = "UPDATE users SET last_login_at = ? WHERE user_id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setTimestamp(1, Timestamp.valueOf(LocalDateTime.now()));
            ps.setLong(2, userId);
            ps.executeUpdate();
        }
    }

    // -----------------------------------------------------------------
    // Account lockout (brute-force login protection - see service.AuthService)
    // -----------------------------------------------------------------

    /** Increments the failed-login counter and returns the new count, in one atomic statement. */
    public int incrementFailedLoginAttempts(Long userId) throws SQLException {
        String sql = "UPDATE users SET failed_login_attempts = failed_login_attempts + 1 WHERE user_id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, userId);
            ps.executeUpdate();
        }
        return findById(userId).map(User::getFailedLoginAttempts).orElse(0);
    }

    /** Locks the account until the given instant (used once too many failed attempts accumulate). */
    public void lockUntil(Long userId, LocalDateTime until) throws SQLException {
        String sql = "UPDATE users SET locked_until = ? WHERE user_id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setTimestamp(1, Timestamp.valueOf(until));
            ps.setLong(2, userId);
            ps.executeUpdate();
        }
    }

    /** Clears the failed-attempt counter and any lockout - called on every successful login. */
    public void resetFailedLogins(Long userId) throws SQLException {
        String sql = "UPDATE users SET failed_login_attempts = 0, locked_until = NULL WHERE user_id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, userId);
            ps.executeUpdate();
        }
    }

    /** Updates the editable profile fields (full name, phone, email). */
    public void updateBasicInfo(User user) throws SQLException {
        String sql = "UPDATE users SET full_name = ?, phone = ?, email = ? WHERE user_id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, user.getFullName());
            ps.setString(2, user.getPhone());
            ps.setString(3, user.getEmail());
            ps.setLong(4, user.getUserId());
            ps.executeUpdate();
        }
    }

    public void updatePassword(Long userId, String newHash, String newSalt) throws SQLException {
        String sql = "UPDATE users SET password_hash = ?, password_salt = ? WHERE user_id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, newHash);
            ps.setString(2, newSalt);
            ps.setLong(3, userId);
            ps.executeUpdate();
        }
    }

    // -----------------------------------------------------------------
    // Admin Module additions (final implementation): user management
    // -----------------------------------------------------------------

    /** All users, most recently created first - used by the admin User Management screen. */
    public List<User> findAll() throws SQLException {
        String sql = "SELECT * FROM users ORDER BY created_at DESC";
        List<User> result = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                result.add(mapRow(rs));
            }
        }
        return result;
    }

    /** Activates or deactivates a user account (deactivated users cannot log in - see AuthService#login). */
    public boolean setActive(Long userId, boolean active) throws SQLException {
        String sql = "UPDATE users SET is_active = ? WHERE user_id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setBoolean(1, active);
            ps.setLong(2, userId);
            return ps.executeUpdate() > 0;
        }
    }

    /** Promotes/demotes a user between USER and ADMIN. */
    public boolean setRole(Long userId, UserRole role) throws SQLException {
        String sql = "UPDATE users SET role = ? WHERE user_id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, role.name());
            ps.setLong(2, userId);
            return ps.executeUpdate() > 0;
        }
    }

    /** Permanently deletes a user and (via ON DELETE CASCADE) all of their dependent data. Admin-only, irreversible. */
    public boolean delete(Long userId) throws SQLException {
        String sql = "DELETE FROM users WHERE user_id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, userId);
            return ps.executeUpdate() > 0;
        }
    }

    public int countAll() throws SQLException {
        String sql = "SELECT COUNT(*) FROM users";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    public int countByRole(UserRole role) throws SQLException {
        String sql = "SELECT COUNT(*) FROM users WHERE role = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, role.name());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    private User mapRow(ResultSet rs) throws SQLException {
        User user = new User();
        user.setUserId(rs.getLong("user_id"));
        user.setUsername(rs.getString("username"));
        user.setEmail(rs.getString("email"));
        user.setPasswordHash(rs.getString("password_hash"));
        user.setPasswordSalt(rs.getString("password_salt"));
        user.setFullName(rs.getString("full_name"));
        user.setPhone(rs.getString("phone"));
        user.setRole(UserRole.valueOf(rs.getString("role")));
        user.setActive(rs.getBoolean("is_active"));
        Timestamp createdAt = rs.getTimestamp("created_at");
        if (createdAt != null) {
            user.setCreatedAt(createdAt.toLocalDateTime());
        }
        Timestamp updatedAt = rs.getTimestamp("updated_at");
        if (updatedAt != null) {
            user.setUpdatedAt(updatedAt.toLocalDateTime());
        }
        Timestamp lastLogin = rs.getTimestamp("last_login_at");
        if (lastLogin != null) {
            user.setLastLoginAt(lastLogin.toLocalDateTime());
        }
        try {
            user.setFailedLoginAttempts(rs.getInt("failed_login_attempts"));
            Timestamp lockedUntil = rs.getTimestamp("locked_until");
            if (lockedUntil != null) {
                user.setLockedUntil(lockedUntil.toLocalDateTime());
            }
        } catch (SQLException e) {
            // lockout simply behaves as "never locked" until the migration is applied.
        }
        return user;
    }
}
