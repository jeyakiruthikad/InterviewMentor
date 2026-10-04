package com.careerintelligence.ui;

import com.careerintelligence.dao.ReadinessScoreDAO;
import com.careerintelligence.model.User;
import com.careerintelligence.service.ProgressAnalyticsService;

import java.sql.SQLException;
import java.util.List;
import java.util.Scanner;

/**
 * UI for Progress Analytics: readiness trend over time, per-topic momentum
 * (improving/declining/stable), overall accuracy and answering-speed trends
 * across completed assessments, mistake resolution rate, and roadmap
 * completion % - all derived from data the app already records (readiness
 * history, topic_performance, assessments, mistake log, roadmap), so this
 * screen is purely a connected view, not a new data source.
 */
public class ProgressAnalyticsMenu {

    private final Scanner scanner;
    private final ProgressAnalyticsService progressAnalyticsService;

    public ProgressAnalyticsMenu(Scanner scanner, ProgressAnalyticsService progressAnalyticsService) {
        this.scanner = scanner;
        this.progressAnalyticsService = progressAnalyticsService;
    }

    public void show(User user) {
        ConsoleIO.printHeader("PROGRESS ANALYTICS - " + user.getFullName());
        try {
            ProgressAnalyticsService.AnalyticsSummary summary = progressAnalyticsService.buildSummary(user.getUserId());

            System.out.println("== Readiness Trend ==");
            List<ReadinessScoreDAO.Snapshot> history = summary.readinessHistory();
            if (history.isEmpty()) {
                System.out.println("No readiness history yet - it's recorded each time your Readiness Score is computed.");
            } else {
                // history is most-recent-first; print oldest-to-newest so the trend reads left to right.
                for (int i = history.size() - 1; i >= 0; i--) {
                    ReadinessScoreDAO.Snapshot s = history.get(i);
                    System.out.printf("  %s  ->  %.1f/final releasen", s.computedAt(), s.overallScore());
                }
                if (history.size() >= 2) {
                    double delta = history.get(0).overallScore() - history.get(history.size() - 1).overallScore();
                    System.out.printf("Change over this window: %+.1f points.%n", delta);
                }
            }

            System.out.println("\n== Topic Improvement ==");
            if (summary.topicTrends().isEmpty()) {
                System.out.println("No topic attempts recorded yet.");
            } else {
                for (ProgressAnalyticsService.TopicTrend t : summary.topicTrends()) {
                    System.out.printf("  %-25s accuracy %5.1f%% (%d attempts) - %s%n",
                            t.topicName(), t.accuracyPercent(), t.attempts(), describeTopicTrend(t.trend()));
                }
            }

            System.out.println("\n== Accuracy & Speed Trend (across completed assessments) ==");
            System.out.println(ProgressAnalyticsService.describeTrend(summary.accuracyTrend(), "%", false));
            System.out.println(ProgressAnalyticsService.describeTrend(summary.speedTrend(), "s/question", true));

            System.out.println("\n== Mistakes ==");
            System.out.printf("Total logged: %d | Resolved: %d | Unresolved: %d%n",
                    summary.mistakeCounts().total(), summary.mistakeCounts().resolved(),
                    summary.mistakeCounts().unresolved());

            System.out.println("\n== Roadmap Progress ==");
            if (summary.roadmapTotalItems() == 0) {
                System.out.println("No roadmap generated yet (see Learning Roadmap).");
            } else {
                System.out.printf("%.1f%% complete (%d/%d items).%n", summary.roadmapCompletionPercent(),
                        summary.roadmapCompletedItems(), summary.roadmapTotalItems());
            }
        } catch (SQLException e) {
            ConsoleIO.printError("Database error: " + e.getMessage());
        }
        ConsoleIO.pause(scanner);
    }

    private String describeTopicTrend(com.careerintelligence.util.AdaptiveDifficultyCalculator.Trend trend) {
        return switch (trend) {
            case IMPROVING -> "improving";
            case DECLINING -> "declining - consider revisiting this topic";
            case STABLE -> "stable";
            case INSUFFICIENT_DATA -> "not enough recent attempts to tell yet";
        };
    }
}
