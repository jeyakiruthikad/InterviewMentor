package com.careerintelligence.dao;

import com.careerintelligence.database.DatabaseConnection;
import com.careerintelligence.model.Badge;
import com.careerintelligence.model.PointsLogEntry;
import com.careerintelligence.model.UserBadge;
import com.careerintelligence.model.UserPoints;

import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * DAO for all gamification tables: `user_points`, `points_log`,
 * `badge_catalog` and `user_badges`. Powers service.GamificationService
 * (points, streaks, badges, achievements).
 */
public class GamificationDAO {

    /** Loads (or lazily creates) the points/streak row for a user. */
    public UserPoints findOrCreate(long userId) throws SQLException {
        String selectSql = "SELECT * FROM user_points WHERE user_id = ?";
        try (Connection conn = DatabaseConnection.getConnection()) {
            try (PreparedStatement ps = conn.prepareStatement(selectSql)) {
                ps.setLong(1, userId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return mapRow(rs);
                    }
                }
            }
            String insertSql = "INSERT INTO user_points (user_id, total_points, current_streak_days, longest_streak_days) "
                    + "VALUES (?, 0, 0, 0)";
            try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
                ps.setLong(1, userId);
                ps.executeUpdate();
            }
        }
        UserPoints fresh = new UserPoints();
        fresh.setUserId(userId);
        return fresh;
    }

    /** Adds (or subtracts) points and appends an audit log row, in one transaction. */
    public void addPoints(long userId, int points, String reason) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection()) {
            boolean originalAutoCommit = conn.getAutoCommit();
            conn.setAutoCommit(false);
            try {
                String upsert = "INSERT INTO user_points (user_id, total_points) VALUES (?, ?) "
                        + "ON DUPLICATE KEY UPDATE total_points = total_points + VALUES(total_points)";
                try (PreparedStatement ps = conn.prepareStatement(upsert)) {
                    ps.setLong(1, userId);
                    ps.setInt(2, points);
                    ps.executeUpdate();
                }
                String log = "INSERT INTO points_log (user_id, points, reason) VALUES (?, ?, ?)";
                try (PreparedStatement ps = conn.prepareStatement(log)) {
                    ps.setLong(1, userId);
                    ps.setInt(2, points);
                    ps.setString(3, reason);
                    ps.executeUpdate();
                }
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(originalAutoCommit);
            }
        }
    }

    /**
     * Updates the daily activity streak for a user: if their last activity
     * was yesterday, the streak continues (+1); if it was already today,
     * nothing changes; otherwise (gap of 2+ days, or first ever activity)
     * the streak resets to 1. Longest streak is tracked alongside.
     * Returns the updated streak count.
     */
    public int touchStreak(long userId) throws SQLException {
        UserPoints current = findOrCreate(userId);
        LocalDate today = LocalDate.now();
        LocalDate last = current.getLastActivityDate();

        int newStreak;
        if (last == null) {
            newStreak = 1;
        } else if (last.equals(today)) {
            return current.getCurrentStreakDays() == 0 ? 1 : current.getCurrentStreakDays();
        } else if (last.equals(today.minusDays(1))) {
            newStreak = current.getCurrentStreakDays() + 1;
        } else {
            newStreak = 1;
        }
        int longest = Math.max(newStreak, current.getLongestStreakDays());

        String sql = "UPDATE user_points SET current_streak_days = ?, longest_streak_days = ?, last_activity_date = ? "
                + "WHERE user_id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, newStreak);
            ps.setInt(2, longest);
            ps.setDate(3, Date.valueOf(today));
            ps.setLong(4, userId);
            ps.executeUpdate();
        }
        return newStreak;
    }

    public List<Badge> findAllBadgeDefinitions() throws SQLException {
        String sql = "SELECT * FROM badge_catalog ORDER BY badge_name";
        List<Badge> result = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                result.add(new Badge(rs.getString("badge_code"), rs.getString("badge_name"),
                        rs.getString("description"), rs.getString("criteria")));
            }
        }
        return result;
    }

    public boolean hasBadge(long userId, String badgeCode) throws SQLException {
        String sql = "SELECT 1 FROM user_badges WHERE user_id = ? AND badge_code = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, userId);
            ps.setString(2, badgeCode);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    /** Awards a badge if the user doesn't already have it. Returns true if newly awarded. */
    public boolean awardBadge(long userId, Badge badge) throws SQLException {
        if (hasBadge(userId, badge.getBadgeCode())) {
            return false;
        }
        String sql = "INSERT IGNORE INTO user_badges (user_id, badge_name, badge_code, description) VALUES (?, ?, ?, ?)";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, userId);
            ps.setString(2, badge.getBadgeName());
            ps.setString(3, badge.getBadgeCode());
            ps.setString(4, badge.getDescription());
            int rows = ps.executeUpdate();
            return rows > 0;
        }
    }

    public List<UserBadge> findBadgesByUser(long userId) throws SQLException {
        String sql = "SELECT * FROM user_badges WHERE user_id = ? ORDER BY earned_at DESC";
        List<UserBadge> result = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    UserBadge b = new UserBadge();
                    b.setId(rs.getLong("id"));
                    b.setUserId(rs.getLong("user_id"));
                    b.setBadgeName(rs.getString("badge_name"));
                    b.setBadgeCode(rs.getString("badge_code"));
                    b.setDescription(rs.getString("description"));
                    Timestamp earned = rs.getTimestamp("earned_at");
                    if (earned != null) b.setEarnedAt(earned.toLocalDateTime());
                    result.add(b);
                }
            }
        }
        return result;
    }

    public List<PointsLogEntry> findRecentPointsLog(long userId, int limit) throws SQLException {
        String sql = "SELECT * FROM points_log WHERE user_id = ? ORDER BY created_at DESC LIMIT ?";
        List<PointsLogEntry> result = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, userId);
            ps.setInt(2, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    PointsLogEntry e = new PointsLogEntry();
                    e.setId(rs.getLong("id"));
                    e.setUserId(rs.getLong("user_id"));
                    e.setPoints(rs.getInt("points"));
                    e.setReason(rs.getString("reason"));
                    Timestamp created = rs.getTimestamp("created_at");
                    if (created != null) e.setCreatedAt(created.toLocalDateTime());
                    result.add(e);
                }
            }
        }
        return result;
    }

    /** Leaderboard: top N users by total points (for admin cross-user visibility). */
    public List<Object[]> findTopUsersByPoints(int limit) throws SQLException {
        String sql = "SELECT u.full_name, u.username, up.total_points, up.current_streak_days "
                + "FROM user_points up JOIN users u ON u.user_id = up.user_id "
                + "ORDER BY up.total_points DESC LIMIT ?";
        List<Object[]> result = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(new Object[]{rs.getString("full_name"), rs.getString("username"),
                            rs.getInt("total_points"), rs.getInt("current_streak_days")});
                }
            }
        }
        return result;
    }

    private UserPoints mapRow(ResultSet rs) throws SQLException {
        UserPoints p = new UserPoints();
        p.setUserId(rs.getLong("user_id"));
        p.setTotalPoints(rs.getInt("total_points"));
        p.setCurrentStreakDays(rs.getInt("current_streak_days"));
        p.setLongestStreakDays(rs.getInt("longest_streak_days"));
        Date lastActivity = rs.getDate("last_activity_date");
        if (lastActivity != null) p.setLastActivityDate(lastActivity.toLocalDate());
        Timestamp updated = rs.getTimestamp("updated_at");
        if (updated != null) p.setUpdatedAt(updated.toLocalDateTime());
        return p;
    }
}
