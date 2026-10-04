package com.careerintelligence.ui;

import com.careerintelligence.dao.AssessmentDAO;
import com.careerintelligence.model.User;
import com.careerintelligence.service.AdminService;

import java.sql.SQLException;
import java.util.List;
import java.util.Scanner;

/** Admin Assessment Management screen: cross-user visibility + record deletion (e.g. for abuse/cleanup). */
public class AdminAssessmentMenu {

    private final Scanner scanner;
    private final AdminService adminService;

    public AdminAssessmentMenu(Scanner scanner, AdminService adminService) {
        this.scanner = scanner;
        this.adminService = adminService;
    }

    public void show(User admin) {
        boolean back = false;
        while (!back) {
            ConsoleIO.printHeader("ASSESSMENT MANAGEMENT (most recent 25, across all users)");
            List<AssessmentDAO.AdminAssessmentRow> rows;
            try {
                rows = adminService.getRecentAssessments(admin, 25);
            } catch (SQLException e) {
                ConsoleIO.printError("Database error: " + e.getMessage());
                ConsoleIO.pause(scanner);
                return;
            }
            if (rows.isEmpty()) {
                ConsoleIO.printInfo("No assessments recorded yet.");
            } else {
                System.out.printf("%-6s %-16s %-10s %-14s %-10s %s%n", "ID", "User", "Status", "Mode", "Score", "Created");
                ConsoleIO.printDivider();
                for (AssessmentDAO.AdminAssessmentRow r : rows) {
                    System.out.printf("%-6d %-16s %-10s %-14s %-10s %s%n", r.assessmentId(), r.username(),
                            r.status(), r.mode(), r.totalScore() + "/" + r.maxScore(), r.createdAt());
                }
            }

            System.out.println("\n1. Delete an assessment record");
            System.out.println("2. Back");
            int choice = ConsoleIO.promptInt(scanner, "Choose an option", 1, 2);
            if (choice == 1) {
                deleteAssessment(admin);
            } else {
                back = true;
            }
        }
    }

    private void deleteAssessment(User admin) {
        long id = ConsoleIO.promptInt(scanner, "Enter assessment ID (0 to cancel)", 0, Integer.MAX_VALUE);
        if (id == 0) return;
        String confirm = ConsoleIO.prompt(scanner, "Type DELETE to permanently remove this assessment record");
        if (!confirm.equalsIgnoreCase("DELETE")) {
            ConsoleIO.printInfo("Cancelled.");
            return;
        }
        AdminService.Result result = adminService.deleteAssessment(admin, id);
        if (result.success()) ConsoleIO.printSuccess(result.message());
        else ConsoleIO.printError(result.message());
        ConsoleIO.pause(scanner);
    }
}
