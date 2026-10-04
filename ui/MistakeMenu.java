package com.careerintelligence.ui;

import com.careerintelligence.model.*;
import com.careerintelligence.service.AssessmentService;
import com.careerintelligence.service.MistakeAnalyzerService;

import java.sql.SQLException;
import java.util.List;
import java.util.Scanner;
import java.util.stream.Collectors;

/**
 * Mistake Analyzer + Retry screen: shows weak topics (low accuracy) and
 * repeated mistakes (a question missed 2+ times), and lets the user
 * immediately retry every currently-unresolved incorrect/unanswered
 * question as a new timed assessment (mode = RETRY).
 */
public class MistakeMenu {

    private final Scanner scanner;
    private final MistakeAnalyzerService mistakeAnalyzerService;
    private final AssessmentService assessmentService;
    private final AssessmentMenu assessmentMenu;

    public MistakeMenu(Scanner scanner, MistakeAnalyzerService mistakeAnalyzerService,
                        AssessmentService assessmentService, AssessmentMenu assessmentMenu) {
        this.scanner = scanner;
        this.mistakeAnalyzerService = mistakeAnalyzerService;
        this.assessmentService = assessmentService;
        this.assessmentMenu = assessmentMenu;
    }

    public void show(User user) {
        try {
            List<TopicPerformance> weakTopics = mistakeAnalyzerService.getWeakTopics(user.getUserId());
            List<MistakeEntry> repeated = mistakeAnalyzerService.getRepeatedMistakes(user.getUserId());
            List<MistakeEntry> unresolved = mistakeAnalyzerService.getUnresolvedMistakes(user.getUserId());

            ConsoleIO.printHeader("MISTAKE ANALYZER");

            System.out.println("Weak topics (accuracy below 60%):");
            if (weakTopics.isEmpty()) {
                System.out.println("  None yet - keep taking assessments to build up topic accuracy data.");
            } else {
                for (TopicPerformance tp : weakTopics) {
                    System.out.printf("  - %-25s accuracy %.1f%% (%d/%d correct)%n",
                            tp.getTopicName(), tp.getAccuracyPercent().doubleValue(),
                            tp.getCorrectCount(), tp.getAttemptsCount());
                }
            }

            System.out.println("\nRepeated mistakes (missed 2+ times):");
            if (repeated.isEmpty()) {
                System.out.println("  None yet.");
            } else {
                for (MistakeEntry m : repeated) {
                    System.out.printf("  - [%s] %s (missed %d times, %s)%n",
                            m.getTopicName(), truncate(m.getQuestionText(), 70), m.getTimesWrong(),
                            m.isResolved() ? "resolved since" : "still unresolved");
                }
            }

            System.out.println("\nUnresolved mistakes available to retry: " + unresolved.size());

            if (unresolved.isEmpty()) {
                ConsoleIO.pause(scanner);
                return;
            }

            System.out.println("\n1. Retry ALL unresolved mistakes");
            System.out.println("2. Back to main menu");
            int choice = ConsoleIO.promptInt(scanner, "Choose an option", 1, 2);
            if (choice != 1) {
                return;
            }

            List<Long> questionIds = unresolved.stream().map(MistakeEntry::getQuestionId).collect(Collectors.toList());
            // Keep retry sessions focused: one minute per question, chosen automatically.
            int durationMinutes = Math.max(1, Math.min(120, questionIds.size()));

            Assessment retryAssessment = assessmentService.createRetryAssessment(
                    user.getUserId(), questionIds, durationMinutes, "Retry - Mistake Review");
            assessmentMenu.runAssessment(retryAssessment);

        } catch (SQLException e) {
            ConsoleIO.printError("Database error while loading mistakes: " + e.getMessage());
            ConsoleIO.pause(scanner);
        } catch (IllegalStateException e) {
            ConsoleIO.printError(e.getMessage());
            ConsoleIO.pause(scanner);
        }
    }

    private String truncate(String text, int maxLen) {
        if (text == null) return "";
        return text.length() <= maxLen ? text : text.substring(0, maxLen - 3) + "...";
    }
}
