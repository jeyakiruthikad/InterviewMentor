package com.careerintelligence.ui;

import com.careerintelligence.model.Assessment;
import com.careerintelligence.model.OverallStats;
import com.careerintelligence.model.TopicPerformance;
import com.careerintelligence.model.User;
import com.careerintelligence.service.DashboardService;

import java.sql.SQLException;
import java.util.List;
import java.util.Scanner;

/**
 * Performance Dashboard: overall accuracy, average/best score, topic-wise
 * performance, strong/weak topics, and recent results.
 */
public class DashboardMenu {

    private final Scanner scanner;
    private final DashboardService dashboardService;

    public DashboardMenu(Scanner scanner, DashboardService dashboardService) {
        this.scanner = scanner;
        this.dashboardService = dashboardService;
    }

    public void show(User user) {
        try {
            DashboardService.DashboardData data = dashboardService.buildDashboard(user.getUserId());
            OverallStats stats = data.overallStats();

            ConsoleIO.printHeader("PERFORMANCE DASHBOARD - " + user.getFullName());

            if (stats.getTotalAssessments() == 0) {
                System.out.println("No completed assessments yet. Take an assessment to see your dashboard!");
                ConsoleIO.pause(scanner);
                return;
            }

            System.out.println("OVERALL");
            System.out.printf("  Assessments completed : %d%n", stats.getTotalAssessments());
            System.out.printf("  Overall accuracy       : %.2f%% (%d correct / %d wrong / %d unanswered)%n",
                    stats.getOverallAccuracyPercent(), stats.getTotalCorrect(), stats.getTotalWrong(), stats.getTotalUnanswered());
            System.out.printf("  Average score          : %.2f%%%n", stats.getAverageScorePercent());
            System.out.printf("  Best score              : %.2f%%%n", stats.getBestScorePercent());

            System.out.println("\nTOPIC-WISE PERFORMANCE");
            List<TopicPerformance> allTopics = data.topicPerformance();
            if (allTopics.isEmpty()) {
                System.out.println("  No topic data yet.");
            } else {
                System.out.printf("  %-25s %-10s %-12s %-10s%n", "Topic", "Accuracy", "Attempts", "Next Diff.");
                ConsoleIO.printDivider();
                for (TopicPerformance tp : allTopics) {
                    System.out.printf("  %-25s %8.1f%%  %-12d %-10s%n",
                            tp.getTopicName(), tp.getAccuracyPercent().doubleValue(),
                            tp.getAttemptsCount(), tp.getCurrentDifficulty());
                }
            }

            System.out.println("\nSTRONG TOPICS");
            printTopicList(data.strongTopics());

            System.out.println("\nWEAK TOPICS");
            printTopicList(data.weakTopics());

            System.out.println("\nRECENT RESULTS");
            List<Assessment> recent = data.recentResults();
            if (recent.isEmpty()) {
                System.out.println("  None yet.");
            } else {
                for (Assessment a : recent) {
                    double pct = (a.getMaxScore() != null && a.getMaxScore().signum() > 0)
                            ? a.getTotalScore().doubleValue() / a.getMaxScore().doubleValue() * 100.0 : 0.0;
                    System.out.printf("  #%-4d %-28s %-10s %6.2f%%  %s%n",
                            a.getAssessmentId(), truncate(a.getTitle(), 28), a.getMode(), pct, a.getCreatedAt());
                }
            }

            ConsoleIO.pause(scanner);
        } catch (SQLException e) {
            ConsoleIO.printError("Database error while building dashboard: " + e.getMessage());
            ConsoleIO.pause(scanner);
        }
    }

    private void printTopicList(List<TopicPerformance> topics) {
        if (topics.isEmpty()) {
            System.out.println("  Not enough data yet.");
            return;
        }
        for (TopicPerformance tp : topics) {
            System.out.printf("  - %-25s %.1f%%%n", tp.getTopicName(), tp.getAccuracyPercent().doubleValue());
        }
    }

    private String truncate(String text, int maxLen) {
        if (text == null) return "";
        return text.length() <= maxLen ? text : text.substring(0, maxLen - 3) + "...";
    }
}
