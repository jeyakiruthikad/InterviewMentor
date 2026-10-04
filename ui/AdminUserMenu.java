package com.careerintelligence.ui;

import com.careerintelligence.model.User;
import com.careerintelligence.model.UserRole;
import com.careerintelligence.service.AdminService;

import java.sql.SQLException;
import java.util.List;
import java.util.Scanner;

/** Admin User Management screen: list users, activate/deactivate, change role, delete. */
public class AdminUserMenu {

    private final Scanner scanner;
    private final AdminService adminService;

    public AdminUserMenu(Scanner scanner, AdminService adminService) {
        this.scanner = scanner;
        this.adminService = adminService;
    }

    public void show(User admin) {
        boolean back = false;
        while (!back) {
            ConsoleIO.printHeader("USER MANAGEMENT");
            List<User> users;
            try {
                users = adminService.getAllUsers(admin);
            } catch (SQLException e) {
                ConsoleIO.printError("Database error: " + e.getMessage());
                ConsoleIO.pause(scanner);
                return;
            }
            printUsers(users);

            System.out.println("\n1. Activate / Deactivate a user");
            System.out.println("2. Change a user's role");
            System.out.println("3. Delete a user");
            System.out.println("4. Back");
            int choice = ConsoleIO.promptInt(scanner, "Choose an option", 1, 4);

            switch (choice) {
                case 1 -> toggleActive(admin, users);
                case 2 -> changeRole(admin, users);
                case 3 -> deleteUser(admin, users);
                case 4 -> back = true;
            }
        }
    }

    private void printUsers(List<User> users) {
        System.out.printf("%-4s %-20s %-28s %-8s %-6s %-8s%n", "ID", "Username", "Email", "Role", "Active", "Full Name");
        ConsoleIO.printDivider();
        for (User u : users) {
            System.out.printf("%-4d %-20s %-28s %-8s %-6s %s%n",
                    u.getUserId(), u.getUsername(), u.getEmail(), u.getRole(), u.isActive() ? "Yes" : "No", u.getFullName());
        }
    }

    private Long promptUserId(List<User> users) {
        long id = -1;
        while (id == -1) {
            String raw = ConsoleIO.prompt(scanner, "Enter user ID (0 to cancel)");
            try {
                id = Long.parseLong(raw.trim());
            } catch (NumberFormatException e) {
                System.out.println("Please enter a valid numeric ID.");
                id = -1;
            }
        }
        if (id == 0) {
            return null;
        }
        final long targetId = id;
        boolean exists = users.stream().anyMatch(u -> u.getUserId() == targetId);
        if (!exists) {
            ConsoleIO.printError("No user with that ID.");
            return null;
        }
        return id;
    }

    private void toggleActive(User admin, List<User> users) {
        Long id = promptUserId(users);
        if (id == null) return;
        String action = ConsoleIO.prompt(scanner, "Type ACTIVATE or DEACTIVATE").trim().toUpperCase();
        boolean active = action.equals("ACTIVATE");
        if (!active && !action.equals("DEACTIVATE")) {
            ConsoleIO.printError("Please type ACTIVATE or DEACTIVATE.");
            ConsoleIO.pause(scanner);
            return;
        }
        AdminService.Result result = adminService.setUserActive(admin, id, active);
        printResult(result);
    }

    private void changeRole(User admin, List<User> users) {
        Long id = promptUserId(users);
        if (id == null) return;
        String roleStr = ConsoleIO.prompt(scanner, "Type new role (USER or ADMIN)").trim().toUpperCase();
        UserRole role;
        try {
            role = UserRole.valueOf(roleStr);
        } catch (IllegalArgumentException e) {
            ConsoleIO.printError("Role must be USER or ADMIN.");
            ConsoleIO.pause(scanner);
            return;
        }
        AdminService.Result result = adminService.setUserRole(admin, id, role);
        printResult(result);
    }

    private void deleteUser(User admin, List<User> users) {
        Long id = promptUserId(users);
        if (id == null) return;
        String confirm = ConsoleIO.prompt(scanner, "Type DELETE to permanently remove this user and all their data").trim();
        if (!confirm.equalsIgnoreCase("DELETE")) {
            ConsoleIO.printInfo("Cancelled.");
            return;
        }
        AdminService.Result result = adminService.deleteUser(admin, id);
        printResult(result);
    }

    private void printResult(AdminService.Result result) {
        if (result.success()) {
            ConsoleIO.printSuccess(result.message());
        } else {
            ConsoleIO.printError(result.message());
        }
        ConsoleIO.pause(scanner);
    }
}
