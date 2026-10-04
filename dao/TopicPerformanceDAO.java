package com.careerintelligence.dao;

import com.careerintelligence.database.DatabaseConnection;
import com.careerintelligence.model.DifficultyLevel;
import com.careerintelligence.model.TopicPerformance;
import com.careerintelligence.util.AdaptiveDifficultyCalculator;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * DAO for `topic_performance`: the running per-user, per-topic accuracy
 * that drives both the Adaptive Quiz engine (service.AssessmentService)
 * and the topic-wise breakdown on the Performance Dashboard.
 *
 * Difficulty recommendation rule (applied in {@link #recordResult}, see
 * {@code util.AdaptiveDifficultyCalculator} for the exact, unit-tested logic):
 *   - fewer than 3 attempts on the topic  -> EASY (not enough data yet)
 *   - baseline from cumulative accuracy: &gt;=75% HARD, &gt;=45% MEDIUM, else EASY
 *   - momentum override: last 3 wrong in a row -> forced EASY regardless of
 *     the cumulative baseline (a recent cold streak always wins); last 3
 *     correct in a row -> baseline bumped up one level (capped at HARD)
 */
public class TopicPerformanceDAO {

    public Optional<TopicPerformance> find(long userId, int topicId) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection()) {
            return find(conn, userId, topicId);
        }
    }

    private Optional<TopicPerformance> find(Connection conn, long userId, int topicId) throws SQLException {
        String sql = "SELECT * FROM topic_performance WHERE user_id = ? AND topic_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, userId);
            ps.setInt(2, topicId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
            }
        }
        return Optional.empty();
    }

    public List<TopicPerformance> findAllByUser(long userId) throws SQLException {
        String sql = "SELECT tp.*, t.topic_name FROM topic_performance tp "
                + "JOIN topics t ON t.topic_id = tp.topic_id WHERE tp.user_id = ? ORDER BY tp.accuracy_percent DESC";
        List<TopicPerformance> result = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    TopicPerformance tp = mapRow(rs);
                    tp.setTopicName(rs.getString("topic_name"));
                    result.add(tp);
                }
            }
        }
        return result;
    }

    /**
     * Records one graded answer's outcome for a topic: increments the
     * attempt/correct/wrong counters, recomputes accuracy, and recomputes
     * the recommended next difficulty for that topic.
     */
    public void recordResult(long userId, int topicId, boolean correct) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection()) {
            TopicPerformance tp = find(conn, userId, topicId).orElseGet(() -> {
                TopicPerformance t = new TopicPerformance();
                t.setUserId(userId);
                t.setTopicId(topicId);
                return t;
            });
            tp.setAttemptsCount(tp.getAttemptsCount() + 1);
            if (correct) {
                tp.setCorrectCount(tp.getCorrectCount() + 1);
            } else {
                tp.setWrongCount(tp.getWrongCount() + 1);
            }
            double accuracy = tp.getAttemptsCount() == 0 ? 0.0
                    : (tp.getCorrectCount() * 100.0 / tp.getAttemptsCount());
            tp.setAccuracyPercent(BigDecimal.valueOf(accuracy).setScale(2, RoundingMode.HALF_UP));
            tp.setRecentResults(AdaptiveDifficultyCalculator.appendResult(tp.getRecentResults(), correct));
            tp.setCurrentDifficulty(AdaptiveDifficultyCalculator
                    .deriveDifficulty(accuracy, tp.getAttemptsCount(), tp.getRecentResults()));
            tp.setLastAttemptAt(LocalDateTime.now());
            upsert(conn, tp);
        }
    }

    /** Recent-momentum trend (IMPROVING/DECLINING/STABLE) for this topic, for Progress Analytics. */
    public AdaptiveDifficultyCalculator.Trend getTrend(long userId, int topicId) throws SQLException {
        return find(userId, topicId)
                .map(tp -> AdaptiveDifficultyCalculator.classifyTrend(tp.getRecentResults()))
                .orElse(AdaptiveDifficultyCalculator.Trend.INSUFFICIENT_DATA);
    }

    private void upsert(Connection conn, TopicPerformance tp) throws SQLException {
        String sql = "INSERT INTO topic_performance "
                + "(user_id, topic_id, attempts_count, correct_count, wrong_count, accuracy_percent, current_difficulty, recent_results, last_attempt_at) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?) "
                + "ON DUPLICATE KEY UPDATE attempts_count = VALUES(attempts_count), correct_count = VALUES(correct_count), "
                + "wrong_count = VALUES(wrong_count), accuracy_percent = VALUES(accuracy_percent), "
                + "current_difficulty = VALUES(current_difficulty), recent_results = VALUES(recent_results), "
                + "last_attempt_at = VALUES(last_attempt_at)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, tp.getUserId());
            ps.setInt(2, tp.getTopicId());
            ps.setInt(3, tp.getAttemptsCount());
            ps.setInt(4, tp.getCorrectCount());
            ps.setInt(5, tp.getWrongCount());
            ps.setBigDecimal(6, tp.getAccuracyPercent());
            ps.setString(7, tp.getCurrentDifficulty().name());
            ps.setString(8, tp.getRecentResults());
            ps.setTimestamp(9, Timestamp.valueOf(tp.getLastAttemptAt()));
            ps.executeUpdate();
        }
    }

    private TopicPerformance mapRow(ResultSet rs) throws SQLException {
        TopicPerformance tp = new TopicPerformance();
        tp.setId(rs.getLong("id"));
        tp.setUserId(rs.getLong("user_id"));
        tp.setTopicId(rs.getInt("topic_id"));
        tp.setAttemptsCount(rs.getInt("attempts_count"));
        tp.setCorrectCount(rs.getInt("correct_count"));
        tp.setWrongCount(rs.getInt("wrong_count"));
        tp.setAccuracyPercent(rs.getBigDecimal("accuracy_percent") == null ? BigDecimal.ZERO : rs.getBigDecimal("accuracy_percent"));
        tp.setCurrentDifficulty(DifficultyLevel.valueOf(rs.getString("current_difficulty")));
        try {
            tp.setRecentResults(rs.getString("recent_results"));
        } catch (SQLException e) {
            // Legacy databases may not contain recent-result history;
            // momentum simply starts fresh; cumulative-accuracy difficulty still works fine.
            tp.setRecentResults("");
        }
        Timestamp last = rs.getTimestamp("last_attempt_at");
        if (last != null) tp.setLastAttemptAt(last.toLocalDateTime());
        Timestamp updated = rs.getTimestamp("updated_at");
        if (updated != null) tp.setUpdatedAt(updated.toLocalDateTime());
        return tp;
    }
}
