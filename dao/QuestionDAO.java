package com.careerintelligence.dao;

import com.careerintelligence.database.DatabaseConnection;
import com.careerintelligence.model.DifficultyLevel;
import com.careerintelligence.model.Question;
import com.careerintelligence.model.QuestionOption;
import com.careerintelligence.model.QuestionType;

import java.sql.*;
import java.util.*;

/**
 * DAO for `questions` and `question_options`. Provides database-driven,
 * randomised question retrieval across one or more topics, which is what
 * the assessment engine (service.AssessmentService) uses to build a new
 * multi-topic assessment.
 */
public class QuestionDAO {

    /**
     * Fetches up to {@code count} random, active questions drawn from the
     * given topic ids. If more topics are selected than questions requested,
     * the random ordering still gives a natural mix across topics.
     */
    public List<Question> findRandomQuestionsByTopics(List<Integer> topicIds, int count) throws SQLException {
        if (topicIds == null || topicIds.isEmpty() || count <= 0) {
            return new ArrayList<>();
        }
        String placeholders = String.join(",", topicIds.stream().map(id -> "?").toList());
        String sql = "SELECT q.*, t.topic_name FROM questions q "
                + "JOIN topics t ON t.topic_id = q.topic_id "
                + "WHERE q.topic_id IN (" + placeholders + ") AND q.is_active = TRUE "
                + "ORDER BY RAND() LIMIT ?";

        List<Question> questions = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            int idx = 1;
            for (Integer topicId : topicIds) {
                ps.setInt(idx++, topicId);
            }
            ps.setInt(idx, count);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    questions.add(mapRow(rs));
                }
            }
        }
        // Load MCQ options for the fetched questions
        for (Question q : questions) {
            if (q.getQuestionType() == QuestionType.MCQ) {
                q.setOptions(findOptionsByQuestionId(q.getQuestionId()));
            }
        }
        return questions;
    }

    /**
     * Adaptive selection: for each topic, picks questions weighted around
     * that topic's recommended difficulty (majority at the target difficulty,
     * ~20% one level easier, ~20% one level harder, falling back to any
     * difficulty when a topic doesn't have enough questions at a band).
     * This is the query the Adaptive Quiz mode uses instead of pure
     * uniform randomness.
     */
    public List<Question> findAdaptiveQuestionsByTopics(Map<Integer, DifficultyLevel> topicDifficulties, int totalCount) throws SQLException {
        if (topicDifficulties == null || topicDifficulties.isEmpty() || totalCount <= 0) {
            return new ArrayList<>();
        }
        List<Integer> topicIds = new ArrayList<>(topicDifficulties.keySet());
        int topicCountSize = topicIds.size();
        int base = totalCount / topicCountSize;
        int remainder = totalCount % topicCountSize;

        List<Question> selected = new ArrayList<>();
        Set<Long> seenIds = new HashSet<>();

        try (Connection conn = DatabaseConnection.getConnection()) {
            for (int i = 0; i < topicIds.size(); i++) {
                Integer topicId = topicIds.get(i);
                int perTopicCount = base + (i < remainder ? 1 : 0);
                if (perTopicCount <= 0) {
                    continue;
                }
                DifficultyLevel target = topicDifficulties.getOrDefault(topicId, DifficultyLevel.EASY);
                List<DifficultyLevel> band = difficultyBand(target);

                int targetCount = (int) Math.ceil(perTopicCount * 0.6);
                int secondaryCount = perTopicCount - targetCount;

                List<Question> picked = new ArrayList<>();
                picked.addAll(fetchByTopicAndDifficulty(conn, topicId, band.get(0), targetCount, seenIds));
                int stillNeeded = perTopicCount - picked.size();
                if (stillNeeded > 0 && band.size() > 1) {
                    int half = Math.max(1, stillNeeded / 2);
                    picked.addAll(fetchByTopicAndDifficulty(conn, topicId, band.get(1), half, seenIds));
                }
                stillNeeded = perTopicCount - picked.size();
                if (stillNeeded > 0 && band.size() > 2) {
                    picked.addAll(fetchByTopicAndDifficulty(conn, topicId, band.get(2), stillNeeded, seenIds));
                }
                // Final top-up: any remaining active questions for the topic, regardless of difficulty.
                stillNeeded = perTopicCount - picked.size();
                if (stillNeeded > 0) {
                    picked.addAll(fetchByTopicAndDifficulty(conn, topicId, null, stillNeeded, seenIds));
                }
                selected.addAll(picked);
            }
        }

        for (Question q : selected) {
            if (q.getQuestionType() == QuestionType.MCQ) {
                q.setOptions(findOptionsByQuestionId(q.getQuestionId()));
            }
        }
        Collections.shuffle(selected);
        return selected;
    }

    /** Target difficulty first, then the adjacent bands to fall back on if the topic lacks enough questions. */
    private List<DifficultyLevel> difficultyBand(DifficultyLevel target) {
        return switch (target) {
            case EASY -> List.of(DifficultyLevel.EASY, DifficultyLevel.MEDIUM, DifficultyLevel.HARD);
            case MEDIUM -> List.of(DifficultyLevel.MEDIUM, DifficultyLevel.EASY, DifficultyLevel.HARD);
            case HARD -> List.of(DifficultyLevel.HARD, DifficultyLevel.MEDIUM, DifficultyLevel.EASY);
        };
    }

    private List<Question> fetchByTopicAndDifficulty(Connection conn, int topicId, DifficultyLevel difficulty,
                                                       int count, Set<Long> excludeIds) throws SQLException {
        if (count <= 0) {
            return new ArrayList<>();
        }
        StringBuilder sql = new StringBuilder(
                "SELECT q.*, t.topic_name FROM questions q JOIN topics t ON t.topic_id = q.topic_id "
                        + "WHERE q.topic_id = ? AND q.is_active = TRUE");
        if (difficulty != null) {
            sql.append(" AND q.difficulty = ?");
        }
        if (!excludeIds.isEmpty()) {
            sql.append(" AND q.question_id NOT IN (")
                    .append(String.join(",", excludeIds.stream().map(id -> "?").toList()))
                    .append(")");
        }
        sql.append(" ORDER BY RAND() LIMIT ?");

        List<Question> result = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            int idx = 1;
            ps.setInt(idx++, topicId);
            if (difficulty != null) {
                ps.setString(idx++, difficulty.name());
            }
            for (Long excludeId : excludeIds) {
                ps.setLong(idx++, excludeId);
            }
            ps.setInt(idx, count);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Question q = mapRow(rs);
                    result.add(q);
                    excludeIds.add(q.getQuestionId());
                }
            }
        }
        return result;
    }

    /** Fetches a specific set of questions by id, e.g. for building a RETRY-mode assessment. Order is not guaranteed. */
    public List<Question> findByIds(List<Long> questionIds) throws SQLException {
        if (questionIds == null || questionIds.isEmpty()) {
            return new ArrayList<>();
        }
        String placeholders = String.join(",", questionIds.stream().map(id -> "?").toList());
        String sql = "SELECT q.*, t.topic_name FROM questions q JOIN topics t ON t.topic_id = q.topic_id "
                + "WHERE q.question_id IN (" + placeholders + ")";
        List<Question> questions = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            int idx = 1;
            for (Long id : questionIds) {
                ps.setLong(idx++, id);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    questions.add(mapRow(rs));
                }
            }
        }
        for (Question q : questions) {
            if (q.getQuestionType() == QuestionType.MCQ) {
                q.setOptions(findOptionsByQuestionId(q.getQuestionId()));
            }
        }
        return questions;
    }

    public Optional<Question> findById(Long questionId) throws SQLException {
        String sql = "SELECT q.*, t.topic_name FROM questions q "
                + "JOIN topics t ON t.topic_id = q.topic_id WHERE q.question_id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, questionId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Question q = mapRow(rs);
                    if (q.getQuestionType() == QuestionType.MCQ) {
                        q.setOptions(findOptionsByQuestionId(q.getQuestionId()));
                    }
                    return Optional.of(q);
                }
            }
        }
        return Optional.empty();
    }

    public List<QuestionOption> findOptionsByQuestionId(Long questionId) throws SQLException {
        String sql = "SELECT * FROM question_options WHERE question_id = ? ORDER BY option_label";
        List<QuestionOption> options = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, questionId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    options.add(new QuestionOption(
                            rs.getLong("option_id"),
                            rs.getLong("question_id"),
                            rs.getString("option_label"),
                            rs.getString("option_text"),
                            rs.getBoolean("is_correct")));
                }
            }
        }
        return options;
    }

    public int countActiveQuestions() throws SQLException {
        String sql = "SELECT COUNT(*) FROM questions WHERE is_active = TRUE";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    // -----------------------------------------------------------------
    // Admin Module additions (final implementation): question CRUD
    // -----------------------------------------------------------------

    /** All questions for a topic, active and inactive, for the admin Question Management screen. */
    public List<Question> findAllByTopicIncludingInactive(Integer topicId) throws SQLException {
        String sql = "SELECT q.*, t.topic_name FROM questions q JOIN topics t ON t.topic_id = q.topic_id "
                + "WHERE q.topic_id = ? ORDER BY q.question_id";
        List<Question> result = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, topicId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(mapRow(rs));
                }
            }
        }
        for (Question q : result) {
            if (q.getQuestionType() == QuestionType.MCQ) {
                q.setOptions(findOptionsByQuestionId(q.getQuestionId()));
            }
        }
        return result;
    }

    /** Creates a new question (and its MCQ options, if any) in one transaction. Admin-only. */
    public Question create(Question question) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection()) {
            boolean originalAutoCommit = conn.getAutoCommit();
            conn.setAutoCommit(false);
            try {
                String sql = "INSERT INTO questions (topic_id, question_text, question_type, difficulty, "
                        + "correct_answer, explanation, marks, is_active) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
                try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                    ps.setInt(1, question.getTopicId());
                    ps.setString(2, question.getQuestionText());
                    ps.setString(3, question.getQuestionType().name());
                    ps.setString(4, question.getDifficulty().name());
                    ps.setString(5, question.getCorrectAnswer());
                    ps.setString(6, question.getExplanation());
                    ps.setInt(7, question.getMarks());
                    ps.setBoolean(8, question.isActive());
                    ps.executeUpdate();
                    try (ResultSet keys = ps.getGeneratedKeys()) {
                        if (keys.next()) {
                            question.setQuestionId(keys.getLong(1));
                        }
                    }
                }
                if (question.getQuestionType() == QuestionType.MCQ && !question.getOptions().isEmpty()) {
                    String optSql = "INSERT INTO question_options (question_id, option_label, option_text, is_correct) "
                            + "VALUES (?, ?, ?, ?)";
                    try (PreparedStatement ps = conn.prepareStatement(optSql)) {
                        for (QuestionOption opt : question.getOptions()) {
                            ps.setLong(1, question.getQuestionId());
                            ps.setString(2, opt.getOptionLabel());
                            ps.setString(3, opt.getOptionText());
                            ps.setBoolean(4, opt.isCorrect());
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
        return question;
    }

    /** Updates a question's core fields (not its options - see {@link #replaceOptions}). Admin-only. */
    public boolean update(Question question) throws SQLException {
        String sql = "UPDATE questions SET topic_id = ?, question_text = ?, question_type = ?, difficulty = ?, "
                + "correct_answer = ?, explanation = ?, marks = ?, is_active = ? WHERE question_id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, question.getTopicId());
            ps.setString(2, question.getQuestionText());
            ps.setString(3, question.getQuestionType().name());
            ps.setString(4, question.getDifficulty().name());
            ps.setString(5, question.getCorrectAnswer());
            ps.setString(6, question.getExplanation());
            ps.setInt(7, question.getMarks());
            ps.setBoolean(8, question.isActive());
            ps.setLong(9, question.getQuestionId());
            return ps.executeUpdate() > 0;
        }
    }

    /** Replaces all MCQ options for a question (delete then re-insert). Admin-only. */
    public void replaceOptions(Long questionId, List<QuestionOption> options) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection()) {
            boolean originalAutoCommit = conn.getAutoCommit();
            conn.setAutoCommit(false);
            try {
                try (PreparedStatement del = conn.prepareStatement("DELETE FROM question_options WHERE question_id = ?")) {
                    del.setLong(1, questionId);
                    del.executeUpdate();
                }
                if (options != null && !options.isEmpty()) {
                    String sql = "INSERT INTO question_options (question_id, option_label, option_text, is_correct) "
                            + "VALUES (?, ?, ?, ?)";
                    try (PreparedStatement ps = conn.prepareStatement(sql)) {
                        for (QuestionOption opt : options) {
                            ps.setLong(1, questionId);
                            ps.setString(2, opt.getOptionLabel());
                            ps.setString(3, opt.getOptionText());
                            ps.setBoolean(4, opt.isCorrect());
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

    /** Permanently deletes a question. Admin-only, irreversible. */
    public boolean delete(Long questionId) throws SQLException {
        String sql = "DELETE FROM questions WHERE question_id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, questionId);
            return ps.executeUpdate() > 0;
        }
    }

    private Question mapRow(ResultSet rs) throws SQLException {
        Question q = new Question();
        q.setQuestionId(rs.getLong("question_id"));
        q.setTopicId(rs.getInt("topic_id"));
        q.setTopicName(rs.getString("topic_name"));
        q.setQuestionText(rs.getString("question_text"));
        q.setQuestionType(QuestionType.valueOf(rs.getString("question_type")));
        q.setDifficulty(DifficultyLevel.valueOf(rs.getString("difficulty")));
        q.setCorrectAnswer(rs.getString("correct_answer"));
        q.setExplanation(rs.getString("explanation"));
        q.setMarks(rs.getInt("marks"));
        q.setActive(rs.getBoolean("is_active"));
        return q;
    }
}
