package com.careerintelligence.ui;

import com.careerintelligence.model.CompanyProfile;
import com.careerintelligence.model.DifficultyLevel;
import com.careerintelligence.model.RolePrepQuestion;
import com.careerintelligence.service.RolePrepService;

import java.sql.SQLException;
import java.util.List;
import java.util.Scanner;

/** Admin screen for managing Role & Company Preparation content: prep questions and company profiles. */
public class AdminRolePrepMenu {

    private final Scanner scanner;
    private final RolePrepService rolePrepService;

    public AdminRolePrepMenu(Scanner scanner, RolePrepService rolePrepService) {
        this.scanner = scanner;
        this.rolePrepService = rolePrepService;
    }

    public void show() {
        boolean back = false;
        while (!back) {
            ConsoleIO.printHeader("ROLE & COMPANY PREP CONTENT");
            System.out.println("1. List / add a prep question");
            System.out.println("2. Delete a prep question");
            System.out.println("3. List / add a company profile");
            System.out.println("4. Delete a company profile");
            System.out.println("5. Back");
            int choice = ConsoleIO.promptInt(scanner, "Choose an option", 1, 5);

            switch (choice) {
                case 1 -> addQuestion();
                case 2 -> deleteQuestion();
                case 3 -> addCompany();
                case 4 -> deleteCompany();
                case 5 -> back = true;
            }
        }
    }

    private void addQuestion() {
        try {
            List<RolePrepQuestion> existing = rolePrepService.adminFindAllQuestions();
            System.out.println("Existing questions (" + existing.size() + "):");
            for (RolePrepQuestion q : existing) {
                System.out.printf("[%d] %s / %s: %s%n", q.getId(), q.getRoleName(),
                        q.getCompanyName() == null ? "Any company" : q.getCompanyName(), q.getQuestionText());
            }
        } catch (SQLException e) {
            ConsoleIO.printError("Database error: " + e.getMessage());
            ConsoleIO.pause(scanner);
            return;
        }

        String role = ConsoleIO.prompt(scanner, "Role name");
        String company = ConsoleIO.prompt(scanner, "Company name (blank = generic for this role)");
        String text = ConsoleIO.prompt(scanner, "Question text");
        String category = ConsoleIO.prompt(scanner, "Category (Technical / Behavioral / System Design ...)");
        String diffStr = ConsoleIO.prompt(scanner, "Difficulty (EASY / MEDIUM / HARD)").trim().toUpperCase();
        DifficultyLevel difficulty;
        try {
            difficulty = DifficultyLevel.valueOf(diffStr);
        } catch (IllegalArgumentException e) {
            ConsoleIO.printError("Invalid difficulty.");
            ConsoleIO.pause(scanner);
            return;
        }
        if (role.isBlank() || text.isBlank()) {
            ConsoleIO.printError("Role name and question text are required.");
            ConsoleIO.pause(scanner);
            return;
        }

        RolePrepQuestion q = new RolePrepQuestion();
        q.setRoleName(role.trim());
        q.setCompanyName(company.isBlank() ? null : company.trim());
        q.setQuestionText(text.trim());
        q.setCategory(category.isBlank() ? "General" : category.trim());
        q.setDifficulty(difficulty);
        q.setActive(true);

        try {
            rolePrepService.adminCreateQuestion(q);
            ConsoleIO.printSuccess("Prep question added (ID " + q.getId() + ").");
        } catch (SQLException e) {
            ConsoleIO.printError("Database error: " + e.getMessage());
        }
        ConsoleIO.pause(scanner);
    }

    private void deleteQuestion() {
        long id = ConsoleIO.promptInt(scanner, "Enter prep question ID (0 to cancel)", 0, Integer.MAX_VALUE);
        if (id == 0) return;
        try {
            boolean deleted = rolePrepService.adminDeleteQuestion(id);
            if (deleted) ConsoleIO.printSuccess("Prep question deleted.");
            else ConsoleIO.printError("No prep question with that ID.");
        } catch (SQLException e) {
            ConsoleIO.printError("Database error: " + e.getMessage());
        }
        ConsoleIO.pause(scanner);
    }

    private void addCompany() {
        try {
            List<CompanyProfile> existing = rolePrepService.adminFindAllCompanies();
            System.out.println("Existing companies (" + existing.size() + "):");
            for (CompanyProfile c : existing) {
                System.out.printf("[%d] %s (%s)%n", c.getCompanyId(), c.getCompanyName(), c.getIndustry());
            }
        } catch (SQLException e) {
            ConsoleIO.printError("Database error: " + e.getMessage());
            ConsoleIO.pause(scanner);
            return;
        }

        String name = ConsoleIO.prompt(scanner, "Company name");
        if (name.isBlank()) {
            ConsoleIO.printError("Company name is required.");
            ConsoleIO.pause(scanner);
            return;
        }
        String industry = ConsoleIO.prompt(scanner, "Industry");
        String process = ConsoleIO.prompt(scanner, "Typical interview process");
        String notes = ConsoleIO.prompt(scanner, "Notes (optional)");

        CompanyProfile profile = new CompanyProfile();
        profile.setCompanyName(name.trim());
        profile.setIndustry(industry);
        profile.setInterviewProcess(process);
        profile.setNotes(notes);
        profile.setActive(true);

        try {
            rolePrepService.adminSaveCompany(profile);
            ConsoleIO.printSuccess("Company profile saved.");
        } catch (SQLException e) {
            ConsoleIO.printError("Database error: " + e.getMessage());
        }
        ConsoleIO.pause(scanner);
    }

    private void deleteCompany() {
        int id = ConsoleIO.promptInt(scanner, "Enter company ID (0 to cancel)", 0, Integer.MAX_VALUE);
        if (id == 0) return;
        try {
            boolean deleted = rolePrepService.adminDeleteCompany(id);
            if (deleted) ConsoleIO.printSuccess("Company profile deleted.");
            else ConsoleIO.printError("No company with that ID.");
        } catch (SQLException e) {
            ConsoleIO.printError("Database error: " + e.getMessage());
        }
        ConsoleIO.pause(scanner);
    }
}
