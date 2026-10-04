package com.careerintelligence.ui;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the console chart primitives. Colour is forced off so the
 * assertions can be made against plain, un-escaped strings - which is also
 * exactly how the charts render in a piped/CI context.
 */
class ChartsTest {

    ChartsTest() {
        Ansi.setEnabled(false);
    }

    @Test
    void gaugeFillsProportionallyToValue() {
        String half = Charts.gauge(50.0, 10);
        assertTrue(half.startsWith("█████░░░░░"), "50% of a 10-wide track should be 5 filled blocks: " + half);
        assertTrue(half.contains("50.0%"));
    }

    @Test
    void gaugeIsEmptyAtZeroAndFullAtHundred() {
        assertTrue(Charts.gauge(0.0, 8).startsWith("░░░░░░░░"));
        assertTrue(Charts.gauge(100.0, 8).startsWith("████████"));
    }

    @Test
    void gaugeClampsOutOfRangeValuesInsteadOfOverflowing() {
        String over = Charts.gauge(250.0, 10);
        String under = Charts.gauge(-40.0, 10);
        assertTrue(over.startsWith("██████████"), "over-range should clamp to full");
        assertTrue(over.contains("100.0%"));
        assertTrue(under.startsWith("░░░░░░░░░░"), "under-range should clamp to empty");
        assertTrue(under.contains("0.0%"));
    }

    @Test
    void gaugeTrackLengthIsAlwaysExactlyTheRequestedWidth() {
        for (double v : new double[]{0, 13.7, 49.9, 50.0, 87.3, 100}) {
            String track = Charts.gauge(v, 20).split(" ")[0];
            assertEquals(20, track.length(), "track width should be stable at value " + v);
        }
    }

    @Test
    void nanValueDegradesToZeroRatherThanThrowing() {
        String gauge = Charts.gauge(Double.NaN, 6);
        assertTrue(gauge.contains("0.0%"));
    }

    @Test
    void labelledGaugePadsLabelToFixedColumnSoRowsAlign() {
        String a = Charts.labelledGauge("Technical", 60.0, 14, 10);
        String b = Charts.labelledGauge("Problem Solving", 40.0, 14, 10);
        assertEquals(a.indexOf('█') < 0 ? a.indexOf('░') : a.indexOf('█'),
                b.indexOf('█') < 0 ? b.indexOf('░') : b.indexOf('█'),
                "bars in a stack must start at the same column");
    }

    @Test
    void sparklineProducesOneGlyphPerValue() {
        String spark = Charts.sparkline(List.of(1.0, 5.0, 3.0, 9.0));
        assertEquals(4, spark.length());
    }

    @Test
    void sparklineUsesLowestGlyphForMinAndHighestForMax() {
        String spark = Charts.sparkline(List.of(10.0, 20.0, 30.0));
        assertEquals('▁', spark.charAt(0));
        assertEquals('█', spark.charAt(2));
    }

    @Test
    void flatSeriesDoesNotDivideByZero() {
        String spark = Charts.sparkline(List.of(50.0, 50.0, 50.0));
        assertEquals(3, spark.length());
        assertEquals(spark.charAt(0), spark.charAt(2), "a flat series should render flat");
    }

    @Test
    void sparklineOfEmptyOrNullSeriesIsEmpty() {
        assertEquals("", Charts.sparkline(List.of()));
        assertEquals("", Charts.sparkline(null));
    }

    @Test
    void deltaShowsUpArrowForGainAndDownArrowForLoss() {
        assertTrue(Charts.delta(6.4, true).contains("▲"));
        assertTrue(Charts.delta(6.4, true).contains("+6.4"));
        assertTrue(Charts.delta(-2.1, true).contains("▼"));
    }

    @Test
    void deltaShowsNeutralMarkerForNoMeaningfulChange() {
        assertTrue(Charts.delta(0.0, true).contains("●"));
    }

    @Test
    void trendLineFallsBackToAMessageWhenHistoryIsTooShort() {
        assertTrue(Charts.trendLine(List.of(50.0), "x").contains("not enough history"));
        assertTrue(Charts.trendLine(null, "x").contains("not enough history"));
    }

    @Test
    void trendLinePlotsSeriesAndNetMovement() {
        String line = Charts.trendLine(List.of(40.0, 55.0, 70.0), "across 3 snapshots");
        assertTrue(line.contains("▲"));
        assertTrue(line.contains("+30.0"));
        assertTrue(line.contains("across 3 snapshots"));
    }

    @Test
    void fractionRendersCountsAndPercentage() {
        String f = Charts.fraction(3, 10, 10);
        assertTrue(f.contains("3/10"));
        assertTrue(f.contains("30%"));
    }

    @Test
    void fractionWithZeroTotalDoesNotDivideByZero() {
        String f = Charts.fraction(0, 0, 8);
        assertTrue(f.contains("0/0"));
        assertTrue(f.contains("0%"));
    }

    @Test
    void padRightPadsShortTextAndTruncatesLongText() {
        assertEquals("ab   ", Charts.padRight("ab", 5));
        String truncated = Charts.padRight("abcdefghij", 5);
        assertEquals(5, truncated.length());
        assertTrue(truncated.endsWith("…"), "truncation should be marked with an ellipsis");
    }

    @Test
    void padRightTreatsNullAsEmpty() {
        assertEquals("   ", Charts.padRight(null, 3));
    }

    @Test
    void barChartProducesOneRowPerLabel() {
        List<String> rows = Charts.barChart(List.of("A", "B", "C"), List.of(10.0, 50.0, 90.0), 10);
        assertEquals(3, rows.size());
        assertTrue(rows.get(2).contains("90.0%"));
    }

    @Test
    void barRowScalesAgainstProvidedMaxNotAlwaysHundred() {
        String row = Charts.barRow("x", 5.0, 10.0, 4, 10, "half");
        assertTrue(row.contains("█████░░░░░"), "5 out of a max of 10 should fill half the bar: " + row);
    }

    @Test
    void percentRendersNaForUnknownValues() {
        assertEquals("n/a", Charts.percent(null));
        assertEquals("72.5%", Charts.percent(72.5));
    }
}
