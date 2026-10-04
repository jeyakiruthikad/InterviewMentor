package com.careerintelligence;

import com.careerintelligence.database.DatabaseConnection;
import com.careerintelligence.demo.DemoFlow;
import com.careerintelligence.ui.Ansi;
import com.careerintelligence.ui.Layout;
import com.careerintelligence.model.User;
import com.careerintelligence.model.UserRole;
import com.careerintelligence.service.AdminService;
import com.careerintelligence.service.AssessmentService;
import com.careerintelligence.service.AuthService;
import com.careerintelligence.service.GamificationService;
import com.careerintelligence.service.RolePrepService;
import com.careerintelligence.ui.AdminMenu;
import com.careerintelligence.ui.AuthMenu;
import com.careerintelligence.ui.ConsoleIO;
import com.careerintelligence.ui.MainMenu;

import java.util.Scanner;

/** Application entry point for InterviewMentor. */
public class Main {
    public static void main(String[] args) {
        if (hasFlag(args, "--no-color")) {
            Ansi.setEnabled(false);
        } else if (hasFlag(args, "--color")) {
            Ansi.setEnabled(true);
        }

        // The guided demo is fully self-contained: no MySQL, no API key, no login.
        // It is checked before the database so it always runs, which is exactly
        // what makes it dependable on an unfamiliar machine.
        if (hasFlag(args, "--demo")) {
            try (Scanner demoScanner = new Scanner(System.in)) {
                new DemoFlow(demoScanner, !hasFlag(args, "--auto")).run();
            }
            return;
        }

        Layout.print(Layout.banner("INTERVIEWMENTOR",
                "Resume Intelligence \u2022 Adaptive Assessments \u2022 AI Evaluation \u2022 "
                        + "Mock Interviews \u2022 Roadmaps"));
        System.out.println();
        System.out.println("Connecting to MySQL...");

        if (!DatabaseConnection.testConnection()) {
            printConnectionHelp();
            try (Scanner scanner = new Scanner(System.in)) {
                if (ConsoleIO.promptYesNo(scanner, "Run the guided demo instead?", true)) {
                    new DemoFlow(scanner, true).run();
                }
            }
            return;
        }
        ConsoleIO.printSuccess("Connected to MySQL successfully.");

        try (Scanner scanner = new Scanner(System.in)) {
            AuthService authService = new AuthService();
            AssessmentService assessmentService = new AssessmentService();
            GamificationService gamificationService = new GamificationService();
            AuthMenu authMenu = new AuthMenu(scanner, authService);
            MainMenu mainMenu = new MainMenu(scanner, authService, assessmentService);
            AdminMenu adminMenu = new AdminMenu(scanner, new AdminService(), new RolePrepService());

            boolean exit = false;
            while (!exit) {
                User user = authMenu.show();
                if (user == null) {
                    exit = true;
                } else {
                    GamificationService.AwardResult loginAward =
                            gamificationService.recordDailyLogin(user.getUserId());
                    if (loginAward.points().getCurrentStreakDays() > 1) {
                        ConsoleIO.printInfo("You are on a " + loginAward.points().getCurrentStreakDays()
                                + "-day practice streak. Keep it going!");
                    }
                    if (user.getRole() == UserRole.ADMIN) {
                        adminMenu.show(user);
                    } else {
                        mainMenu.show(user);
                    }
                }
            }
        }

        System.out.println();
        ConsoleIO.printInfo("Thank you for using InterviewMentor. Goodbye!");
    }

    private static boolean hasFlag(String[] args, String flag) {
        if (args == null) {
            return false;
        }
        for (String a : args) {
            if (a != null && a.equalsIgnoreCase(flag)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Actionable guidance when the database is unreachable. A failed
     * connection is the single most common first-run problem, so this
     * spells out each cause and - importantly - points at the guided demo,
     * which needs no database at all.
     */
    private static void printConnectionHelp() {
        System.out.println();
        ConsoleIO.printError("Could not connect to MySQL.");
        System.out.println();
        System.out.println("Check each of the following:");
        System.out.println("  1. MySQL Server is running and reachable on the configured host/port.");
        System.out.println("  2. database/schema.sql has been executed.");
        System.out.println("  3. database/migration_v2_job_intelligence.sql has been executed.");
        System.out.println("  4. database/seed_data.sql has been executed (optional demo data).");
        System.out.println("  5. Your .env file contains the correct DB_HOST, DB_PORT, DB_NAME, DB_USER and DB_PASSWORD (DB_URL is optional).");
        System.out.println();
        ConsoleIO.printInfo("No database handy? Run the full guided demo instead - it needs nothing:");
        System.out.println("      java -jar target/interviewmentor.jar --demo");
        System.out.println();
        System.out.println("See README.md for full setup instructions.");
    }
}
