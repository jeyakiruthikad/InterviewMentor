package com.careerintelligence.service;

import com.careerintelligence.dao.CareerRefreshLogDAO;
import com.careerintelligence.dao.MistakeLogDAO;
import com.careerintelligence.dao.ReadinessScoreDAO;
import com.careerintelligence.model.LearningRoadmap;
import com.careerintelligence.model.OverallStats;
import com.careerintelligence.model.ReadinessScore;
import com.careerintelligence.model.ResumeSkill;
import com.careerintelligence.model.RoadmapItem;
import com.careerintelligence.model.RoadmapItemStatus;
import com.careerintelligence.model.TopicPerformance;
import com.careerintelligence.model.UserBadge;
import com.careerintelligence.model.UserPoints;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * The immutable, presentation-ready view-model behind the Complete
 * Dashboard screen.
 *
 * <p>This type sits between {@link IntegratedDashboardService} (which
 * reads the data) and {@code ui.DashboardRenderer} (which draws it). It
 * exists so that every derived number the dashboard shows - the readiness
 * trend series, the strengths/gaps split, topic mastery ordering, roadmap
 * progress and the explained next action - is computed in one pure,
 * unit-testable place rather than inline in print statements.
 *
 * <p>Nothing here queries a database or mutates state, so the same
 * view-model powers both the live dashboard and the guided demo.
 */
public record DashboardView(
        String candidateName,
        String targetRole,
        ReadinessScore readiness,
        List<Double> readinessSeries,
        Double readinessDelta,
        JobMatchView jobMatch,
        List<ResumeSkill> resumeSkills,
        List<String> missingSkills,
        List<String> weakSkills,
        List<TopicMastery> topicMastery,
        OverallStats assessmentStats,
        MockView mockInterviews,
        MistakeLogDAO.MistakeCounts mistakeCounts,
        RoadmapView roadmap,
        List<String> careerUpdates,
        RecommendationExplainer.Explanation nextAction,
        UserPoints points,
        List<UserBadge> badges,
        String aiMode) {

    /** How far below this per-topic accuracy a topic is treated as a gap rather than a strength. */
    public static final double MASTERY_THRESHOLD = 60.0;
    /** At or above this accuracy a topic is reported as a strength. */
    public static final double STRENGTH_THRESHOLD = 75.0;

    /** The JD-match portion of the dashboard, flattened for display. */
    public record JobMatchView(String jobTitle, Double matchScore, List<String> strongMatches,
                                List<String> partialMatches, List<String> missing,
                                List<String> highPrioritySkills, String summary,
                                List<ReadinessScore.Component> breakdown) {

        public static JobMatchView none() {
            return new JobMatchView(null, null, List.of(), List.of(), List.of(), List.of(), null, List.of());
        }

        public boolean isPresent() {
            return matchScore != null;
        }
    }

    /** One topic's mastery level, used by the topic-mastery bar chart. */
    public record TopicMastery(String topicName, double accuracyPercent, int attempts, String trend) {

        public boolean isGap() {
            return accuracyPercent < MASTERY_THRESHOLD;
        }

        public boolean isStrength() {
            return accuracyPercent >= STRENGTH_THRESHOLD;
        }
    }

    /** Mock-interview counts plus the most recent score, when one exists. */
    public record MockView(int totalSessions, int completedSessions, Double lastScore) {

        public static MockView empty() {
            return new MockView(0, 0, null);
        }
    }

    /** Roadmap progress plus the top outstanding priorities. */
    public record RoadmapView(int totalItems, int completedItems, double completionPercent,
                               List<String> topPriorities, String summary) {

        public static RoadmapView empty() {
            return new RoadmapView(0, 0, 0.0, List.of(), null);
        }
    }

    // -----------------------------------------------------------------
    // Derived helpers used by the renderer
    // -----------------------------------------------------------------

    /** True when the user has essentially no data yet, so the UI can show an onboarding state. */
    public boolean isEmptyProfile() {
        return (resumeSkills == null || resumeSkills.isEmpty())
                && (assessmentStats == null || assessmentStats.getTotalAssessments() == 0)
                && (mockInterviews == null || mockInterviews.completedSessions() == 0);
    }

    /** Topics at or above the strength threshold, strongest first. */
    public List<TopicMastery> strengths() {
        List<TopicMastery> out = new ArrayList<>();
        if (topicMastery == null) {
            return out;
        }
        for (TopicMastery t : topicMastery) {
            if (t.isStrength()) {
                out.add(t);
            }
        }
        out.sort(Comparator.comparingDouble(TopicMastery::accuracyPercent).reversed());
        return out;
    }

    /** Topics below the mastery threshold, weakest first. */
    public List<TopicMastery> gaps() {
        List<TopicMastery> out = new ArrayList<>();
        if (topicMastery == null) {
            return out;
        }
        for (TopicMastery t : topicMastery) {
            if (t.isGap()) {
                out.add(t);
            }
        }
        out.sort(Comparator.comparingDouble(TopicMastery::accuracyPercent));
        return out;
    }

    /** A one-line readiness trend caption, e.g. {@code "across 6 snapshots"}. The numeric delta itself is rendered separately by the chart, so it is deliberately not repeated here. */
    public String readinessTrendCaption() {
        if (readinessSeries == null || readinessSeries.size() < 2) {
            return "first score - no trend yet";
        }
        return String.format(Locale.ROOT, "across %d snapshots", readinessSeries.size());
    }

    // -----------------------------------------------------------------
    // Construction from live data
    // -----------------------------------------------------------------

    /**
     * Builds the view-model from the data
     * {@link IntegratedDashboardService} already aggregates, plus the two
     * extras the dashboard needs (the latest JD match and the career
     * refresh log). Every argument is optional-tolerant: nulls and empty
     * collections degrade into empty states rather than throwing, because
     * a brand-new user legitimately has none of this data.
     */
    public static DashboardView from(String candidateName,
                                      IntegratedDashboardService.CompleteDashboard d,
                                      JobMatchView jobMatch,
                                      List<CareerRefreshLogDAO.Snapshot> refreshLog,
                                      Double lastMockScore,
                                      String aiMode) {
        List<Double> series = readinessSeriesFrom(d == null ? null : d.analytics());
        Double delta = series.size() >= 2 ? series.get(series.size() - 1) - series.get(0) : null;

        List<TopicMastery> mastery = topicMasteryFrom(d == null ? null : d.analytics(),
                d == null ? null : d.weakTopics());

        RoadmapView roadmapView = roadmapViewFrom(d == null ? Optional.empty() : d.roadmap());

        List<String> updates = new ArrayList<>();
        if (refreshLog != null) {
            for (CareerRefreshLogDAO.Snapshot s : refreshLog) {
                if (s.details() != null && !s.details().isBlank()) {
                    updates.add(s.details());
                }
            }
        }

        RecommendationExplainer.Evidence evidence = new RecommendationExplainer.Evidence(
                d == null ? null : d.targetRole(),
                d == null ? null : d.readinessScore(),
                d == null ? List.of() : d.resumeSkills(),
                d == null ? List.of() : d.missingSkills(),
                d == null ? List.of() : d.weakSkills(),
                d == null ? List.of() : d.weakTopics(),
                d == null ? null : d.mistakeCounts(),
                jobMatch == null ? null : jobMatch.matchScore(),
                jobMatch == null ? null : jobMatch.jobTitle(),
                jobMatch == null ? List.of() : jobMatch.highPrioritySkills(),
                d == null || d.assessmentStats() == null ? null : d.assessmentStats().getAverageScorePercent(),
                d == null || d.assessmentStats() == null ? 0 : d.assessmentStats().getTotalAssessments(),
                d == null ? 0 : d.completedMockSessions(),
                roadmapView.totalItems(),
                roadmapView.completedItems());

        return new DashboardView(
                candidateName,
                d == null ? null : d.targetRole(),
                d == null ? null : d.readinessScore(),
                series,
                delta,
                jobMatch == null ? JobMatchView.none() : jobMatch,
                d == null ? List.of() : d.resumeSkills(),
                d == null ? List.of() : d.missingSkills(),
                d == null ? List.of() : d.weakSkills(),
                mastery,
                d == null ? null : d.assessmentStats(),
                d == null ? MockView.empty()
                        : new MockView(d.totalMockSessions(), d.completedMockSessions(), lastMockScore),
                d == null ? null : d.mistakeCounts(),
                roadmapView,
                updates,
                RecommendationExplainer.explainNextAction(evidence),
                d == null ? null : d.points(),
                d == null ? List.of() : d.badges(),
                aiMode);
    }

    /**
     * Extracts the readiness score series in chronological order. The
     * analytics summary stores snapshots newest-first (the DAO orders by
     * {@code computed_at DESC}), so this reverses them for plotting.
     */
    static List<Double> readinessSeriesFrom(ProgressAnalyticsService.AnalyticsSummary analytics) {
        List<Double> series = new ArrayList<>();
        if (analytics == null || analytics.readinessHistory() == null) {
            return series;
        }
        for (ReadinessScoreDAO.Snapshot s : analytics.readinessHistory()) {
            series.add(s.overallScore());
        }
        Collections.reverse(series);
        return series;
    }

    /**
     * Builds the topic-mastery rows. Prefers the analytics topic trends
     * (which cover every attempted topic, with momentum); falls back to
     * the weak-topic list alone when analytics are unavailable, so the
     * section still renders something useful.
     */
    static List<TopicMastery> topicMasteryFrom(ProgressAnalyticsService.AnalyticsSummary analytics,
                                                List<TopicPerformance> weakTopics) {
        List<TopicMastery> rows = new ArrayList<>();
        if (analytics != null && analytics.topicTrends() != null && !analytics.topicTrends().isEmpty()) {
            for (ProgressAnalyticsService.TopicTrend t : analytics.topicTrends()) {
                rows.add(new TopicMastery(t.topicName(), t.accuracyPercent(), t.attempts(),
                        t.trend() == null ? "STEADY" : t.trend().name()));
            }
            return rows;
        }
        if (weakTopics != null) {
            for (TopicPerformance tp : weakTopics) {
                rows.add(new TopicMastery(
                        tp.getTopicName() == null ? "Topic #" + tp.getTopicId() : tp.getTopicName(),
                        tp.getAccuracyPercent() == null ? 0.0 : tp.getAccuracyPercent().doubleValue(),
                        tp.getAttemptsCount(), "STEADY"));
            }
        }
        return rows;
    }

    /** Flattens the latest roadmap into progress counts plus its top outstanding item titles. */
    static RoadmapView roadmapViewFrom(Optional<LearningRoadmap> roadmap) {
        if (roadmap == null || roadmap.isEmpty() || roadmap.get().getItems() == null) {
            return RoadmapView.empty();
        }
        LearningRoadmap r = roadmap.get();
        List<RoadmapItem> items = r.getItems();
        int total = items.size();
        int done = (int) items.stream().filter(i -> i.getStatus() == RoadmapItemStatus.COMPLETED).count();
        double pct = total == 0 ? 0.0 : Math.round(done * 10000.0 / total) / 100.0;

        List<String> top = items.stream()
                .filter(i -> i.getStatus() != RoadmapItemStatus.COMPLETED)
                .sorted(Comparator.comparingInt(RoadmapItem::getItemOrder))
                .limit(4)
                .map(i -> {
                    String priority = i.getPriority() == null ? "" : "[" + i.getPriority() + "] ";
                    return priority + i.getTitle();
                })
                .toList();

        return new RoadmapView(total, done, pct, top, r.getSummary());
    }
}
