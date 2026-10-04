package com.careerintelligence.dao;

import com.careerintelligence.database.DatabaseConnection;
import com.careerintelligence.model.UserAnswer;

import java.math.BigDecimal;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * DAO for `user_answers`. Uses an upsert (ON DUPLICATE KEY UPDATE) keyed on
 * (assessment_id, question_id) so a question can be answered, and re-answered
 * before final submission, without extra existence checks.
 */
public class UserAnswerDAO {

    public void saveOrUpdateAnswer(UserAnswer answer) throws SQLException {
        String sql = "INSERT INTO user_answers "
                + "(assessment_id, question_id, selected_option_id, answer_text, is_correct, marks_obtained, ai_score, ai_feedback) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?) "
                + "ON DUPLICATE KEY UPDATE selected_option_id = VALUES(selected_option_id), "
                + "answer_text = VALUES(answer_text), is_correct = VALUES(is_correct), "
                + "marks_obtained = VALUES(marks_obtained), ai_score = VALUES(ai_score), "
                + "ai_feedback = VALUES(ai_feedback), answered_at = CURRENT_TIMESTAMP";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, answer.getAssessmentId());
            ps.setLong(2, answer.getQuestionId());
            if (answer.getSelectedOptionId() != null) {
                ps.setLong(3, answer.getSelectedOptionId());
            } else {
                ps.setNull(3, Types.BIGINT);
            }
            ps.setString(4, answer.getAnswerText());
            if (answer.getCorrect() != null) {
                ps.setBoolean(5, answer.getCorrect());
            } else {
                ps.setNull(5, Types.BOOLEAN);
            }
            ps.setBigDecimal(6, answer.getMarksObtained() == null ? BigDecimal.ZERO : answer.getMarksObtained());
            if (answer.getAiScore() != null) {
                ps.setInt(7, answer.getAiScore());
            } else {
                ps.setNull(7, Types.INTEGER);
            }
            ps.setString(8, answer.getAiFeedback());
            ps.executeUpdate();
        }
    }

    public List<UserAnswer> findByAssessment(Long assessmentId) throws SQLException {
        String sql = "SELECT * FROM user_answers WHERE assessment_id = ?";
        List<UserAnswer> answers = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, assessmentId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    UserAnswer answer = new UserAnswer();
                    answer.setAnswerId(rs.getLong("answer_id"));
                    answer.setAssessmentId(rs.getLong("assessment_id"));
                    answer.setQuestionId(rs.getLong("question_id"));
                    long selectedOption = rs.getLong("selected_option_id");
                    answer.setSelectedOptionId(rs.wasNull() ? null : selectedOption);
                    answer.setAnswerText(rs.getString("answer_text"));
                    boolean correct = rs.getBoolean("is_correct");
                    answer.setCorrect(rs.wasNull() ? null : correct);
                    answer.setMarksObtained(rs.getBigDecimal("marks_obtained"));
                    int aiScore = rs.getInt("ai_score");
                    answer.setAiScore(rs.wasNull() ? null : aiScore);
                    answer.setAiFeedback(rs.getString("ai_feedback"));
                    Timestamp answeredAt = rs.getTimestamp("answered_at");
                    if (answeredAt != null) {
                        answer.setAnsweredAt(answeredAt.toLocalDateTime());
                    }
                    answers.add(answer);
                }
            }
        }
        return answers;
    }

    public record DescriptiveScoreStats(int count, double averageAiScore) {
    }

    /**
     * Average AI-graded score (0-100) across all of a user's DESCRIPTIVE
     * answers that have actually been graded, joined through `assessments`
     * since `user_answers` itself has no user_id column. Used by the
     * Communication dimension of {@code service.ReadinessScoreService}:
     * how well the user explains/articulates open-ended answers is a much
     * closer proxy for interview communication skill than MCQ accuracy is.
     */
    public DescriptiveScoreStats getDescriptiveScoreStats(long userId) throws SQLException {
        String sql = "SELECT COUNT(*) AS cnt, AVG(ua.ai_score) AS avg_score "
                + "FROM user_answers ua "
                + "JOIN assessments a ON a.assessment_id = ua.assessment_id "
                + "WHERE a.user_id = ? AND ua.ai_score IS NOT NULL";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    int count = rs.getInt("cnt");
                    double avg = rs.getDouble("avg_score");
                    return new DescriptiveScoreStats(count, rs.wasNull() ? 0.0 : avg);
                }
            }
        }
        return new DescriptiveScoreStats(0, 0.0);
    }
}
