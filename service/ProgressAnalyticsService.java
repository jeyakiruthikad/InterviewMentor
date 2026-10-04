package com.careerintelligence.service;

import com.careerintelligence.dao.AssessmentDAO;
import com.careerintelligence.dao.MistakeLogDAO;
import com.careerintelligence.dao.ReadinessScoreDAO;
import com.careerintelligence.dao.RoadmapDAO;
import com.careerintelligence.dao.TopicPerformanceDAO;
import com.careerintelligence.model.Assessment;
import com.careerintelligence.model.AssessmentStatus;
import com.careerintelligence.model.LearningRoadmap;
import com.careerintelligence.model.RoadmapItem;
import com.careerintelligence.model.RoadmapItemStatus;
import com.careerintelligence.model.TopicPerformance;
import com.careerintelligence.util.AdaptiveDifficultyCalculator;

import java.sql.SQLException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Progress Analytics: a single place that turns the raw history every other
 * feature already records - readiness snapshots, per-topic accuracy/momentum,
 * completed assessments, the mistake log, and the roadmap - into trends a
 * user can act on (readiness over time, which topics are improving vs.
 * slipping, overall accuracy trend, answering speed trend, mistake-resolution
 * rate, and roadmap completion %). Every number here is derived directly
 * from existing tables; nothing is invented or estimated.
 */
public class ProgressAnalyticsService {

    /** How many of the most recent completed assessments count as "recent" vs. "earlier", for trend comparisons. */
    private static final int RECENT_WINDOW = 5;

    private final ReadinessScoreDAO readinessScoreDAO = new ReadinessScoreDAO();
    private final TopicPerformanceDAO topicPerformanceDAO = new TopicPerformanceDAO();
    private final AssessmentDAO assessmentDAO = new AssessmentDAO();
    private final MistakeLogDAO mistakeLogDAO = new MistakeLogDAO();
    private final RoadmapDAO roadmapDAO = new RoadmapDAO();

    /** One topic's momentum trend + current accuracy, for the "topic improvement" analytics section. */
    public record TopicTrend(String topicName, double accuracyPercent, int attempts,
                              AdaptiveDifficultyCalculator.Trend trend) {
    }

    public enum Direction {
        UP, DOWN, FLAT, INSUFFICIENT_DATA
    }

    /** A single before/after comparison (e.g. accuracy across recent vs. earlier assessments). */
    public record TrendMetric(String label, Double recentValue, Double earlierValue, Direction direction) {

        static TrendMetric of(String label, Double recent, Double earlier) {
            if (recent == null || earlier == null) {
                return new TrendMetric(label, recent, earlier, Direction.INSUFFICIENT_DATA);
            }
            double delta = recent - earlier;
            Direction dir = Math.abs(delta) < 0.01 ? Direction.FLAT : (delta > 0 ? Direction.UP : Direction.DOWN);
            return new TrendMetric(label, recent, earlier, dir);
        }
    }

    /** Full analytics summary returned to the UI (ui.ProgressAnalyticsMenu) in one call. */
    public record AnalyticsSummary(List<ReadinessScoreDAO.Snapshot> readinessHistory,
                                    List<TopicTrend> topicTrends,
                                    TrendMetric accuracyTrend,
                                    TrendMetric speedTrend,
                                    MistakeLogDAO.MistakeCounts mistakeCounts,
                                    double roadmapCompletionPercent,
                                    int roadmapTotalItems,
                                    int roadmapCompletedItems) {
    }

    public AnalyticsSummary buildSummary(long userId) throws SQLException {
        List<ReadinessScoreDAO.Snapshot> readinessHistory = readinessScoreDAO.findRecentByUser(userId, 20);

        List<TopicTrend> topicTrends = topicPerformanceDAO.findAllByUser(userId).stream()
                .filter(tp -> tp.getAttemptsCount() > 0)
                .map(tp -> new TopicTrend(
                        tp.getTopicName() == null ? ("Topic #" + tp.getTopicId()) : tp.getTopicName(),
                        tp.getAccuracyPercent().doubleValue(),
                        tp.getAttemptsCount(),
                        AdaptiveDifficultyCalculator.classifyTrend(tp.getRecentResults())))
                .sorted(Comparator.comparingDouble(TopicTrend::accuracyPercent))
                .collect(Collectors.toList());

        List<Assessment> completed = assessmentDAO.findHistoryByUser(userId).stream()
                .filter(a -> (a.getStatus() == AssessmentStatus.COMPLETED
                        || a.getStatus() == AssessmentStatus.AUTO_SUBMITTED) && a.getTotalQuestions() > 0)
                .sorted(Comparator.comparing(Assessment::getCreatedAt))
                .collect(Collectors.toList());

        TrendMetric accuracyTrend = buildAccuracyTrend(completed);
        TrendMetric speedTrend = buildSpeedTrend(completed);

        MistakeLogDAO.MistakeCounts mistakeCounts = mistakeLogDAO.countsByUser(userId);

        Optional<LearningRoadmap> roadmap = roadmapDAO.findLatestByUser(userId);
        int totalItems = roadmap.map(r -> r.getItems().size()).orElse(0);
        int completedItems = roadmap.map(r -> (int) r.getItems().stream()
                .filter(i -> i.getStatus() == RoadmapItemStatus.COMPLETED).count()).orElse(0);
        double roadmapPercent = totalItems == 0 ? 0.0 : Math.round(completedItems * 10000.0 / totalItems) / 100.0;

        return new AnalyticsSummary(readinessHistory, topicTrends, accuracyTrend, speedTrend,
                mistakeCounts, roadmapPercent, totalItems, completedItems);
    }

    private TrendMetric buildAccuracyTrend(List<Assessment> completedOldestFirst) {
        if (completedOldestFirst.size() < 2) {
            return TrendMetric.of("Overall accuracy", null, null);
        }
        List<Double> accuracies = completedOldestFirst.stream()
                .map(a -> a.getCorrectCount() * 100.0 / a.getTotalQuestions())
                .collect(Collectors.toList());
        return TrendMetric.of("Overall accuracy", averageOfLast(accuracies), averageOfFirst(accuracies));
    }

    private TrendMetric buildSpeedTrend(List<Assessment> completedOldestFirst) {
        List<Double> secondsPerQuestion = new ArrayList<>();
        for (Assessment a : completedOldestFirst) {
            if (a.getStartTime() == null || a.getEndTime() == null || a.getTotalQuestions() == 0) {
                continue;
            }
            long seconds = Duration.between(a.getStartTime(), a.getEndTime()).getSeconds();
            if (seconds > 0) {
                secondsPerQuestion.add(seconds / (double) a.getTotalQuestions());
            }
        }
        if (secondsPerQuestion.size() < 2) {
            return TrendMetric.of("Seconds per question", null, null);
        }
        return TrendMetric.of("Seconds per question", averageOfLast(secondsPerQuestion), averageOfFirst(secondsPerQuestion));
    }

    private double averageOfLast(List<Double> values) {
        int window = Math.min(RECENT_WINDOW, values.size());
        return values.subList(values.size() - window, values.size()).stream()
                .mapToDouble(Double::doubleValue).average().orElse(0.0);
    }

    private double averageOfFirst(List<Double> values) {
        int window = Math.min(RECENT_WINDOW, values.size());
        return values.subList(0, window).stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
    }

    /** One-line human summary of a trend metric, for console/dashboard display. */
    public static String describeTrend(TrendMetric metric, String unit, boolean lowerIsBetter) {
        if (metric.direction() == Direction.INSUFFICIENT_DATA) {
            return metric.label() + ": not enough completed assessments yet to show a trend.";
        }
        String arrow = switch (metric.direction()) {
            case UP -> lowerIsBetter ? "down" : "up";
            case DOWN -> lowerIsBetter ? "up" : "down";
            case FLAT -> "holding steady";
            default -> "n/a";
        };
        return String.format(Locale.ROOT, "%s: %.1f%s recently (was %.1f%s) - trending %s.",
                metric.label(), metric.recentValue(), unit, metric.earlierValue(), unit, arrow);
    }
}
