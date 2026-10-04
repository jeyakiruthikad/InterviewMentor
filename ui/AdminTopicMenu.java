package com.careerintelligence.ui;

import com.careerintelligence.model.Topic;
import com.careerintelligence.model.User;
import com.careerintelligence.service.AdminService;

import java.sql.SQLException;
import java.util.List;
import java.util.Scanner;

/** Admin Topic Management screen: full CRUD over question-bank topics. */
public class AdminTopicMenu {

    private final Scanner scanner;
    private final AdminService adminService;

    public AdminTopicMenu(Scanner scanner, AdminService adminService) {
        this.scanner = scanner;
        this.adminService = adminService;
    }

    public void show(User admin) {
        boolean back = false;
        while (!back) {
            ConsoleIO.printHeader("TOPIC MANAGEMENT");
            List<Topic> topics;
            try {
                topics = adminService.getAllTopics(admin);
            } catch (SQLException e) {
                ConsoleIO.printError("Database error: " + e.getMessage());
                ConsoleIO.pause(scanner);
                return;
            }
            printTopics(topics);

            System.out.println("\n1. Add a topic");
            System.out.println("2. Edit a topic");
            System.out.println("3. Delete a topic");
            System.out.println("4. Back");
            int choice = ConsoleIO.promptInt(scanner, "Choose an option", 1, 4);

            switch (choice) {
                case 1 -> addTopic(admin);
                case 2 -> editTopic(admin, topics);
                case 3 -> deleteTopic(admin, topics);
                case 4 -> back = true;
            }
        }
    }

    private void printTopics(List<Topic> topics) {
        System.out.printf("%-4s %-30s %-18s %-6s%n", "ID", "Name", "Category", "Active");
        ConsoleIO.printDivider();
        for (Topic t : topics) {
            System.out.printf("%-4d %-30s %-18s %-6s%n", t.getTopicId(), t.getTopicName(), t.getCategory(),
                    t.isActive() ? "Yes" : "No");
        }
    }

    private void addTopic(User admin) {
        String name = ConsoleIO.prompt(scanner, "Topic name");
        String category = ConsoleIO.prompt(scanner, "Category");
        String description = ConsoleIO.prompt(scanner, "Description (optional)");
        AdminService.Result result = adminService.createTopic(admin, name, category, description);
        printResult(result);
    }

    private void editTopic(User admin, List<Topic> topics) {
        Topic topic = findById(topics);
        if (topic == null) return;
        String name = ConsoleIO.prompt(scanner, "New name (blank to keep \"" + topic.getTopicName() + "\")");
        String category = ConsoleIO.prompt(scanner, "New category (blank to keep \"" + topic.getCategory() + "\")");
        String description = ConsoleIO.prompt(scanner, "New description (blank to keep current)");
        String activeStr = ConsoleIO.prompt(scanner, "Active? (Y/N, blank to keep \""
                + (topic.isActive() ? "Y" : "N") + "\")");

        if (!name.isBlank()) topic.setTopicName(name);
        if (!category.isBlank()) topic.setCategory(category);
        if (!description.isBlank()) topic.setDescription(description);
        if (!activeStr.isBlank()) topic.setActive(activeStr.trim().equalsIgnoreCase("Y"));

        AdminService.Result result = adminService.updateTopic(admin, topic);
        printResult(result);
    }

    private void deleteTopic(User admin, List<Topic> topics) {
        Topic topic = findById(topics);
        if (topic == null) return;
        String confirm = ConsoleIO.prompt(scanner, "Type DELETE to permanently remove \"" + topic.getTopicName()
                + "\" and ALL of its questions");
        if (!confirm.equalsIgnoreCase("DELETE")) {
            ConsoleIO.printInfo("Cancelled.");
            return;
        }
        AdminService.Result result = adminService.deleteTopic(admin, topic.getTopicId());
        printResult(result);
    }

    private Topic findById(List<Topic> topics) {
        int id = ConsoleIO.promptInt(scanner, "Enter topic ID (0 to cancel)", 0, Integer.MAX_VALUE);
        if (id == 0) return null;
        for (Topic t : topics) {
            if (t.getTopicId() == id) return t;
        }
        ConsoleIO.printError("No topic with that ID.");
        return null;
    }

    private void printResult(AdminService.Result result) {
        if (result.success()) ConsoleIO.printSuccess(result.message());
        else ConsoleIO.printError(result.message());
        ConsoleIO.pause(scanner);
    }
}
