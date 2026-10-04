package com.careerintelligence.service;

import com.careerintelligence.dao.ReadinessScoreDAO;
import com.careerintelligence.model.LearningRoadmap;
import com.careerintelligence.model.RoadmapItem;
import com.careerintelligence.model.RoadmapItemStatus;
import com.careerintelligence.model.TopicPerformance;
import com.careerintelligence.util.AdaptiveDifficultyCalculator;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the pure derivations behind the dashboard: the readiness trend
 * series, topic-mastery ordering, the strengths/gaps split and roadmap
 * progress. These run without any database, which is the point of keeping
 * the view-model separate from the service that loads it.
 */
class DashboardViewTest {

    private static ReadinessScoreDAO.Snapshot snapshot(double overall, int daysAgo) {
        return new ReadinessScoreDAO.Snapshot(overall, overall, overall, overall, overall, overall,
                overall, overall, overall, overall, overall, "Technical", "Communication",
                "next action", LocalDateTime.now().minusDays(daysAgo));
    }

    private static ProgressAnalyticsService.AnalyticsSummary analytics(
            List<ReadinessScoreDAO.Snapshot> history, List<ProgressAnalyticsService.TopicTrend> trends) {
        return new ProgressAnalyticsService.AnalyticsSummary(history, trends,
                new ProgressAnalyticsService.TrendMetric("acc", 60.0, 50.0, ProgressAnalyticsService.Direction.UP),
                new ProgressAnalyticsService.TrendMetric("spd", 40.0, 50.0, ProgressAnalyticsService.Direction.DOWN),
                null, 0.0, 0, 0);
    }

    // -----------------------------------------------------------------
    // Readiness series
    // -----------------------------------------------------------------

    @Test
    void readinessSeriesIsReversedIntoChronologicalOrder() {
        // The DAO returns newest-first; the sparkline must plot oldest-first.
        List<ReadinessScoreDAO.Snapshot> newestFirst = List.of(
                snapshot(70.0, 0), snapshot(60.0, 1), snapshot(50.0, 2));
        List<Double> series = DashboardView.readinessSeriesFrom(analytics(newestFirst, List.of()));
        assertEquals(List.of(50.0, 60.0, 70.0), series);
    }

    @Test
    void readinessSeriesIsEmptyWhenAnalyticsAreMissing() {
        assertTrue(DashboardView.readinessSeriesFrom(null).isEmpty());
        assertTrue(DashboardView.readinessSeriesFrom(analytics(null, List.of())).isEmpty());
    }

    // -----------------------------------------------------------------
    // Topic mastery
    // -----------------------------------------------------------------

    @Test
    void topicMasteryPrefersAnalyticsTrendsWhenAvailable() {
        List<ProgressAnalyticsService.TopicTrend> trends = List.of(
                new ProgressAnalyticsService.TopicTrend("System Design", 38.0, 8,
                        AdaptiveDifficultyCalculator.Trend.DECLINING));
        List<DashboardView.TopicMastery> rows =
                DashboardView.topicMasteryFrom(analytics(List.of(), trends), List.of());
        assertEquals(1, rows.size());
        assertEquals("System Design", rows.get(0).topicName());
        assertEquals("DECLINING", rows.get(0).trend());
    }

    @Test
    void topicMasteryFallsBackToWeakTopicsWhenAnalyticsAreUnavailable() {
        TopicPerformance tp = new TopicPerformance();
        tp.setTopicName("Concurrency");
        tp.setAccuracyPercent(BigDecimal.valueOf(45.0));
        tp.setAttemptsCount(11);
        List<DashboardView.TopicMastery> rows = DashboardView.topicMasteryFrom(null, List.of(tp));
        assertEquals(1, rows.size());
        assertEquals("Concurrency", rows.get(0).topicName());
        assertEquals(45.0, rows.get(0).accuracyPercent(), 0.001);
    }

    @Test
    void topicWithoutANameFallsBackToItsIdRatherThanShowingNull() {
        TopicPerformance tp = new TopicPerformance();
        tp.setTopicId(7);
        tp.setAccuracyPercent(BigDecimal.valueOf(50.0));
        tp.setAttemptsCount(2);
        List<DashboardView.TopicMastery> rows = DashboardView.topicMasteryFrom(null, List.of(tp));
        assertEquals("Topic #7", rows.get(0).topicName());
    }

    @Test
    void masteryClassificationUsesTheDocumentedThresholds() {
        assertTrue(new DashboardView.TopicMastery("a", 59.9, 1, "STABLE").isGap());
        assertFalse(new DashboardView.TopicMastery("b", 60.0, 1, "STABLE").isGap());
        assertTrue(new DashboardView.TopicMastery("c", 75.0, 1, "STABLE").isStrength());
        assertFalse(new DashboardView.TopicMastery("d", 74.9, 1, "STABLE").isStrength());
    }

    // -----------------------------------------------------------------
    // Roadmap
    // -----------------------------------------------------------------

    private static RoadmapItem item(int order, String title, RoadmapItemStatus status) {
        RoadmapItem i = new RoadmapItem();
        i.setItemOrder(order);
        i.setTitle(title);
        i.setStatus(status);
        return i;
    }

    @Test
    void roadmapProgressCountsCompletedItems() {
        LearningRoadmap r = new LearningRoadmap();
        r.setItems(List.of(
                item(1, "A", RoadmapItemStatus.COMPLETED),
                item(2, "B", RoadmapItemStatus.COMPLETED),
                item(3, "C", RoadmapItemStatus.PENDING),
                item(4, "D", RoadmapItemStatus.PENDING)));
        DashboardView.RoadmapView view = DashboardView.roadmapViewFrom(Optional.of(r));
        assertEquals(4, view.totalItems());
        assertEquals(2, view.completedItems());
        assertEquals(50.0, view.completionPercent(), 0.001);
    }

    @Test
    void roadmapTopPrioritiesExcludeCompletedItemsAndRespectOrder() {
        LearningRoadmap r = new LearningRoadmap();
        r.setItems(List.of(
                item(1, "Done", RoadmapItemStatus.COMPLETED),
                item(3, "Third", RoadmapItemStatus.PENDING),
                item(2, "Second", RoadmapItemStatus.PENDING)));
        DashboardView.RoadmapView view = DashboardView.roadmapViewFrom(Optional.of(r));
        assertEquals(2, view.topPriorities().size());
        assertTrue(view.topPriorities().get(0).contains("Second"), view.topPriorities().toString());
        assertTrue(view.topPriorities().stream().noneMatch(p -> p.contains("Done")));
    }

    @Test
    void absentRoadmapDegradesToAnEmptyViewRatherThanThrowing() {
        DashboardView.RoadmapView view = DashboardView.roadmapViewFrom(Optional.empty());
        assertEquals(0, view.totalItems());
        assertEquals(0.0, view.completionPercent(), 0.001);
        assertTrue(view.topPriorities().isEmpty());
        assertEquals(0, DashboardView.roadmapViewFrom(null).totalItems());
    }

    // -----------------------------------------------------------------
    // Strengths / gaps split and empty state
    // -----------------------------------------------------------------

    private static DashboardView viewWithMastery(List<DashboardView.TopicMastery> mastery) {
        return new DashboardView("Test", "Role", null, List.of(), null,
                DashboardView.JobMatchView.none(), List.of(), List.of(), List.of(),
                mastery, null, DashboardView.MockView.empty(), null,
                DashboardView.RoadmapView.empty(), List.of(), null, null, List.of(), "local");
    }

    @Test
    void strengthsAreSortedBestFirstAndGapsWorstFirst() {
        DashboardView view = viewWithMastery(List.of(
                new DashboardView.TopicMastery("Low", 38.0, 5, "STABLE"),
                new DashboardView.TopicMastery("High", 90.0, 5, "STABLE"),
                new DashboardView.TopicMastery("Mid", 80.0, 5, "STABLE"),
                new DashboardView.TopicMastery("Lower", 20.0, 5, "STABLE")));

        assertEquals("High", view.strengths().get(0).topicName());
        assertEquals("Mid", view.strengths().get(1).topicName());
        assertEquals("Lower", view.gaps().get(0).topicName());
        assertEquals("Low", view.gaps().get(1).topicName());
    }

    @Test
    void midRangeTopicsAreNeitherStrengthsNorGaps() {
        DashboardView view = viewWithMastery(List.of(
                new DashboardView.TopicMastery("Mid", 68.0, 5, "STABLE")));
        assertTrue(view.strengths().isEmpty());
        assertTrue(view.gaps().isEmpty());
    }

    @Test
    void trendCaptionDoesNotRepeatTheNumericDelta() {
        DashboardView view = new DashboardView("Test", "Role", null, List.of(40.0, 60.0), 20.0,
                DashboardView.JobMatchView.none(), List.of(), List.of(), List.of(),
                List.of(), null, DashboardView.MockView.empty(), null,
                DashboardView.RoadmapView.empty(), List.of(), null, null, List.of(), "local");
        // The chart renders the delta itself; repeating it in the caption looked like a bug.
        assertEquals("across 2 snapshots", view.readinessTrendCaption());
    }

    @Test
    void singleSnapshotReportsNoTrendYet() {
        DashboardView view = viewWithMastery(List.of());
        assertTrue(view.readinessTrendCaption().contains("no trend yet"));
    }

    @Test
    void emptyProfileIsDetectedSoTheUiCanShowOnboarding() {
        assertTrue(viewWithMastery(List.of()).isEmptyProfile());
    }

    @Test
    void jobMatchNoneIsNotTreatedAsPresent() {
        assertFalse(DashboardView.JobMatchView.none().isPresent());
        assertTrue(new DashboardView.JobMatchView("t", 50.0, List.of(), List.of(), List.of(),
                List.of(), null, List.of()).isPresent());
    }
}
