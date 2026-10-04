package com.careerintelligence.ui;

import java.io.Console;
import java.util.Scanner;

/**
 * Small collection of console formatting helpers shared by all menu classes.
 */
public final class ConsoleIO {

    public static final String DIVIDER = "-".repeat(64);
    public static final String DOUBLE_DIVIDER = "=".repeat(64);

    private ConsoleIO() {
    }

    public static void printHeader(String title) {
        System.out.println();
        System.out.println(DOUBLE_DIVIDER);
        System.out.println("  " + title);
        System.out.println(DOUBLE_DIVIDER);
    }

    public static void printDivider() {
        System.out.println(DIVIDER);
    }

    public static void printError(String message) {
        System.out.println(Ansi.red("[ERROR] ") + message);
    }

    public static void printSuccess(String message) {
        System.out.println(Ansi.green("[OK] ") + message);
    }

    public static void printInfo(String message) {
        System.out.println(Ansi.cyan("[INFO] ") + message);
    }

    /** A non-fatal problem the user should notice but that does not stop the flow. */
    public static void printWarn(String message) {
        System.out.println(Ansi.yellow("[WARN] ") + message);
    }

    /**
     * A consistent empty state: what is missing, and the one action that
     * would fill it. Used wherever a screen would otherwise print a
     * heading with nothing under it.
     */
    public static void printEmptyState(String whatIsMissing, String howToFixIt) {
        System.out.println("  " + Ansi.grey("— " + whatIsMissing));
        if (howToFixIt != null && !howToFixIt.isBlank()) {
            System.out.println("  " + Ansi.grey("  " + howToFixIt));
        }
    }

    public static String prompt(Scanner scanner, String label) {
        System.out.print(label + ": ");
        return scanner.nextLine().trim();
    }

    /** Reads a password without echoing it when the application has a real console.
     * Falls back to the scanner so the application still works in IDEs and test runners. */
    public static String promptPassword(Scanner scanner, String label) {
        Console console = System.console();
        if (console != null) {
            char[] password = console.readPassword(label + ": ");
            return password == null ? "" : new String(password);
        }
        return prompt(scanner, label);
    }

    /** Simple yes/no prompt with a safe default. Blank input keeps the default. */
    public static boolean promptYesNo(Scanner scanner, String label, boolean defaultValue) {
        String hint = defaultValue ? "Y/n" : "y/N";
        while (true) {
            String raw = prompt(scanner, label + " [" + hint + "]");
            if (raw.isBlank()) return defaultValue;
            if (raw.equalsIgnoreCase("y") || raw.equalsIgnoreCase("yes")) return true;
            if (raw.equalsIgnoreCase("n") || raw.equalsIgnoreCase("no")) return false;
            System.out.println("Please enter Y or N (or press Enter for the default).");
        }
    }

    public static int promptInt(Scanner scanner, String label, int min, int max) {
        while (true) {
            System.out.print(label + ": ");
            String raw = scanner.nextLine().trim();
            try {
                int value = Integer.parseInt(raw);
                if (value < min || value > max) {
                    System.out.println("Please enter a number between " + min + " and " + max + ".");
                    continue;
                }
                return value;
            } catch (NumberFormatException e) {
                System.out.println("Please enter a valid whole number.");
            }
        }
    }

    public static void pause(Scanner scanner) {
        System.out.print("\nPress ENTER to continue...");
        scanner.nextLine();
    }
}
