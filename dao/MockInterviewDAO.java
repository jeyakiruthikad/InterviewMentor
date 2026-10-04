package com.careerintelligence.dao;

import com.careerintelligence.database.DatabaseConnection;
import com.careerintelligence.model.MockInterviewQuestion;
import com.careerintelligence.model.MockInterviewSession;
import com.careerintelligence.model.MockSessionStatus;

import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * DAO for `mock_interview_sessions` and `mock_interview_questions`: powers
 * the AI Mock Interview feature (role/topic selection, AI-generated
 * questions and dynamic follow-ups, per-answer AI evaluation, and the
 * final session feedback/readiness contribution).
 */
public class MockInterviewDAO {

    public MockInterviewSession createSession(MockInterviewSession session) throws SQLException {
        String sql = "INSERT INTO mock_interview_sessions (user_id, role_name, company_name, topic_ids, status, total_questions) "
                + "VALUES (?, ?, ?, ?, ?, ?)";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, session.getUserId());
            ps.setString(2, session.getRoleName());
            ps.setString(3, session.getCompanyName());
            ps.setString(4, session.getTopicIds().stream().map(String::valueOf).collect(Collectors.joining(",")));
            ps.setString(5, session.getStatus().name());
            ps.setInt(6, session.getTotalQuestions());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    session.setSessionId(keys.getLong(1));
                }
            }
        }
        return session;
    }

    public MockInterviewQuestion addQuestion(MockInterviewQuestion question) throws SQLException {
        String sql = "INSERT INTO mock_interview_questions (session_id, question_order, question_text, is_followup, parent_question_id) "
                + "VALUES (?, ?, ?, ?, ?)";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, question.getSessionId());
            ps.setInt(2, question.getQuestionOrder());
            ps.setString(3, question.getQuestionText());
            ps.setBoolean(4, question.isFollowup());
            if (question.getParentQuestionId() != null) {
                ps.setLong(5, question.getParentQuestionId());
            } else {
                ps.setNull(5, Types.BIGINT);
            }
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    question.setId(keys.getLong(1));
                }
            }
        }
        return question;
    }

    public void saveAnswer(Long questionId, String answerText, Integer aiScore, String aiFeedback) throws SQLException {
        String sql = "UPDATE mock_interview_questions SET answer_text = ?, ai_score = ?, ai_feedback = ?, "
                + "answered_at = CURRENT_TIMESTAMP WHERE id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, answerText);
            if (aiScore != null) {
                ps.setInt(2, aiScore);
            } else {
                ps.setNull(2, Types.INTEGER);
            }
            ps.setString(3, aiFeedback);
            ps.setLong(4, questionId);
            ps.executeUpdate();
        }
    }

    public List<MockInterviewQuestion> findQuestions(Long sessionId) throws SQLException {
        String sql = "SELECT * FROM mock_interview_questions WHERE session_id = ? ORDER BY question_order";
        List<MockInterviewQuestion> result = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, sessionId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(mapQuestionRow(rs));
                }
            }
        }
        return result;
    }

    public int maxQuestionOrder(Long sessionId) throws SQLException {
        String sql = "SELECT COALESCE(MAX(question_order), 0) FROM mock_interview_questions WHERE session_id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, sessionId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    /** Finalises a session: average score, readiness score, status and completion time. */
    public void completeSession(Long sessionId, BigDecimal averageScore, BigDecimal readinessScore) throws SQLException {
        String sql = "UPDATE mock_interview_sessions SET status = 'COMPLETED', average_score = ?, "
                + "readiness_score = ?, completed_at = CURRENT_TIMESTAMP WHERE session_id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setBigDecimal(1, averageScore);
            ps.setBigDecimal(2, readinessScore);
            ps.setLong(3, sessionId);
            ps.executeUpdate();
        }
    }

    public Optional<MockInterviewSession> findById(Long sessionId) throws SQLException {
        String sql = "SELECT * FROM mock_interview_sessions WHERE session_id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, sessionId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapSessionRow(rs));
                }
            }
        }
        return Optional.empty();
    }

    public List<MockInterviewSession> findByUser(Long userId) throws SQLException {
        String sql = "SELECT * FROM mock_interview_sessions WHERE user_id = ? ORDER BY created_at DESC";
        List<MockInterviewSession> result = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(mapSessionRow(rs));
                }
            }
        }
        return result;
    }

    /** Average of average_score across a user's completed sessions (most recent N), for the readiness score. */
    public List<MockInterviewSession> findRecentCompleted(Long userId, int limit) throws SQLException {
        String sql = "SELECT * FROM mock_interview_sessions WHERE user_id = ? AND status = 'COMPLETED' "
                + "ORDER BY completed_at DESC LIMIT ?";
        List<MockInterviewSession> result = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, userId);
            ps.setInt(2, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(mapSessionRow(rs));
                }
            }
        }
        return result;
    }

    /**
     * The user's most recently answered mock interview questions across all
     * of their sessions (completed or in-progress), most recent first. Used
     * by {@code ReadinessScoreService} to find real behavioral/STAR answers
     * for the Behavioral readiness dimension, independent of whether the
     * session containing them has been finalised yet.
     */
    public List<MockInterviewQuestion> findRecentAnsweredByUser(Long userId, int limit) throws SQLException {
        String sql = "SELECT q.* FROM mock_interview_questions q "
                + "JOIN mock_interview_sessions s ON q.session_id = s.session_id "
                + "WHERE s.user_id = ? AND q.answered_at IS NOT NULL "
                + "ORDER BY q.answered_at DESC LIMIT ?";
        List<MockInterviewQuestion> result = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, userId);
            ps.setInt(2, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(mapQuestionRow(rs));
                }
            }
        }
        return result;
    }

    private MockInterviewQuestion mapQuestionRow(ResultSet rs) throws SQLException {
        MockInterviewQuestion q = new MockInterviewQuestion();
        q.setId(rs.getLong("id"));
        q.setSessionId(rs.getLong("session_id"));
        q.setQuestionOrder(rs.getInt("question_order"));
        q.setQuestionText(rs.getString("question_text"));
        q.setFollowup(rs.getBoolean("is_followup"));
        long parent = rs.getLong("parent_question_id");
        q.setParentQuestionId(rs.wasNull() ? null : parent);
        q.setAnswerText(rs.getString("answer_text"));
        int aiScore = rs.getInt("ai_score");
        q.setAiScore(rs.wasNull() ? null : aiScore);
        q.setAiFeedback(rs.getString("ai_feedback"));
        Timestamp answeredAt = rs.getTimestamp("answered_at");
        if (answeredAt != null) q.setAnsweredAt(answeredAt.toLocalDateTime());
        Timestamp createdAt = rs.getTimestamp("created_at");
        if (createdAt != null) q.setCreatedAt(createdAt.toLocalDateTime());
        return q;
    }

    private MockInterviewSession mapSessionRow(ResultSet rs) throws SQLException {
        MockInterviewSession s = new MockInterviewSession();
        s.setSessionId(rs.getLong("session_id"));
        s.setUserId(rs.getLong("user_id"));
        s.setRoleName(rs.getString("role_name"));
        s.setCompanyName(rs.getString("company_name"));
        String topicIdsCsv = rs.getString("topic_ids");
        List<Integer> topicIds = new ArrayList<>();
        if (topicIdsCsv != null && !topicIdsCsv.isBlank()) {
            for (String part : topicIdsCsv.split(",")) {
                try {
                    topicIds.add(Integer.parseInt(part.trim()));
                } catch (NumberFormatException ignored) {
                    // skip malformed token
                }
            }
        }
        s.setTopicIds(topicIds);
        String statusStr = rs.getString("status");
        s.setStatus(statusStr == null ? MockSessionStatus.IN_PROGRESS : MockSessionStatus.valueOf(statusStr));
        s.setTotalQuestions(rs.getInt("total_questions"));
        s.setAverageScore(rs.getBigDecimal("average_score"));
        s.setReadinessScore(rs.getBigDecimal("readiness_score"));
        Timestamp createdAt = rs.getTimestamp("created_at");
        if (createdAt != null) s.setCreatedAt(createdAt.toLocalDateTime());
        Timestamp completedAt = rs.getTimestamp("completed_at");
        if (completedAt != null) s.setCompletedAt(completedAt.toLocalDateTime());
        return s;
    }
}
