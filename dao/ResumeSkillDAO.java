package com.careerintelligence.dao;

import com.careerintelligence.database.DatabaseConnection;
import com.careerintelligence.model.ResumeSkill;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * DAO for `resume_skills`: the structured, categorised, per-user skill set
 * extracted from an uploaded resume by ai.AIService#extractSkillsFromResume.
 * Each skill is optionally matched to a `topics` row so it can drive
 * resume-based question generation (service.ResumeService) and weak-skill
 * detection against topic_performance.
 */
public class ResumeSkillDAO {

    /**
     * Replaces the full skill set for a user in one transaction (delete then
     * batch-insert), so re-analysing a resume always reflects only the
     * latest extraction rather than accumulating stale skills.
     */
    public void replaceForUser(long userId, List<ResumeSkill> skills) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection()) {
            boolean originalAutoCommit = conn.getAutoCommit();
            conn.setAutoCommit(false);
            try {
                try (PreparedStatement del = conn.prepareStatement("DELETE FROM resume_skills WHERE user_id = ?")) {
                    del.setLong(1, userId);
                    del.executeUpdate();
                }
                if (skills != null && !skills.isEmpty()) {
                    String sql = "INSERT INTO resume_skills (user_id, skill_name, category, matched_topic_id) "
                            + "VALUES (?, ?, ?, ?)";
                    try (PreparedStatement ps = conn.prepareStatement(sql)) {
                        for (ResumeSkill s : skills) {
                            ps.setLong(1, userId);
                            ps.setString(2, s.getSkillName());
                            ps.setString(3, s.getCategory() == null ? "General" : s.getCategory());
                            if (s.getMatchedTopicId() != null) {
                                ps.setInt(4, s.getMatchedTopicId());
                            } else {
                                ps.setNull(4, Types.INTEGER);
                            }
                            ps.addBatch();
                        }
                        ps.executeBatch();
                    }
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

    public List<ResumeSkill> findByUser(long userId) throws SQLException {
        String sql = "SELECT rs.*, t.topic_name FROM resume_skills rs "
                + "LEFT JOIN topics t ON t.topic_id = rs.matched_topic_id "
                + "WHERE rs.user_id = ? ORDER BY rs.category, rs.skill_name";
        List<ResumeSkill> result = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(mapRow(rs));
                }
            }
        }
        return result;
    }

    private ResumeSkill mapRow(ResultSet rs) throws SQLException {
        ResumeSkill s = new ResumeSkill();
        s.setId(rs.getLong("id"));
        s.setUserId(rs.getLong("user_id"));
        s.setSkillName(rs.getString("skill_name"));
        s.setCategory(rs.getString("category"));
        int matchedTopicId = rs.getInt("matched_topic_id");
        s.setMatchedTopicId(rs.wasNull() ? null : matchedTopicId);
        s.setMatchedTopicName(rs.getString("topic_name"));
        Timestamp extractedAt = rs.getTimestamp("extracted_at");
        if (extractedAt != null) {
            s.setExtractedAt(extractedAt.toLocalDateTime());
        }
        return s;
    }
}
