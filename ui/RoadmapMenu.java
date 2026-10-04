package com.careerintelligence.ui;

import com.careerintelligence.model.CareerRefreshTrigger;
import com.careerintelligence.model.LearningRoadmap;
import com.careerintelligence.model.RoadmapItem;
import com.careerintelligence.model.User;
import com.careerintelligence.service.CareerIntelligenceService;
import com.careerintelligence.service.GamificationService;
import com.careerintelligence.service.RoadmapService;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.Scanner;

/**
 * UI for the Personalised Learning Roadmap: shows the user's most
 * recently generated roadmap, lets them regenerate it on demand, and lets
 * them check off items as completed. Manual regeneration goes through
 * {@link CareerIntelligenceService#refreshAfterActivity} - the exact same
 * entry point used automatically after an assessment, mistake retry or
 * mock interview - so there is only one code path that ever regenerates a
 * roadmap.
 */
public class RoadmapMenu {

    private final Scanner scanner;
    private final RoadmapService roadmapService;
    private final GamificationService gamificationService;
    private final CareerIntelligenceService careerIntelligenceService;

    public RoadmapMenu(Scanner scanner, RoadmapService roadmapService, GamificationService gamificationService) {
        this(scanner, roadmapService, gamificationService, new CareerIntelligenceService());
    }

    public RoadmapMenu(Scanner scanner, RoadmapService roadmapService, GamificationService gamificationService,
                        CareerIntelligenceService careerIntelligenceService) {
        this.scanner = scanner;
        this.roadmapService = roadmapService;
        this.gamificationService = gamificationService;
        this.careerIntelligenceService = careerIntelligenceService;
    }

    public void show(User user) {
        boolean back = false;
        while (!back) {
            ConsoleIO.printHeader("PERSONALISED LEARNING ROADMAP");
            System.out.println("1. View my latest roadmap");
            System.out.println("2. Generate a fresh roadmap (uses your current resume, mistakes & scores)");
            System.out.println("3. Mark a roadmap item complete / pending");
            System.out.println("4. Back to Main Menu");
            int choice = ConsoleIO.promptInt(scanner, "Choose an option", 1, 4);

            switch (choice) {
                case 1 -> viewLatest(user);
                case 2 -> generate(user);
                case 3 -> toggleItem(user);
                case 4 -> back = true;
            }
        }
    }

    private void viewLatest(User user) {
        try {
            Optional<LearningRoadmap> maybe = roadmapService.getLatest(user.getUserId());
            if (maybe.isEmpty()) {
                ConsoleIO.printInfo("You don't have a roadmap yet. Choose option 2 to generate one.");
            } else {
                printRoadmap(maybe.get());
            }
        } catch (SQLException e) {
            ConsoleIO.printError("Database error loading roadmap: " + e.getMessage());
        }
        ConsoleIO.pause(scanner);
    }

    private void generate(User user) {
        try {
            ConsoleIO.printInfo("Analyzing your resume skills, skill gaps, mistakes, assessment performance "
                    + "and readiness score...");
            LearningRoadmap roadmap = careerIntelligenceService.refreshAfterActivity(
                    user.getUserId(), CareerRefreshTrigger.MANUAL_ROADMAP_REQUEST);
            printRoadmap(roadmap);

            GamificationService.AwardResult award = gamificationService.awardRoadmapGenerated(user.getUserId());
            GamificationUi.printAward(award);
        } catch (SQLException e) {
            ConsoleIO.printError("Database error generating roadmap: " + e.getMessage());
        }
        ConsoleIO.pause(scanner);
    }

    private void toggleItem(User user) {
        try {
            Optional<LearningRoadmap> maybe = roadmapService.getLatest(user.getUserId());
            if (maybe.isEmpty() || maybe.get().getItems().isEmpty()) {
                ConsoleIO.printInfo("No roadmap items to update. Generate a roadmap first.");
                ConsoleIO.pause(scanner);
                return;
            }
            LearningRoadmap roadmap = maybe.get();
            printRoadmap(roadmap);
            int itemNumber = ConsoleIO.promptInt(scanner, "Enter the item number to toggle (0 to cancel)",
                    0, roadmap.getItems().size());
            if (itemNumber == 0) {
                return;
            }
            RoadmapItem item = roadmap.getItems().get(itemNumber - 1);
            boolean nowComplete = item.getStatus() != com.careerintelligence.model.RoadmapItemStatus.COMPLETED;
            roadmapService.markItemComplete(item.getId(), nowComplete);
            ConsoleIO.printSuccess("Marked \"" + item.getTitle() + "\" as " + (nowComplete ? "COMPLETED" : "PENDING") + ".");
        } catch (SQLException e) {
            ConsoleIO.printError("Database error updating roadmap item: " + e.getMessage());
        }
        ConsoleIO.pause(scanner);
    }

    private void printRoadmap(LearningRoadmap roadmap) {
        System.out.println("Generated: " + roadmap.getGeneratedAt());
        if (roadmap.getReadinessScore() != null) {
            System.out.printf("Readiness at time of generation: %.1f/100 (%s)%n",
                    roadmap.getReadinessScore(), roadmap.getReadinessBand());
        }
        ConsoleIO.printDivider();
        System.out.println(roadmap.getSummary());
        ConsoleIO.printDivider();

        List<RoadmapItem> items = roadmap.getItems();
        for (int i = 0; i < items.size(); i++) {
            RoadmapItem item = items.get(i);
            String box = item.getStatus() == com.careerintelligence.model.RoadmapItemStatus.COMPLETED ? "[x]" : "[ ]";
            System.out.printf("%d. %s (%s / %s) %s%n", i + 1, box, item.getCategory(), item.getPriority(), item.getTitle());
            System.out.println("     " + item.getDescription());
        }
    }
}
