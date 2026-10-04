package com.careerintelligence.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Minimal ".env" file loader (no third-party dependency required).
 * Looks for a ".env" file in the current working directory (project root,
 * i.e. next to pom.xml) and falls back to real environment variables /
 * JVM system properties if a key is not found in the file.
 *
 * Lines starting with '#' are treated as comments; blank lines are ignored.
 * Format: KEY=VALUE
 */
public final class EnvLoader {

    private static final Map<String, String> VALUES = new HashMap<>();
    private static boolean loaded = false;

    private EnvLoader() {
    }

    private static synchronized void loadIfNeeded() {
        if (loaded) {
            return;
        }
        loaded = true;
        Path envPath = Path.of(".env");
        if (!Files.exists(envPath)) {
            // Also try relative to the project root if run from a sub-directory (e.g. an IDE's default out dir)
            envPath = Path.of("../.env");
            if (!Files.exists(envPath)) {
                return; // fine - caller will rely on system env vars / defaults
            }
        }
        try {
            for (String rawLine : Files.readAllLines(envPath)) {
                String line = rawLine.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                int idx = line.indexOf('=');
                if (idx <= 0) {
                    continue;
                }
                String key = line.substring(0, idx).trim();
                String value = line.substring(idx + 1).trim();
                // Strip surrounding quotes if present
                if (value.length() >= 2 && (value.startsWith("\"") && value.endsWith("\""))) {
                    value = value.substring(1, value.length() - 1);
                }
                VALUES.put(key, value);
            }
        } catch (IOException e) {
            System.err.println("Warning: could not read .env file: " + e.getMessage());
        }
    }

    public static String get(String key, String defaultValue) {
        loadIfNeeded();
        if (VALUES.containsKey(key)) {
            return VALUES.get(key);
        }
        String sysEnv = System.getenv(key);
        if (sysEnv != null) {
            return sysEnv;
        }
        String sysProp = System.getProperty(key);
        if (sysProp != null) {
            return sysProp;
        }
        return defaultValue;
    }

    public static int getInt(String key, int defaultValue) {
        String value = get(key, null);
        if (value == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}
