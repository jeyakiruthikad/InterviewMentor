package com.careerintelligence.ui;

import com.careerintelligence.model.User;
import com.careerintelligence.model.UserProfile;
import com.careerintelligence.service.AuthService;

import java.util.Optional;
import java.util.Scanner;

public class ProfileMenu {

    private final Scanner scanner;
    private final AuthService authService;

    public ProfileMenu(Scanner scanner, AuthService authService) {
        this.scanner = scanner;
        this.authService = authService;
    }

    public void show(User user) {
        boolean back = false;
        while (!back) {
            ConsoleIO.printHeader("MY PROFILE");
            System.out.println("Username     : " + user.getUsername());
            System.out.println("Full name    : " + user.getFullName());
            System.out.println("Email        : " + user.getEmail());
            System.out.println("Phone        : " + (user.getPhone() == null ? "-" : user.getPhone()));
            System.out.println("Role         : " + user.getRole());
            System.out.println("Member since : " + user.getCreatedAt());

            Optional<UserProfile> profile = authService.getProfile(user.getUserId());
            profile.ifPresent(p -> {
                System.out.println();
                System.out.println("Target role     : " + (p.getTargetRole() == null ? "-" : p.getTargetRole()));
                System.out.println("Target company  : " + (p.getTargetCompany() == null ? "-" : p.getTargetCompany()));
                System.out.println("Experience level: " + p.getExperienceLevel());
                System.out.println("Bio             : " + (p.getBio() == null ? "-" : p.getBio()));
            });

            ConsoleIO.printDivider();
            System.out.println("1. Edit basic info (name / phone / email)");
            System.out.println("2. Edit career profile (target role / company / experience / bio)");
            System.out.println("3. Change password");
            System.out.println("4. Back to main menu");
            int choice = ConsoleIO.promptInt(scanner, "Choose an option", 1, 4);

            switch (choice) {
                case 1 -> editBasicInfo(user);
                case 2 -> editCareerProfile(user);
                case 3 -> changePassword(user);
                case 4 -> back = true;
            }
        }
    }

    private void editBasicInfo(User user) {
        ConsoleIO.printHeader("EDIT BASIC INFO");
        String fullName = ConsoleIO.prompt(scanner, "Full name [" + user.getFullName() + "]");
        String phone = ConsoleIO.prompt(scanner, "Phone [" + (user.getPhone() == null ? "-" : user.getPhone()) + "]");
        String email = ConsoleIO.prompt(scanner, "Email [" + user.getEmail() + "]");

        if (fullName.isBlank()) fullName = user.getFullName();
        if (phone.isBlank()) phone = user.getPhone();
        if (email.isBlank()) email = user.getEmail();

        AuthService.AuthResult result = authService.updateProfile(user, fullName, phone, email);
        if (result.isSuccess()) {
            ConsoleIO.printSuccess(result.getMessage());
        } else {
            ConsoleIO.printError(result.getMessage());
        }
        ConsoleIO.pause(scanner);
    }

    private void editCareerProfile(User user) {
        ConsoleIO.printHeader("EDIT CAREER PROFILE");
        String targetRole = ConsoleIO.prompt(scanner, "Target role (e.g. Backend Developer)");
        String targetCompany = ConsoleIO.prompt(scanner, "Target company (optional)");
        System.out.println("Experience level:");
        System.out.println("1. Fresher");
        System.out.println("2. Junior");
        System.out.println("3. Mid-level");
        System.out.println("4. Senior");
        int experienceChoice = ConsoleIO.promptInt(scanner, "Choose your level", 1, 4);
        String experienceLevel = switch (experienceChoice) {
            case 1 -> "FRESHER";
            case 2 -> "JUNIOR";
            case 3 -> "MID";
            case 4 -> "SENIOR";
            default -> "FRESHER";
        };
        String bio = ConsoleIO.prompt(scanner, "Short bio (optional)");

        AuthService.AuthResult result = authService.updateCareerProfile(
                user.getUserId(), targetRole, targetCompany, experienceLevel, bio);
        if (result.isSuccess()) {
            ConsoleIO.printSuccess(result.getMessage());
        } else {
            ConsoleIO.printError(result.getMessage());
        }
        ConsoleIO.pause(scanner);
    }

    private void changePassword(User user) {
        ConsoleIO.printHeader("CHANGE PASSWORD");
        String current = ConsoleIO.promptPassword(scanner, "Current password");
        String updated = ConsoleIO.promptPassword(scanner, "New password");
        AuthService.AuthResult result = authService.changePassword(user, current, updated);
        if (result.isSuccess()) {
            ConsoleIO.printSuccess(result.getMessage());
        } else {
            ConsoleIO.printError(result.getMessage());
        }
        ConsoleIO.pause(scanner);
    }
}
