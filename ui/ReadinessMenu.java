package com.careerintelligence.ui;

import com.careerintelligence.dao.ReadinessScoreDAO;
import com.careerintelligence.model.ReadinessScore;
import com.careerintelligence.model.User;
import com.careerintelligence.service.GamificationService;
import com.careerintelligence.service.ReadinessScoreService;

import java.sql.SQLException;
import java.util.List;
import java.util.Scanner;

/**
 * UI for the Interview Readiness Score feature (current implementation): computes
 * and displays the blended 0-100 readiness score with a breakdown of every
 * component that fed into it, plus a short history of past computations.
 */
public class ReadinessMenu {

    private final Scanner scanner;
    private final ReadinessScoreService readinessScoreService;
    private final GamificationService gamificationService;

    public ReadinessMenu(Scanner scanner, ReadinessScoreService readinessScoreService, GamificationService gamificationService) {
        this.scanner = scanner;
        this.readinessScoreService = readinessScoreService;
        this.gamificationService = gamificationService;
    }

    public void show(User user) {
        ConsoleIO.printHeader("INTERVIEW READINESS SCORE");
        try {
            ReadinessScore score = readinessScoreService.compute(user.getUserId());

            System.out.printf("Overall Readiness Score: %.2f / 100  ->  %s%n", score.getOverallScore(), score.getBand());
            ConsoleIO.printDivider();

            if (score.getComponents().isEmpty()) {
                System.out.println("No data yet - take an assessment, log a mistake, upload a resume, or do a "
                        + "mock interview, then check back here.");
            } else {
                System.out.println("Breakdown (only components you have data for are included, weights renormalised):");
                for (ReadinessScore.Component c : score.getComponents().values()) {
                    System.out.printf("  %-28s %6.2f/100  (weight %d%%)  - %s%n",
                            c.getLabel(), c.getValue(), c.getWeight(), c.getNote());
                }
            }

            List<String> missingComponents = missingComponentHints(score);
            if (!missingComponents.isEmpty()) {
                System.out.println("\nTo get a fuller picture, also try:");
                for (String hint : missingComponents) {
                    System.out.println("  - " + hint);
                }
            }

            List<ReadinessScoreDAO.Snapshot> history = readinessScoreService.getHistory(user.getUserId(), 5);
            if (history.size() > 1) {
                System.out.println("\nRecent history (most recent first):");
                for (ReadinessScoreDAO.Snapshot s : history) {
                    System.out.printf("  %.2f/100 on %s%n", s.overallScore(), s.computedAt());
                }
            }

            GamificationService.AwardResult award = gamificationService.checkReadinessBadge(user.getUserId(), score.getOverallScore());
            GamificationUi.printAward(award);
        } catch (SQLException e) {
            ConsoleIO.printError("Database error while computing readiness score: " + e.getMessage());
        }
        ConsoleIO.pause(scanner);
    }

    private List<String> missingComponentHints(ReadinessScore score) {
        List<String> hints = new java.util.ArrayList<>();
        if (!score.getComponents().containsKey("assessment")) {
            hints.add("Complete a Standard/Adaptive assessment (main menu -> Start New Assessment)");
        }
        if (!score.getComponents().containsKey("resume")) {
            hints.add("Upload your resume (main menu -> Resume & Skills)");
        }
        if (!score.getComponents().containsKey("mock")) {
            hints.add("Complete an AI Mock Interview (main menu -> AI Mock Interview)");
        }
        return hints;
    }
}

