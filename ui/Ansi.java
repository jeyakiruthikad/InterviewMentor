package com.careerintelligence.ui;

/**
 * Terminal styling helper: a tiny ANSI palette used by the dashboard and
 * chart renderers.
 *
 * <p>Colour output is <em>opt-out safe</em>. It is disabled automatically
 * when the process has no attached console (piped output, CI, unit tests),
 * when the {@code NO_COLOR} environment variable is set (the
 * no-color.org convention), or when {@code TERM=dumb}. It can be forced on
 * with {@code CAREER_AI_COLOR=1}/{@code FORCE_COLOR=1} for demo recordings
 * where output is piped through a pager.
 *
 * <p>Because every method degrades to returning the raw text unchanged,
 * callers never have to branch on whether colour is available, and the
 * chart/dashboard unit tests can assert on plain, un-escaped strings.
 */
public final class Ansi {

    private static final String RESET = "\u001B[0m";

    // Foreground colours (bright variants render well on both light and dark themes).
    private static final String RED = "\u001B[91m";
    private static final String GREEN = "\u001B[92m";
    private static final String YELLOW = "\u001B[93m";
    private static final String BLUE = "\u001B[94m";
    private static final String MAGENTA = "\u001B[95m";
    private static final String CYAN = "\u001B[96m";
    private static final String GREY = "\u001B[90m";
    private static final String WHITE = "\u001B[97m";

    private static final String BOLD = "\u001B[1m";
    private static final String DIM = "\u001B[2m";

    private static Boolean override;

    private Ansi() {
    }

    /**
     * Forces colour on or off regardless of environment detection. Passing
     * {@code null} restores automatic detection. Used by the demo runner
     * (to force colour for a projector) and by tests (to force it off).
     */
    public static void setEnabled(Boolean enabled) {
        override = enabled;
    }

    public static boolean isEnabled() {
        if (override != null) {
            return override;
        }
        if (isTruthy(System.getenv("NO_COLOR"))) {
            return false;
        }
        if (isTruthy(System.getenv("CAREER_AI_COLOR")) || isTruthy(System.getenv("FORCE_COLOR"))) {
            return true;
        }
        String term = System.getenv("TERM");
        if (term != null && term.equalsIgnoreCase("dumb")) {
            return false;
        }
        // System.console() is null when stdout is redirected (tests, pipes, CI logs).
        return System.console() != null;
    }

    private static boolean isTruthy(String value) {
        return value != null && !value.isBlank() && !value.equals("0") && !value.equalsIgnoreCase("false");
    }

    private static String wrap(String code, String text) {
        if (text == null) {
            return "";
        }
        return isEnabled() ? code + text + RESET : text;
    }

    public static String red(String text) {
        return wrap(RED, text);
    }

    public static String green(String text) {
        return wrap(GREEN, text);
    }

    public static String yellow(String text) {
        return wrap(YELLOW, text);
    }

    public static String blue(String text) {
        return wrap(BLUE, text);
    }

    public static String magenta(String text) {
        return wrap(MAGENTA, text);
    }

    public static String cyan(String text) {
        return wrap(CYAN, text);
    }

    public static String grey(String text) {
        return wrap(GREY, text);
    }

    public static String white(String text) {
        return wrap(WHITE, text);
    }

    public static String bold(String text) {
        return wrap(BOLD, text);
    }

    public static String dim(String text) {
        return wrap(DIM, text);
    }

    /**
     * Colours a 0-100 score by band: green (strong), cyan (good),
     * yellow (developing), red (at risk). Keeps the dashboard's colour
     * language consistent everywhere a score appears.
     */
    public static String byScore(String text, double score) {
        if (score >= 80) {
            return green(text);
        }
        if (score >= 65) {
            return cyan(text);
        }
        if (score >= 45) {
            return yellow(text);
        }
        return red(text);
    }

    /** Green for an improvement, red for a regression, grey for no meaningful change. */
    public static String byDelta(String text, double delta, boolean higherIsBetter) {
        if (Math.abs(delta) < 0.05) {
            return grey(text);
        }
        boolean good = higherIsBetter == (delta > 0);
        return good ? green(text) : red(text);
    }

    /**
     * Visible length of a string, ignoring any ANSI escape sequences, so
     * box-drawing code can pad coloured text to the right column width.
     */
    public static int visibleLength(String text) {
        if (text == null) {
            return 0;
        }
        return text.replaceAll("\u001B\\[[0-9;]*m", "").length();
    }
}
