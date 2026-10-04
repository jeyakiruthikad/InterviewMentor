package com.careerintelligence.dao;

import com.careerintelligence.database.DatabaseConnection;
import com.careerintelligence.model.ReadinessScore;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * DAO for `readiness_score_history`: persists a snapshot every time
 * service.ReadinessScoreService computes a user's Interview Readiness
 * Score, so the UI can show how it has trended over time (and so the
 * computation is auditable, not just a transient in-memory number).
 */
public class ReadinessScoreDAO {

    public void insertSnapshot(long userId, ReadinessScore score) throws SQLException {
        String sql = "INSERT INTO readiness_score_history "
                + "(user_id, overall_score, assessment_component, topic_component, mistake_component, "
                + "resume_component, mock_component, technical_dimension, problem_solving_dimension, "
                + "communication_dimension, behavioral_dimension, role_alignment_dimension, "
                + "biggest_strength, biggest_risk, next_recommended_action) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, userId);
            ps.setDouble(2, score.getOverallScore());
            ps.setDouble(3, componentValue(score, "assessment"));
            ps.setDouble(4, componentValue(score, "topic"));
            ps.setDouble(5, componentValue(score, "mistake"));
            ps.setDouble(6, componentValue(score, "resume"));
            ps.setDouble(7, componentValue(score, "mock"));
            ps.setDouble(8, dimensionValue(score, "technical"));
            ps.setDouble(9, dimensionValue(score, "problem_solving"));
            ps.setDouble(10, dimensionValue(score, "communication"));
            ps.setDouble(11, dimensionValue(score, "behavioral"));
            ps.setDouble(12, dimensionValue(score, "role_alignment"));
            ps.setString(13, score.getBiggestStrength());
            ps.setString(14, score.getBiggestRisk());
            ps.setString(15, score.getNextRecommendedAction());
            ps.executeUpdate();
        }
    }

    private double componentValue(ReadinessScore score, String key) {
        ReadinessScore.Component c = score.getComponents().get(key);
        return c == null ? 0.0 : c.getValue();
    }

    private double dimensionValue(ReadinessScore score, String key) {
        ReadinessScore.Component c = score.getDimensions().get(key);
        return c == null ? 0.0 : c.getValue();
    }

    public record Snapshot(double overallScore, double assessmentComponent, double topicComponent,
                            double mistakeComponent, double resumeComponent, double mockComponent,
                            double technicalDimension, double problemSolvingDimension,
                            double communicationDimension, double behavioralDimension,
                            double roleAlignmentDimension, String biggestStrength, String biggestRisk,
                            String nextRecommendedAction, LocalDateTime computedAt) {
    }

    public List<Snapshot> findRecentByUser(long userId, int limit) throws SQLException {
        String sql = "SELECT * FROM readiness_score_history WHERE user_id = ? ORDER BY computed_at DESC LIMIT ?";
        List<Snapshot> result = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, userId);
            ps.setInt(2, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Timestamp ts = rs.getTimestamp("computed_at");
                    result.add(new Snapshot(
                            rs.getDouble("overall_score"),
                            rs.getDouble("assessment_component"),
                            rs.getDouble("topic_component"),
                            rs.getDouble("mistake_component"),
                            rs.getDouble("resume_component"),
                            rs.getDouble("mock_component"),
                            safeDouble(rs, "technical_dimension"),
                            safeDouble(rs, "problem_solving_dimension"),
                            safeDouble(rs, "communication_dimension"),
                            safeDouble(rs, "behavioral_dimension"),
                            safeDouble(rs, "role_alignment_dimension"),
                            safeString(rs, "biggest_strength"),
                            safeString(rs, "biggest_risk"),
                            safeString(rs, "next_recommended_action"),
                            ts == null ? null : ts.toLocalDateTime()));
                }
            }
        }
        return result;
    }

    /** Tolerates a pre-migration database that doesn't have the newer dimension columns yet. */
    private double safeDouble(ResultSet rs, String column) {
        try {
            return rs.getDouble(column);
        } catch (SQLException e) {
            return 0.0;
        }
    }

    private String safeString(ResultSet rs, String column) {
        try {
            return rs.getString(column);
        } catch (SQLException e) {
            return null;
        }
    }
}
