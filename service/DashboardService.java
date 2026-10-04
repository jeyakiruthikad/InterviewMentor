package com.careerintelligence.service;

import com.careerintelligence.dao.AssessmentDAO;
import com.careerintelligence.dao.TopicPerformanceDAO;
import com.careerintelligence.model.Assessment;
import com.careerintelligence.model.OverallStats;
import com.careerintelligence.model.TopicPerformance;

import java.sql.SQLException;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Aggregates data for the Performance Dashboard: overall accuracy,
 * average/best score, topic-wise performance, strong/weak topics, and
 * recent results. Pure read-side aggregation - all the underlying counters
 * are maintained by AssessmentService as assessments are completed.
 */
public class DashboardService {

    private static final int RECENT_RESULTS_LIMIT = 5;
    private static final int STRONG_WEAK_LIMIT = 3;

    private final AssessmentDAO assessmentDAO = new AssessmentDAO();
    private final TopicPerformanceDAO topicPerformanceDAO = new TopicPerformanceDAO();

    public record DashboardData(
            OverallStats overallStats,
            List<TopicPerformance> topicPerformance,
            List<TopicPerformance> strongTopics,
            List<TopicPerformance> weakTopics,
            List<Assessment> recentResults) {
    }

    public DashboardData buildDashboard(Long userId) throws SQLException {
        OverallStats stats = assessmentDAO.getOverallStats(userId);
        List<TopicPerformance> allTopics = topicPerformanceDAO.findAllByUser(userId).stream()
                .filter(tp -> tp.getAttemptsCount() > 0)
                .collect(Collectors.toList());

        List<TopicPerformance> strong = allTopics.stream()
                .sorted(Comparator.comparing(TopicPerformance::getAccuracyPercent).reversed())
                .limit(STRONG_WEAK_LIMIT)
                .collect(Collectors.toList());

        List<TopicPerformance> weak = allTopics.stream()
                .sorted(Comparator.comparing(TopicPerformance::getAccuracyPercent))
                .limit(STRONG_WEAK_LIMIT)
                .collect(Collectors.toList());

        List<Assessment> recent = assessmentDAO.findRecent(userId, RECENT_RESULTS_LIMIT);

        return new DashboardData(stats, allTopics, strong, weak, recent);
    }
}
