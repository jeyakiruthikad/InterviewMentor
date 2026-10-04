package com.careerintelligence.ui;

import java.util.ArrayList;
import java.util.List;

/**
 * Box-drawing layout primitives shared by the dashboard, the guided demo
 * and the readiness screens: framed panels, section rules, banners, and
 * two-column key/value rows.
 *
 * <p>Like {@link Charts}, everything here is a pure string function, so
 * layout can be unit-tested and reused without a terminal. All widths are
 * measured with {@link Ansi#visibleLength(String)}, so panels stay aligned
 * even when their contents are coloured.
 */
public final class Layout {

    /** Standard content width of the dashboard, in characters. */
    public static final int WIDTH = 78;

    private static final char H = '\u2500';   // ─
    private static final char V = '\u2502';   // │
    private static final char TL = '\u256D';  // ╭
    private static final char TR = '\u256E';  // ╮
    private static final char BL = '\u2570';  // ╰
    private static final char BR = '\u256F';  // ╯

    private Layout() {
    }

    /** A full-width horizontal rule. */
    public static String rule() {
        return rule(WIDTH);
    }

    public static String rule(int width) {
        return Ansi.grey(String.valueOf(H).repeat(width));
    }

    /**
     * A numbered section heading with a trailing rule, e.g.
     * {@code ── INTERVIEW READINESS ─────────────────────}.
     */
    public static String section(String title) {
        String label = " " + title.toUpperCase() + " ";
        int used = Ansi.visibleLength(label) + 2;
        int trailing = Math.max(0, WIDTH - used);
        return Ansi.grey(String.valueOf(H).repeat(2)) + Ansi.bold(Ansi.cyan(label))
                + Ansi.grey(String.valueOf(H).repeat(trailing));
    }

    /**
     * A framed banner used for the dashboard header and demo stage titles:
     * a rounded box containing the title and an optional subtitle.
     */
    public static List<String> banner(String title, String subtitle) {
        List<String> out = new ArrayList<>();
        int inner = WIDTH - 2;
        out.add(Ansi.cyan(TL + String.valueOf(H).repeat(inner) + TR));
        out.add(Ansi.cyan(String.valueOf(V)) + " " + Charts.padRight(Ansi.bold(Ansi.white(title)), inner - 2) + " "
                + Ansi.cyan(String.valueOf(V)));
        if (subtitle != null && !subtitle.isBlank()) {
            out.add(Ansi.cyan(String.valueOf(V)) + " " + Charts.padRight(Ansi.grey(subtitle), inner - 2) + " "
                    + Ansi.cyan(String.valueOf(V)));
        }
        out.add(Ansi.cyan(BL + String.valueOf(H).repeat(inner) + BR));
        return out;
    }

    /**
     * A framed panel with a title and pre-rendered body lines. Long body
     * lines are left intact (never truncated) so numbers are never lost;
     * they simply overflow the frame on very narrow terminals.
     */
    public static List<String> panel(String title, List<String> body) {
        List<String> out = new ArrayList<>();
        int inner = WIDTH - 2;
        String heading = " " + Ansi.bold(title) + " ";
        int headingLen = Ansi.visibleLength(heading);
        out.add(Ansi.grey(String.valueOf(TL) + H) + heading
                + Ansi.grey(String.valueOf(H).repeat(Math.max(0, inner - headingLen - 1)) + TR));
        for (String line : body) {
            out.add(Ansi.grey(String.valueOf(V)) + " " + Charts.padRight(line, inner - 2) + " "
                    + Ansi.grey(String.valueOf(V)));
        }
        out.add(Ansi.grey(BL + String.valueOf(H).repeat(inner) + BR));
        return out;
    }

    /** A {@code label : value} row with the label padded to a fixed column. */
    public static String kv(String label, String value, int labelWidth) {
        return Ansi.grey(Charts.padRight(label, labelWidth)) + "  " + (value == null ? "" : value);
    }

    public static String kv(String label, String value) {
        return kv(label, value, 22);
    }

    /** A bulleted list item. */
    public static String bullet(String text) {
        return "  " + Ansi.cyan("\u2022") + " " + text;
    }

    /** A numbered list item. */
    public static String numbered(int index, String text) {
        return "  " + Ansi.cyan(index + ".") + " " + text;
    }

    /**
     * Wraps prose to the dashboard width with a hanging indent, so long
     * explanations stay inside the layout instead of wrapping raggedly at
     * the terminal edge.
     */
    public static List<String> wrap(String text, int width, String indent) {
        List<String> lines = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return lines;
        }
        StringBuilder current = new StringBuilder();
        for (String word : text.trim().split("\\s+")) {
            int prospective = current.length() + (current.length() == 0 ? 0 : 1) + word.length();
            if (current.length() > 0 && prospective > width) {
                lines.add(indent + current);
                current.setLength(0);
            }
            if (current.length() > 0) {
                current.append(' ');
            }
            current.append(word);
        }
        if (current.length() > 0) {
            lines.add(indent + current);
        }
        return lines;
    }

    public static List<String> wrap(String text) {
        return wrap(text, WIDTH - 4, "  ");
    }

    /** Prints a list of pre-rendered lines to stdout. */
    public static void print(List<String> lines) {
        for (String line : lines) {
            System.out.println(line);
        }
    }
}
