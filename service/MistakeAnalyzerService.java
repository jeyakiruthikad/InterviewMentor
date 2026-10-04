package com.careerintelligence.service;

import com.careerintelligence.dao.MistakeLogDAO;
import com.careerintelligence.dao.TopicPerformanceDAO;
import com.careerintelligence.model.MistakeEntry;
import com.careerintelligence.model.TopicPerformance;

import java.sql.SQLException;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Business logic for the Mistake Analyzer + Retry feature:
 *  - identifying weak topics (low accuracy) from topic_performance
 *  - identifying repeated mistakes (a question missed 2+ times) from mistake_log
 *  - listing unresolved mistakes so the UI can offer a retry
 *
 * Actually building and running the retry assessment itself is handled by
 * {@link AssessmentService#createRetryAssessment}, which this service does
 * not duplicate - it only supplies the question ids to retry.
 */
public class MistakeAnalyzerService {

    /** Below this accuracy, a topic (with at least one attempt) is flagged as "weak". */
    private static final double WEAK_TOPIC_THRESHOLD = 60.0;

    private final MistakeLogDAO mistakeLogDAO = new MistakeLogDAO();
    private final TopicPerformanceDAO topicPerformanceDAO = new TopicPerformanceDAO();

    public List<TopicPerformance> getWeakTopics(Long userId) throws SQLException {
        return topicPerformanceDAO.findAllByUser(userId).stream()
                .filter(tp -> tp.getAttemptsCount() > 0 && tp.getAccuracyPercent().doubleValue() < WEAK_TOPIC_THRESHOLD)
                .collect(Collectors.toList());
    }

    public List<TopicPerformance> getStrongTopics(Long userId, int limit) throws SQLException {
        return topicPerformanceDAO.findAllByUser(userId).stream()
                .filter(tp -> tp.getAttemptsCount() > 0)
                .sorted((a, b) -> b.getAccuracyPercent().compareTo(a.getAccuracyPercent()))
                .limit(limit)
                .collect(Collectors.toList());
    }

    /**
     * All topics the user has attempted at least once, regardless of
     * accuracy - used e.g. by {@code CareerIntelligenceService} to derive
     * an overall adaptive-difficulty baseline for the AI Mock Interview,
     * the same signal {@link com.careerintelligence.util.AdaptiveDifficultyCalculator}
     * already drives the Adaptive Assessment engine with.
     */
    public List<TopicPerformance> getAllAttemptedTopics(Long userId) throws SQLException {
        return topicPerformanceDAO.findAllByUser(userId).stream()
                .filter(tp -> tp.getAttemptsCount() > 0)
                .collect(Collectors.toList());
    }

    /** Questions the user has gotten wrong 2 or more times - the most persistent weak spots. */
    public List<MistakeEntry> getRepeatedMistakes(Long userId) throws SQLException {
        return mistakeLogDAO.findRepeated(userId, 2);
    }

    /** All currently-unresolved mistakes (wrong/unanswered and not yet answered correctly since). */
    public List<MistakeEntry> getUnresolvedMistakes(Long userId) throws SQLException {
        return mistakeLogDAO.findUnresolvedByUser(userId);
    }

    public List<MistakeEntry> getUnresolvedMistakesForTopic(Long userId, int topicId) throws SQLException {
        return mistakeLogDAO.findUnresolvedByUserAndTopic(userId, topicId);
    }
}
