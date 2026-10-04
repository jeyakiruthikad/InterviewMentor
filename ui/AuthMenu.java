package com.careerintelligence.ui;

import com.careerintelligence.model.User;
import com.careerintelligence.service.AuthService;

import java.util.Scanner;

/**
 * Handles the pre-login screens: register, login, exit.
 */
public class AuthMenu {

    private final Scanner scanner;
    private final AuthService authService;

    public AuthMenu(Scanner scanner, AuthService authService) {
        this.scanner = scanner;
        this.authService = authService;
    }

    /** Returns the logged-in user, or null if the user chose to exit the application. */
    public User show() {
        while (true) {
            ConsoleIO.printHeader("INTERVIEWMENTOR - WELCOME");
            System.out.println("1. Login");
            System.out.println("2. Register");
            System.out.println("3. Exit");
            int choice = ConsoleIO.promptInt(scanner, "Choose an option", 1, 3);

            switch (choice) {
                case 1 -> {
                    User user = login();
                    if (user != null) {
                        return user;
                    }
                }
                case 2 -> register();
                case 3 -> {
                    return null;
                }
            }
        }
    }

    private User login() {
        ConsoleIO.printHeader("LOGIN");
        String usernameOrEmail = ConsoleIO.prompt(scanner, "Username or Email");
        String password = ConsoleIO.promptPassword(scanner, "Password");

        AuthService.AuthResult result = authService.login(usernameOrEmail, password);
        if (result.isSuccess()) {
            ConsoleIO.printSuccess(result.getMessage());
            ConsoleIO.pause(scanner);
            return result.getUser();
        } else {
            ConsoleIO.printError(result.getMessage());
            ConsoleIO.pause(scanner);
            return null;
        }
    }

    private void register() {
        ConsoleIO.printHeader("REGISTER");
        String username = ConsoleIO.prompt(scanner, "Choose a username (4-30 chars, letters/digits/_)");
        String email = ConsoleIO.prompt(scanner, "Email");
        System.out.println("(" + com.careerintelligence.util.InputValidator.passwordRequirementsMessage() + ")");
        String password = ConsoleIO.promptPassword(scanner, "Choose a password");
        String fullName = ConsoleIO.prompt(scanner, "Full name");
        String phone = ConsoleIO.prompt(scanner, "Phone (optional, press Enter to skip)");

        AuthService.AuthResult result = authService.register(username, email, password, fullName, phone);
        if (result.isSuccess()) {
            ConsoleIO.printSuccess(result.getMessage());
        } else {
            ConsoleIO.printError(result.getMessage());
        }
        ConsoleIO.pause(scanner);
    }
}
