package com.careerintelligence.dao;

import com.careerintelligence.database.DatabaseConnection;
import com.careerintelligence.model.CareerRefreshTrigger;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * DAO for `career_refresh_log`: a lightweight, purely-additive audit trail
 * of every time {@link com.careerintelligence.service.CareerIntelligenceService
 * #refreshAfterActivity} recomputed a user's Interview Readiness Score and
 * regenerated their Learning Roadmap in response to new activity
 * (an assessment, a mistake retry, or a mock interview). Never read by any
 * scoring or recommendation logic - it exists only to make the "Roadmap
 * Update" step of the Career Intelligence pipeline observable.
 */
public class CareerRefreshLogDAO {

    public void log(Long userId, CareerRefreshTrigger trigger, Double readinessScore, Long roadmapId) throws SQLException {
        log(userId, trigger, readinessScore, roadmapId, null);
    }

    /**
     * Same as {@link #log(Long, CareerRefreshTrigger, Double, Long)} but also records the
     * human-readable "what changed and why" explanation built by
     * {@code service.CareerIntelligenceService#refreshAfterActivityDetailed}.
     */
    public void log(Long userId, CareerRefreshTrigger trigger, Double readinessScore, Long roadmapId,
                     String details) throws SQLException {
        String sql = "INSERT INTO career_refresh_log (user_id, trigger_source, readiness_score, roadmap_id, details) "
                + "VALUES (?, ?, ?, ?, ?)";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, userId);
            ps.setString(2, trigger.name());
            if (readinessScore != null) {
                ps.setDouble(3, readinessScore);
            } else {
                ps.setNull(3, Types.DOUBLE);
            }
            if (roadmapId != null) {
                ps.setLong(4, roadmapId);
            } else {
                ps.setNull(4, Types.BIGINT);
            }
            ps.setString(5, details);
            ps.executeUpdate();
        }
    }

    public record Snapshot(Long id, String triggerSource, Double readinessScore, Long roadmapId,
                            String details, LocalDateTime createdAt) {
    }

    public List<Snapshot> findRecentByUser(Long userId, int limit) throws SQLException {
        String sql = "SELECT * FROM career_refresh_log WHERE user_id = ? ORDER BY created_at DESC LIMIT ?";
        List<Snapshot> result = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, userId);
            ps.setInt(2, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Timestamp ts = rs.getTimestamp("created_at");
                    double readiness = rs.getDouble("readiness_score");
                    Long roadmapId = rs.getLong("roadmap_id");
                    if (rs.wasNull()) {
                        roadmapId = null;
                    }
                    String details;
                    try {
                        details = rs.getString("details");
                    } catch (SQLException e) {
                        details = null; // pre-migration database without the `details` column yet
                    }
                    result.add(new Snapshot(
                            rs.getLong("id"),
                            rs.getString("trigger_source"),
                            rs.getObject("readiness_score") == null ? null : readiness,
                            roadmapId,
                            details,
                            ts == null ? null : ts.toLocalDateTime()));
                }
            }
        }
        return result;
    }
}
