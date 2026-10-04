package com.careerintelligence.dao;

import com.careerintelligence.database.DatabaseConnection;
import com.careerintelligence.model.MistakeEntry;
import com.careerintelligence.model.QuestionType;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * DAO for `mistake_log`: tracks how many times each user has gotten each
 * question wrong (or left it unanswered), and whether it has since been
 * resolved (answered correctly). Powers the Mistake Analyzer screen and
 * the "retry incorrect questions" flow.
 */
public class MistakeLogDAO {

    /** Records (or increments) a wrong/unanswered outcome for a question. */
    public void recordWrong(long userId, long questionId, int topicId) throws SQLException {
        String sql = "INSERT INTO mistake_log (user_id, question_id, topic_id, times_wrong, resolved, last_wrong_at) "
                + "VALUES (?, ?, ?, 1, FALSE, CURRENT_TIMESTAMP) "
                + "ON DUPLICATE KEY UPDATE times_wrong = times_wrong + 1, resolved = FALSE, "
                + "last_wrong_at = CURRENT_TIMESTAMP, resolved_at = NULL";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, userId);
            ps.setLong(2, questionId);
            ps.setInt(3, topicId);
            ps.executeUpdate();
        }
    }

    /** Marks a question resolved for this user (answered correctly), keeping the historical times_wrong count. */
    public void recordCorrect(long userId, long questionId) throws SQLException {
        String sql = "UPDATE mistake_log SET resolved = TRUE, resolved_at = CURRENT_TIMESTAMP "
                + "WHERE user_id = ? AND question_id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, userId);
            ps.setLong(2, questionId);
            ps.executeUpdate();
        }
    }

    /** All unresolved mistakes for a user (candidates for retry), most recently wrong first. */
    public List<MistakeEntry> findUnresolvedByUser(long userId) throws SQLException {
        String sql = "SELECT m.*, q.question_text, q.question_type, t.topic_name FROM mistake_log m "
                + "JOIN questions q ON q.question_id = m.question_id "
                + "JOIN topics t ON t.topic_id = m.topic_id "
                + "WHERE m.user_id = ? AND m.resolved = FALSE ORDER BY m.last_wrong_at DESC";
        return query(sql, userId);
    }

    /** Unresolved mistakes for a user restricted to one topic (used for topic-scoped retry). */
    public List<MistakeEntry> findUnresolvedByUserAndTopic(long userId, int topicId) throws SQLException {
        String sql = "SELECT m.*, q.question_text, q.question_type, t.topic_name FROM mistake_log m "
                + "JOIN questions q ON q.question_id = m.question_id "
                + "JOIN topics t ON t.topic_id = m.topic_id "
                + "WHERE m.user_id = ? AND m.topic_id = ? AND m.resolved = FALSE ORDER BY m.last_wrong_at DESC";
        List<MistakeEntry> result = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, userId);
            ps.setInt(2, topicId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(mapRow(rs));
                }
            }
        }
        return result;
    }

    /** Repeated mistakes (wrong 2+ times), regardless of current resolved state - highlights persistently weak spots. */
    public List<MistakeEntry> findRepeated(long userId, int minTimesWrong) throws SQLException {
        String sql = "SELECT m.*, q.question_text, q.question_type, t.topic_name FROM mistake_log m "
                + "JOIN questions q ON q.question_id = m.question_id "
                + "JOIN topics t ON t.topic_id = m.topic_id "
                + "WHERE m.user_id = ? AND m.times_wrong >= ? ORDER BY m.times_wrong DESC, m.last_wrong_at DESC";
        List<MistakeEntry> result = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, userId);
            ps.setInt(2, minTimesWrong);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(mapRow(rs));
                }
            }
        }
        return result;
    }

    /** Aggregate resolved/unresolved counts for a user, used by service.ReadinessScoreService's mistake component. */
    public record MistakeCounts(int total, int resolved, int unresolved) {
    }

    public MistakeCounts countsByUser(long userId) throws SQLException {
        String sql = "SELECT COUNT(*) AS total, SUM(CASE WHEN resolved = TRUE THEN 1 ELSE 0 END) AS resolved "
                + "FROM mistake_log WHERE user_id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    int total = rs.getInt("total");
                    int resolved = rs.getInt("resolved");
                    return new MistakeCounts(total, resolved, total - resolved);
                }
            }
        }
        return new MistakeCounts(0, 0, 0);
    }

    private List<MistakeEntry> query(String sql, long userId) throws SQLException {
        List<MistakeEntry> result = new ArrayList<>();
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

    private MistakeEntry mapRow(ResultSet rs) throws SQLException {
        MistakeEntry m = new MistakeEntry();
        m.setId(rs.getLong("id"));
        m.setUserId(rs.getLong("user_id"));
        m.setQuestionId(rs.getLong("question_id"));
        m.setQuestionText(rs.getString("question_text"));
        m.setQuestionType(QuestionType.valueOf(rs.getString("question_type")));
        m.setTopicId(rs.getInt("topic_id"));
        m.setTopicName(rs.getString("topic_name"));
        m.setTimesWrong(rs.getInt("times_wrong"));
        m.setResolved(rs.getBoolean("resolved"));
        Timestamp lastWrong = rs.getTimestamp("last_wrong_at");
        if (lastWrong != null) m.setLastWrongAt(lastWrong.toLocalDateTime());
        Timestamp resolvedAt = rs.getTimestamp("resolved_at");
        if (resolvedAt != null) m.setResolvedAt(resolvedAt.toLocalDateTime());
        return m;
    }
}
