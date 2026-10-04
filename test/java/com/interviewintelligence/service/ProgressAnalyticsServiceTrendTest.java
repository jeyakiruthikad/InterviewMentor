package com.careerintelligence.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the pure, database-free trend classification/description
 * logic in {@link ProgressAnalyticsService}. No database or MySQL
 * connection is required - run with: mvn test
 */
class ProgressAnalyticsServiceTrendTest {

    @Test
    void trendMetricOfReportsInsufficientDataWhenEitherValueMissing() {
        ProgressAnalyticsService.TrendMetric metric = ProgressAnalyticsService.TrendMetric.of("Accuracy", null, 50.0);
        assertEquals(ProgressAnalyticsService.Direction.INSUFFICIENT_DATA, metric.direction());
    }

    @Test
    void trendMetricOfDetectsUpDownAndFlat() {
        assertEquals(ProgressAnalyticsService.Direction.UP,
                ProgressAnalyticsService.TrendMetric.of("Accuracy", 80.0, 60.0).direction());
        assertEquals(ProgressAnalyticsService.Direction.DOWN,
                ProgressAnalyticsService.TrendMetric.of("Accuracy", 60.0, 80.0).direction());
        assertEquals(ProgressAnalyticsService.Direction.FLAT,
                ProgressAnalyticsService.TrendMetric.of("Accuracy", 70.0, 70.0).direction());
    }

    @Test
    void describeTrendHandlesInsufficientData() {
        ProgressAnalyticsService.TrendMetric metric = ProgressAnalyticsService.TrendMetric.of("Accuracy", null, null);
        String description = ProgressAnalyticsService.describeTrend(metric, "%", false);
        assertTrue(description.toLowerCase().contains("not enough"));
    }

    @Test
    void describeTrendReportsUpAsImprovingWhenHigherIsBetter() {
        ProgressAnalyticsService.TrendMetric metric = ProgressAnalyticsService.TrendMetric.of("Accuracy", 90.0, 70.0);
        String description = ProgressAnalyticsService.describeTrend(metric, "%", false);
        assertTrue(description.contains("up"), "a rising accuracy value should describe as trending up");
    }

    @Test
    void describeTrendReportsFasterAnsweringAsImprovingWhenLowerIsBetter() {
        // Speed metric (seconds/question): a lower recent value (numeric DOWN direction) is an
        // improvement, so it should be described using the "up" (improving) wording, not "down".
        ProgressAnalyticsService.TrendMetric metric = ProgressAnalyticsService.TrendMetric.of("Speed", 10.0, 20.0);
        assertEquals(ProgressAnalyticsService.Direction.DOWN, metric.direction());
        String description = ProgressAnalyticsService.describeTrend(metric, "s/question", true);
        assertTrue(description.contains("up"), "getting faster should read as improving (\"up\") for a speed metric");
    }
}
