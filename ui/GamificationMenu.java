package com.careerintelligence.ui;

import com.careerintelligence.model.PointsLogEntry;
import com.careerintelligence.model.UserBadge;
import com.careerintelligence.model.UserPoints;
import com.careerintelligence.model.User;
import com.careerintelligence.service.GamificationService;

import java.util.List;
import java.util.Scanner;

/**
 * UI for gamification and achievement tracking: shows the user's points
 * total, level, current/longest streak, earned badges, and recent
 * point-earning activity.
 */
public class GamificationMenu {

    private final Scanner scanner;
    private final GamificationService gamificationService;

    public GamificationMenu(Scanner scanner, GamificationService gamificationService) {
        this.scanner = scanner;
        this.gamificationService = gamificationService;
    }

    public void show(User user) {
        ConsoleIO.printHeader("MY ACHIEVEMENTS");

        UserPoints points = gamificationService.getPoints(user.getUserId());
        System.out.printf("Total Points: %d  |  Level: %d (%d/100 into this level)%n",
                points.getTotalPoints(), points.getLevel(), points.getPointsIntoCurrentLevel());
        System.out.printf("Current Streak: %d day(s)  |  Longest Streak: %d day(s)%n",
                points.getCurrentStreakDays(), points.getLongestStreakDays());

        ConsoleIO.printDivider();
        List<UserBadge> badges = gamificationService.getBadges(user.getUserId());
        System.out.println("Badges earned (" + badges.size() + "):");
        if (badges.isEmpty()) {
            System.out.println("  None yet - complete assessments, mock interviews, and keep your streak alive!");
        } else {
            for (UserBadge b : badges) {
                System.out.println("  * " + b.getBadgeName() + " - " + b.getDescription()
                        + "  (earned " + b.getEarnedAt() + ")");
            }
        }

        ConsoleIO.printDivider();
        List<PointsLogEntry> activity = gamificationService.getRecentActivity(user.getUserId(), 10);
        System.out.println("Recent activity:");
        if (activity.isEmpty()) {
            System.out.println("  No activity yet.");
        } else {
            for (PointsLogEntry entry : activity) {
                String sign = entry.getPoints() >= 0 ? "+" : "";
                System.out.printf("  %s%d  %-40s %s%n", sign, entry.getPoints(), entry.getReason(), entry.getCreatedAt());
            }
        }

        ConsoleIO.pause(scanner);
    }
}
