package com.careerintelligence.service;

import com.careerintelligence.ai.AIHealth;
import com.careerintelligence.dao.CareerRefreshLogDAO;
import com.careerintelligence.dao.JobDescriptionDAO;
import com.careerintelligence.dao.JobMatchResultDAO;
import com.careerintelligence.dao.MistakeLogDAO;
import com.careerintelligence.dao.MockInterviewDAO;
import com.careerintelligence.model.JobDescription;
import com.careerintelligence.model.LearningRoadmap;
import com.careerintelligence.model.MockInterviewSession;
import com.careerintelligence.model.MockSessionStatus;
import com.careerintelligence.model.OverallStats;
import com.careerintelligence.model.ReadinessScore;
import com.careerintelligence.model.ResumeSkill;
import com.careerintelligence.model.TopicPerformance;
import com.careerintelligence.model.UserBadge;
import com.careerintelligence.model.UserPoints;

import java.sql.SQLException;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Business logic for the Final Dashboard & Integration screen: a single
 * read-side aggregation that pulls together resume analysis, assessment
 * performance, AI evaluations, mistakes, mock interviews, the readiness
 * score, the learning roadmap, personalised recommendations and
 * gamification achievements - the complete personalised career state, in
 * one call. It doesn't own any data itself, and it doesn't recompute
 * anything {@link CareerIntelligenceService} already does: the resume/
 * skill-gap/weak-topic/readiness/roadmap portion of the snapshot is
 * delegated straight to {@link CareerIntelligenceService#buildSnapshot},
 * so the two never drift out of sync. It only adds the two things that
 * are dashboard-specific and not part of the core career snapshot: raw
 * assessment stats (DashboardService) and gamification (GamificationService).
 */
public class IntegratedDashboardService {

    private final DashboardService dashboardService = new DashboardService();
    private final MockInterviewDAO mockInterviewDAO = new MockInterviewDAO();
    private final GamificationService gamificationService = new GamificationService();
    private final CareerIntelligenceService careerIntelligenceService = new CareerIntelligenceService();
    private final JobMatchResultDAO jobMatchResultDAO = new JobMatchResultDAO();
    private final JobDescriptionDAO jobDescriptionDAO = new JobDescriptionDAO();
    private final CareerRefreshLogDAO careerRefreshLogDAO = new CareerRefreshLogDAO();

    public record CompleteDashboard(
            String targetRole,
            OverallStats assessmentStats,
            List<ResumeSkill> resumeSkills,
            List<String> missingSkills,
            List<String> weakSkills,
            List<TopicPerformance> weakTopics,
            List<String> recommendedFocusTopics,
            MistakeLogDAO.MistakeCounts mistakeCounts,
            int totalMockSessions,
            int completedMockSessions,
            ReadinessScore readinessScore,
            Optional<LearningRoadmap> roadmap,
            UserPoints points,
            List<UserBadge> badges,
            ResumeService.RoleMatch roleMatch,
            ProgressAnalyticsService.AnalyticsSummary analytics) {
    }

    public CompleteDashboard build(Long userId) throws SQLException {
        CareerIntelligenceService.CareerSnapshot snapshot = careerIntelligenceService.buildSnapshot(userId);

        OverallStats assessmentStats = dashboardService.buildDashboard(userId).overallStats();

        List<MockInterviewSession> mockSessions = mockInterviewDAO.findByUser(userId);
        int completedMocks = (int) mockSessions.stream()
                .filter(s -> s.getStatus() == MockSessionStatus.COMPLETED).count();

        UserPoints points = gamificationService.getPoints(userId);
        List<UserBadge> badges = gamificationService.getBadges(userId);

        return new CompleteDashboard(
                snapshot.targetRole(),
                assessmentStats,
                snapshot.resumeSkills(),
                snapshot.missingSkills(),
                snapshot.weakSkills(),
                snapshot.weakTopics(),
                snapshot.recommendedFocus().topicNames(),
                snapshot.mistakeCounts(),
                mockSessions.size(), completedMocks,
                snapshot.readiness(), snapshot.roadmap(),
                points, badges,
                snapshot.roleMatch(), snapshot.analytics());
    }

    /**
     * Builds the presentation-ready {@link DashboardView} behind the
     * modern dashboard screen: everything {@link #build} already
     * aggregates, plus the three extras the visual dashboard needs - the
     * latest explainable Job Match, the Career Intelligence Updates feed,
     * and the most recent mock-interview score.
     *
     * <p>Each extra is fetched <em>defensively</em>: a failure to read the
     * JD match or the refresh log degrades that panel to its empty state
     * rather than taking down the whole dashboard, because these are
     * supplementary panels and the core readiness/skills data is already
     * in hand by this point.
     */
    public DashboardView buildView(Long userId, String candidateName) throws SQLException {
        CompleteDashboard dashboard = build(userId);

        DashboardView.JobMatchView jobMatch = loadJobMatch(userId);
        List<CareerRefreshLogDAO.Snapshot> refreshLog = loadRefreshLog(userId);
        Double lastMockScore = loadLatestMockScore(userId);

        return DashboardView.from(candidateName, dashboard, jobMatch, refreshLog,
                lastMockScore, AIHealth.describeMode());
    }

    /** The latest persisted Job Match snapshot, resolved against its job description for a title. */
    private DashboardView.JobMatchView loadJobMatch(Long userId) {
        try {
            Optional<JobMatchResultDAO.Row> row = jobMatchResultDAO.findLatestByUser(userId);
            if (row.isEmpty()) {
                return DashboardView.JobMatchView.none();
            }
            JobMatchResultDAO.Row r = row.get();
            String title = null;
            if (r.jobDescriptionId() != null) {
                title = jobDescriptionDAO.findById(r.jobDescriptionId())
                        .map(JobDescription::getTitle).orElse(null);
            }
            return new DashboardView.JobMatchView(title, r.matchScore(), r.strongMatch(), r.partialMatch(),
                    r.missing(), r.highPriority(), r.summary(), List.of());
        } catch (SQLException e) {
            System.err.println("[Dashboard] Could not load job match: " + e.getMessage());
            return DashboardView.JobMatchView.none();
        }
    }

    private List<CareerRefreshLogDAO.Snapshot> loadRefreshLog(Long userId) {
        try {
            return careerRefreshLogDAO.findRecentByUser(userId, 5);
        } catch (SQLException e) {
            System.err.println("[Dashboard] Could not load career updates: " + e.getMessage());
            return List.of();
        }
    }

    private Double loadLatestMockScore(Long userId) {
        try {
            return mockInterviewDAO.findByUser(userId).stream()
                    .filter(s -> s.getStatus() == MockSessionStatus.COMPLETED)
                    .filter(s -> s.getAverageScore() != null)
                    .max(Comparator.comparing(MockInterviewSession::getSessionId,
                            Comparator.nullsFirst(Comparator.naturalOrder())))
                    .map(s -> s.getAverageScore().doubleValue())
                    .orElse(null);
        } catch (SQLException e) {
            return null;
        }
    }
}
