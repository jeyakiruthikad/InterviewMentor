package com.careerintelligence.ui;

import com.careerintelligence.model.User;
import com.careerintelligence.service.AdminService;
import com.careerintelligence.service.RolePrepService;

import java.util.Scanner;

/**
 * Top-level Admin Module dispatcher (complete implementation). Only ever
 * shown to a logged-in {@code User} whose role is {@code ADMIN} (enforced
 * by Main.java right after {@code AuthService#login} succeeds - the login
 * itself is the same secure PBKDF2-hashed login every user goes through).
 */
public class AdminMenu {

    private final Scanner scanner;
    private final AdminService adminService;
    private final AdminUserMenu adminUserMenu;
    private final AdminTopicMenu adminTopicMenu;
    private final AdminQuestionMenu adminQuestionMenu;
    private final AdminAssessmentMenu adminAssessmentMenu;
    private final AdminRolePrepMenu adminRolePrepMenu;

    public AdminMenu(Scanner scanner, AdminService adminService, RolePrepService rolePrepService) {
        this.scanner = scanner;
        this.adminService = adminService;
        this.adminUserMenu = new AdminUserMenu(scanner, adminService);
        this.adminTopicMenu = new AdminTopicMenu(scanner, adminService);
        this.adminQuestionMenu = new AdminQuestionMenu(scanner, adminService);
        this.adminAssessmentMenu = new AdminAssessmentMenu(scanner, adminService);
        this.adminRolePrepMenu = new AdminRolePrepMenu(scanner, rolePrepService);
    }

    /** Returns when the admin logs out. */
    public void show(User admin) {
        boolean loggedOut = false;
        while (!loggedOut) {
            ConsoleIO.printHeader("ADMIN CONSOLE - " + admin.getFullName());
            try {
                System.out.printf("Users: %d (Admins: %d)  |  Assessments recorded: %d%n",
                        adminService.getTotalUserCount(admin), adminService.getAdminCount(admin),
                        adminService.getTotalAssessmentCount(admin));
            } catch (Exception ignored) {
                // Non-fatal: the summary line is best-effort, menu still works without it.
            }
            System.out.println("1. User Management");
            System.out.println("2. Topic Management");
            System.out.println("3. Question Management");
            System.out.println("4. Assessment Management");
            System.out.println("5. Role & Company Prep Content");
            System.out.println("6. Points Leaderboard");
            System.out.println("7. Logout");
            int choice = ConsoleIO.promptInt(scanner, "Choose an option", 1, 7);

            switch (choice) {
                case 1 -> adminUserMenu.show(admin);
                case 2 -> adminTopicMenu.show(admin);
                case 3 -> adminQuestionMenu.show(admin);
                case 4 -> adminAssessmentMenu.show(admin);
                case 5 -> adminRolePrepMenu.show();
                case 6 -> showLeaderboard(admin);
                case 7 -> {
                    ConsoleIO.printInfo("Admin logged out.");
                    loggedOut = true;
                }
            }
        }
    }

    private void showLeaderboard(User admin) {
        ConsoleIO.printHeader("POINTS LEADERBOARD (TOP 10)");
        try {
            var rows = adminService.getPointsLeaderboard(admin, 10);
            if (rows.isEmpty()) {
                ConsoleIO.printInfo("No gamification activity recorded yet.");
            } else {
                int rank = 1;
                for (Object[] row : rows) {
                    System.out.printf("%2d. %-25s (@%s) - %s pts, %s day streak%n",
                            rank++, row[0], row[1], row[2], row[3]);
                }
            }
        } catch (Exception e) {
            ConsoleIO.printError("Database error: " + e.getMessage());
        }
        ConsoleIO.pause(scanner);
    }
}
