package com.careerintelligence.dao;

import com.careerintelligence.database.DatabaseConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * DAO for `job_match_results`: an audit snapshot of every Resume + JD
 * match computed by {@code service.ResumeService#matchResumeToJobDescription},
 * so the Career Intelligence pipeline can show the latest job match without
 * recomputing it, and so match history/trend is observable over time -
 * mirroring how `readiness_score_history` and `career_refresh_log` already
 * persist an audit trail for the other pipeline stages.
 */
public class JobMatchResultDAO {

    private static final String JOIN_DELIM = "|";
    private static final String SPLIT_DELIM = "\\|";

    public record Row(Long id, Long jobDescriptionId, Long userId, double matchScore,
                       List<String> strongMatch, List<String> partialMatch, List<String> weakEvidence,
                       List<String> missing, List<String> highPriority, String summary,
                       LocalDateTime computedAt) {
    }

    public Long save(Long jobDescriptionId, Long userId, double matchScore, List<String> strongMatch,
                      List<String> partialMatch, List<String> weakEvidence, List<String> missing,
                      List<String> highPriority, String summary) throws SQLException {
        String sql = "INSERT INTO job_match_results "
                + "(job_description_id, user_id, match_score, strong_match_skills, partial_match_skills, "
                + "weak_evidence_skills, missing_skills, high_priority_skills, summary) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, jobDescriptionId);
            ps.setLong(2, userId);
            ps.setDouble(3, matchScore);
            ps.setString(4, join(strongMatch));
            ps.setString(5, join(partialMatch));
            ps.setString(6, join(weakEvidence));
            ps.setString(7, join(missing));
            ps.setString(8, join(highPriority));
            ps.setString(9, summary);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getLong(1);
                }
            }
        }
        return null;
    }

    public Optional<Row> findLatestByUser(long userId) throws SQLException {
        String sql = "SELECT * FROM job_match_results WHERE user_id = ? ORDER BY computed_at DESC LIMIT 1";
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

    public List<Row> findRecentByUser(long userId, int limit) throws SQLException {
        String sql = "SELECT * FROM job_match_results WHERE user_id = ? ORDER BY computed_at DESC LIMIT ?";
        List<Row> result = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, userId);
            ps.setInt(2, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(mapRow(rs));
                }
            }
        }
        return result;
    }

    private String join(List<String> values) {
        if (values == null || values.isEmpty()) {
            return "";
        }
        return String.join(JOIN_DELIM, values);
    }

    private List<String> split(String stored) {
        if (stored == null || stored.isBlank()) {
            return List.of();
        }
        return Arrays.stream(stored.split(SPLIT_DELIM)).filter(s -> !s.isBlank()).toList();
    }

    private Row mapRow(ResultSet rs) throws SQLException {
        Timestamp ts = rs.getTimestamp("computed_at");
        long jobDescriptionId = rs.getLong("job_description_id");
        return new Row(
                rs.getLong("id"),
                rs.wasNull() ? null : jobDescriptionId,
                rs.getLong("user_id"),
                rs.getDouble("match_score"),
                split(rs.getString("strong_match_skills")),
                split(rs.getString("partial_match_skills")),
                split(rs.getString("weak_evidence_skills")),
                split(rs.getString("missing_skills")),
                split(rs.getString("high_priority_skills")),
                rs.getString("summary"),
                ts == null ? null : ts.toLocalDateTime());
    }
}
