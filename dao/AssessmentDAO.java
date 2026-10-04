package com.careerintelligence.dao;

import com.careerintelligence.database.DatabaseConnection;
import com.careerintelligence.model.*;

import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * DAO for `assessments`, `assessment_topics` and `assessment_questions`.
 * Encapsulates everything needed to create an assessment attempt, persist
 * its fixed randomised question set, and later record the final score and
 * status (COMPLETED or AUTO_SUBMITTED by the timer).
 */
public class AssessmentDAO {

    private final QuestionDAO questionDAO = new QuestionDAO();

    public Assessment create(Assessment assessment) throws SQLException {
        String sql = "INSERT INTO assessments (user_id, title, total_questions, duration_minutes, status, mode, source_assessment_id, start_time) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, assessment.getUserId());
            ps.setString(2, assessment.getTitle());
            ps.setInt(3, assessment.getTotalQuestions());
            ps.setInt(4, assessment.getDurationMinutes());
            ps.setString(5, assessment.getStatus().name());
            ps.setString(6, (assessment.getMode() == null ? AssessmentMode.STANDARD : assessment.getMode()).name());
            if (assessment.getSourceAssessmentId() != null) {
                ps.setLong(7, assessment.getSourceAssessmentId());
            } else {
                ps.setNull(7, Types.BIGINT);
            }
            ps.setTimestamp(8, Timestamp.valueOf(assessment.getStartTime()));
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    assessment.setAssessmentId(keys.getLong(1));
                }
            }
        }
        return assessment;
    }

    public void linkTopics(Long assessmentId, List<Integer> topicIds) throws SQLException {
        String sql = "INSERT INTO assessment_topics (assessment_id, topic_id) VALUES (?, ?)";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            for (Integer topicId : topicIds) {
                ps.setLong(1, assessmentId);
                ps.setInt(2, topicId);
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    public void addQuestions(Long assessmentId, List<Long> questionIdsInOrder) throws SQLException {
        String sql = "INSERT INTO assessment_questions (assessment_id, question_id, question_order) VALUES (?, ?, ?)";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            int order = 1;
            for (Long questionId : questionIdsInOrder) {
                ps.setLong(1, assessmentId);
                ps.setLong(2, questionId);
                ps.setInt(3, order++);
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    /** Loads the fixed question set for an assessment, in presentation order, options included. */
    public List<AssessmentQuestion> getAssessmentQuestions(Long assessmentId) throws SQLException {
        String sql = "SELECT * FROM assessment_questions WHERE assessment_id = ? ORDER BY question_order";
        List<AssessmentQuestion> result = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, assessmentId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    AssessmentQuestion aq = new AssessmentQuestion();
                    aq.setId(rs.getLong("id"));
                    aq.setAssessmentId(rs.getLong("assessment_id"));
                    aq.setQuestionId(rs.getLong("question_id"));
                    aq.setQuestionOrder(rs.getInt("question_order"));
                    result.add(aq);
                }
            }
        }
        for (AssessmentQuestion aq : result) {
            Optional<Question> question = questionDAO.findById(aq.getQuestionId());
            question.ifPresent(aq::setQuestion);
        }
        return result;
    }

    /** Finalises an assessment: status, score totals and end time. */
    public void completeAssessment(Assessment assessment) throws SQLException {
        String sql = "UPDATE assessments SET status = ?, total_score = ?, max_score = ?, "
                + "correct_count = ?, wrong_count = ?, unanswered_count = ?, end_time = ? "
                + "WHERE assessment_id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, assessment.getStatus().name());
            ps.setBigDecimal(2, assessment.getTotalScore());
            ps.setBigDecimal(3, assessment.getMaxScore());
            ps.setInt(4, assessment.getCorrectCount());
            ps.setInt(5, assessment.getWrongCount());
            ps.setInt(6, assessment.getUnansweredCount());
            ps.setTimestamp(7, Timestamp.valueOf(assessment.getEndTime() == null ? LocalDateTime.now() : assessment.getEndTime()));
            ps.setLong(8, assessment.getAssessmentId());
            ps.executeUpdate();
        }
    }

    public Optional<Assessment> findById(Long assessmentId) throws SQLException {
        String sql = "SELECT * FROM assessments WHERE assessment_id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, assessmentId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRowWithTopics(conn, rs));
                }
            }
        }
        return Optional.empty();
    }

    /** Returns the assessment history for a user, most recent first. */
    public List<Assessment> findHistoryByUser(Long userId) throws SQLException {
        String sql = "SELECT * FROM assessments WHERE user_id = ? ORDER BY created_at DESC";
        List<Assessment> result = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(mapRowWithTopics(conn, rs));
                }
            }
        }
        return result;
    }

    /** Most recent N assessments for a user (used by the Performance Dashboard's "recent results" panel). */
    public List<Assessment> findRecent(Long userId, int limit) throws SQLException {
        String sql = "SELECT * FROM assessments WHERE user_id = ? ORDER BY created_at DESC LIMIT ?";
        List<Assessment> result = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, userId);
            ps.setInt(2, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(mapRowWithTopics(conn, rs));
                }
            }
        }
        return result;
    }

    /**
     * Aggregated cross-assessment statistics for the Performance Dashboard:
     * overall accuracy, average score %, best score %, and totals.
     * Only COMPLETED / AUTO_SUBMITTED assessments are counted (an
     * IN_PROGRESS or ABANDONED attempt has no final score yet).
     */
    public OverallStats getOverallStats(Long userId) throws SQLException {
        String sql = "SELECT COUNT(*) AS total_assessments, "
                + "SUM(correct_count) AS total_correct, SUM(wrong_count) AS total_wrong, "
                + "SUM(unanswered_count) AS total_unanswered, "
                + "AVG(CASE WHEN max_score > 0 THEN total_score / max_score * 100 ELSE NULL END) AS avg_pct, "
                + "MAX(CASE WHEN max_score > 0 THEN total_score / max_score * 100 ELSE NULL END) AS best_pct "
                + "FROM assessments WHERE user_id = ? AND status IN ('COMPLETED','AUTO_SUBMITTED')";
        OverallStats stats = new OverallStats();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    stats.setTotalAssessments(rs.getInt("total_assessments"));
                    int totalCorrect = rs.getInt("total_correct");
                    int totalWrong = rs.getInt("total_wrong");
                    stats.setTotalCorrect(totalCorrect);
                    stats.setTotalWrong(totalWrong);
                    stats.setTotalUnanswered(rs.getInt("total_unanswered"));
                    int gradedTotal = totalCorrect + totalWrong;
                    stats.setOverallAccuracyPercent(gradedTotal == 0 ? 0.0 : (totalCorrect * 100.0 / gradedTotal));
                    double avgPct = rs.getDouble("avg_pct");
                    stats.setAverageScorePercent(rs.wasNull() ? 0.0 : avgPct);
                    double bestPct = rs.getDouble("best_pct");
                    stats.setBestScorePercent(rs.wasNull() ? 0.0 : bestPct);
                }
            }
        }
        return stats;
    }

    // -----------------------------------------------------------------
    // Admin Module additions (final implementation): cross-user assessment management
    // -----------------------------------------------------------------

    /** Lightweight row for the admin "all assessments" listing: no per-topic join, includes the owner's username. */
    public record AdminAssessmentRow(Long assessmentId, String username, String title, AssessmentStatus status,
                                      AssessmentMode mode, BigDecimal totalScore, BigDecimal maxScore,
                                      LocalDateTime createdAt) {
    }

    public List<AdminAssessmentRow> findAllForAdmin(int limit) throws SQLException {
        String sql = "SELECT a.assessment_id, u.username, a.title, a.status, a.mode, a.total_score, a.max_score, a.created_at "
                + "FROM assessments a JOIN users u ON u.user_id = a.user_id "
                + "ORDER BY a.created_at DESC LIMIT ?";
        List<AdminAssessmentRow> result = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(new AdminAssessmentRow(
                            rs.getLong("assessment_id"), rs.getString("username"), rs.getString("title"),
                            AssessmentStatus.valueOf(rs.getString("status")),
                            rs.getString("mode") == null ? AssessmentMode.STANDARD : AssessmentMode.valueOf(rs.getString("mode")),
                            rs.getBigDecimal("total_score") == null ? BigDecimal.ZERO : rs.getBigDecimal("total_score"),
                            rs.getBigDecimal("max_score") == null ? BigDecimal.ZERO : rs.getBigDecimal("max_score"),
                            rs.getTimestamp("created_at") == null ? null : rs.getTimestamp("created_at").toLocalDateTime()));
                }
            }
        }
        return result;
    }

    /** Permanently deletes an assessment (and its topic/question links via ON DELETE CASCADE). Admin-only. */
    public boolean delete(Long assessmentId) throws SQLException {
        String sql = "DELETE FROM assessments WHERE assessment_id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, assessmentId);
            return ps.executeUpdate() > 0;
        }
    }

    /** Count of COMPLETED/AUTO_SUBMITTED assessments for a user - used by GamificationService achievement rules. */
    public int countCompletedByUser(Long userId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM assessments WHERE user_id = ? AND status IN ('COMPLETED','AUTO_SUBMITTED')";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    public int countAllForAdmin() throws SQLException {
        String sql = "SELECT COUNT(*) FROM assessments";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    private Assessment mapRowWithTopics(Connection conn, ResultSet rs) throws SQLException {
        Assessment assessment = new Assessment();
        assessment.setAssessmentId(rs.getLong("assessment_id"));
        assessment.setUserId(rs.getLong("user_id"));
        assessment.setTitle(rs.getString("title"));
        assessment.setTotalQuestions(rs.getInt("total_questions"));
        assessment.setDurationMinutes(rs.getInt("duration_minutes"));
        assessment.setStatus(AssessmentStatus.valueOf(rs.getString("status")));
        String modeStr = rs.getString("mode");
        assessment.setMode(modeStr == null ? AssessmentMode.STANDARD : AssessmentMode.valueOf(modeStr));
        long sourceId = rs.getLong("source_assessment_id");
        assessment.setSourceAssessmentId(rs.wasNull() ? null : sourceId);
        assessment.setTotalScore(rs.getBigDecimal("total_score") == null ? BigDecimal.ZERO : rs.getBigDecimal("total_score"));
        assessment.setMaxScore(rs.getBigDecimal("max_score") == null ? BigDecimal.ZERO : rs.getBigDecimal("max_score"));
        assessment.setCorrectCount(rs.getInt("correct_count"));
        assessment.setWrongCount(rs.getInt("wrong_count"));
        assessment.setUnansweredCount(rs.getInt("unanswered_count"));
        Timestamp start = rs.getTimestamp("start_time");
        if (start != null) assessment.setStartTime(start.toLocalDateTime());
        Timestamp end = rs.getTimestamp("end_time");
        if (end != null) assessment.setEndTime(end.toLocalDateTime());
        Timestamp created = rs.getTimestamp("created_at");
        if (created != null) assessment.setCreatedAt(created.toLocalDateTime());

        String topicSql = "SELECT t.topic_id, t.topic_name FROM assessment_topics at "
                + "JOIN topics t ON t.topic_id = at.topic_id WHERE at.assessment_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(topicSql)) {
            ps.setLong(1, assessment.getAssessmentId());
            try (ResultSet trs = ps.executeQuery()) {
                while (trs.next()) {
                    assessment.getTopicIds().add(trs.getInt("topic_id"));
                    assessment.getTopicNames().add(trs.getString("topic_name"));
                }
            }
        }
        return assessment;
    }
}
