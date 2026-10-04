package com.careerintelligence.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Pure text-chart primitives for the console dashboard: progress gauges,
 * labelled horizontal bar charts, sparklines, delta badges and score
 * boxes.
 *
 * <p>Every method here is a <em>pure function</em> - it takes numbers and
 * returns a string, touching no database, no service and no global state -
 * so the entire visual layer is unit-testable. Colour is applied through
 * {@link Ansi}, which no-ops when the output stream is not a terminal, so
 * the strings asserted in tests are plain ASCII/Unicode.
 */
public final class Charts {

    /** Default width, in characters, of a gauge's filled track. */
    public static final int DEFAULT_GAUGE_WIDTH = 24;

    private static final char FILLED = '\u2588';       // full block
    private static final char EMPTY = '\u2591';        // light shade
    private static final char[] SPARK = {'\u2581', '\u2582', '\u2583', '\u2584',
                                          '\u2585', '\u2586', '\u2587', '\u2588'};

    private Charts() {
    }

    // -----------------------------------------------------------------
    // Gauges and bars
    // -----------------------------------------------------------------

    /**
     * A horizontal progress gauge for a 0-100 value, e.g.
     * {@code ████████████░░░░░░░░░░░░  51.4%}. Values outside 0-100 are
     * clamped rather than overflowing the track.
     */
    public static String gauge(double value, int width) {
        double clamped = clamp(value);
        int filled = (int) Math.round(clamped / 100.0 * width);
        String track = String.valueOf(FILLED).repeat(filled) + String.valueOf(EMPTY).repeat(Math.max(0, width - filled));
        return Ansi.byScore(track, clamped) + String.format(Locale.ROOT, " %5.1f%%", clamped);
    }

    public static String gauge(double value) {
        return gauge(value, DEFAULT_GAUGE_WIDTH);
    }

    /**
     * A gauge prefixed with a fixed-width label, so a stack of them lines
     * up into a readable chart:
     * {@code Technical        ██████████████░░░░░░░░░░  58.0%}.
     */
    public static String labelledGauge(String label, double value, int labelWidth, int barWidth) {
        return padRight(label, labelWidth) + "  " + gauge(value, barWidth);
    }

    public static String labelledGauge(String label, double value) {
        return labelledGauge(label, value, 18, DEFAULT_GAUGE_WIDTH);
    }

    /** One row of a labelled bar chart, where {@code value} is scaled against {@code max} rather than 100. */
    public static String barRow(String label, double value, double max, int labelWidth, int barWidth, String suffix) {
        double safeMax = max <= 0 ? 1 : max;
        double pct = clamp(value / safeMax * 100.0);
        int filled = (int) Math.round(pct / 100.0 * barWidth);
        String track = String.valueOf(FILLED).repeat(filled) + String.valueOf(EMPTY).repeat(Math.max(0, barWidth - filled));
        return padRight(label, labelWidth) + "  " + Ansi.byScore(track, pct) + "  " + suffix;
    }

    /**
     * Renders a full labelled bar chart from parallel label/value lists,
     * auto-sizing the label column. Returns one string per row.
     */
    public static List<String> barChart(List<String> labels, List<Double> values, int barWidth) {
        List<String> rows = new ArrayList<>();
        if (labels == null || values == null || labels.isEmpty()) {
            return rows;
        }
        int labelWidth = 0;
        for (String l : labels) {
            labelWidth = Math.max(labelWidth, l == null ? 0 : l.length());
        }
        labelWidth = Math.min(labelWidth, 28);
        int n = Math.min(labels.size(), values.size());
        for (int i = 0; i < n; i++) {
            double v = values.get(i) == null ? 0.0 : values.get(i);
            rows.add(barRow(labels.get(i), v, 100.0, labelWidth, barWidth,
                    String.format(Locale.ROOT, "%5.1f%%", clamp(v))));
        }
        return rows;
    }

    // -----------------------------------------------------------------
    // Sparklines and trends
    // -----------------------------------------------------------------

    /**
     * A compact unicode sparkline of a value series, e.g. {@code ▁▂▄▅▇█}.
     * The series is scaled between its own min and max, so a flat series
     * renders as a flat mid-line instead of dividing by zero.
     */
    public static String sparkline(List<Double> values) {
        if (values == null || values.isEmpty()) {
            return "";
        }
        double min = Double.MAX_VALUE;
        double max = -Double.MAX_VALUE;
        for (Double v : values) {
            if (v == null) {
                continue;
            }
            min = Math.min(min, v);
            max = Math.max(max, v);
        }
        if (min == Double.MAX_VALUE) {
            return "";
        }
        double range = max - min;
        StringBuilder sb = new StringBuilder();
        for (Double v : values) {
            if (v == null) {
                sb.append(' ');
                continue;
            }
            int idx = range < 1e-9 ? SPARK.length / 2 : (int) Math.round((v - min) / range * (SPARK.length - 1));
            sb.append(SPARK[Math.max(0, Math.min(SPARK.length - 1, idx))]);
        }
        return sb.toString();
    }

    /**
     * A signed delta badge, e.g. {@code ▲ +6.4} / {@code ▼ -2.1} /
     * {@code ● 0.0}, coloured by whether the movement is good news.
     */
    public static String delta(double delta, boolean higherIsBetter) {
        String arrow;
        if (Math.abs(delta) < 0.05) {
            arrow = "\u25CF";           // filled circle: no change
        } else if (delta > 0) {
            arrow = "\u25B2";           // up triangle
        } else {
            arrow = "\u25BC";           // down triangle
        }
        String text = String.format(Locale.ROOT, "%s %s%.1f", arrow, delta > 0.05 ? "+" : "", delta);
        return Ansi.byDelta(text, delta, higherIsBetter);
    }

    /** A readiness sparkline plus its net movement, e.g. {@code ▁▃▄▆█  ▲ +18.4 since first score}. */
    public static String trendLine(List<Double> series, String suffixLabel) {
        if (series == null || series.size() < 2) {
            return Ansi.grey("not enough history yet to plot a trend");
        }
        double first = series.get(0) == null ? 0 : series.get(0);
        double last = series.get(series.size() - 1) == null ? 0 : series.get(series.size() - 1);
        return sparkline(series) + "  " + delta(last - first, true) + " " + Ansi.grey(suffixLabel);
    }

    // -----------------------------------------------------------------
    // Layout helpers
    // -----------------------------------------------------------------

    /** Pads (or truncates with an ellipsis) to an exact visible width, ANSI-aware. */
    public static String padRight(String text, int width) {
        String safe = text == null ? "" : text;
        int visible = Ansi.visibleLength(safe);
        if (visible > width) {
            if (Ansi.visibleLength(safe) == safe.length()) {
                return width <= 1 ? safe.substring(0, Math.max(0, width)) : safe.substring(0, width - 1) + "\u2026";
            }
            return safe; // coloured text: don't risk cutting mid-escape
        }
        return safe + " ".repeat(width - visible);
    }

    /** A percentage rendered as {@code 72.5%}, or {@code n/a} when the value is unknown. */
    public static String percent(Double value) {
        return value == null ? "n/a" : String.format(Locale.ROOT, "%.1f%%", value);
    }

    /** A 0-100 score rendered as {@code 72.5/100}, coloured by band. */
    public static String score(double value) {
        return Ansi.byScore(String.format(Locale.ROOT, "%.1f/100", clamp(value)), clamp(value));
    }

    /**
     * A fraction rendered as a mini progress bar with counts, e.g.
     * {@code ███░░░░░░░ 3/10 (30%)} - used for roadmap and mistake-resolution progress.
     */
    public static String fraction(int done, int total, int width) {
        double pct = total <= 0 ? 0.0 : done * 100.0 / total;
        int filled = total <= 0 ? 0 : (int) Math.round(pct / 100.0 * width);
        String track = String.valueOf(FILLED).repeat(filled) + String.valueOf(EMPTY).repeat(Math.max(0, width - filled));
        return Ansi.byScore(track, pct) + String.format(Locale.ROOT, " %d/%d (%.0f%%)", done, total, pct);
    }

    private static double clamp(double value) {
        if (Double.isNaN(value)) {
            return 0.0;
        }
        return Math.max(0.0, Math.min(100.0, value));
    }
}
