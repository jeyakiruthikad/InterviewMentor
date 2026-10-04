package com.careerintelligence.demo;

import com.careerintelligence.ai.AIHealth;
import com.careerintelligence.service.DashboardView;
import com.careerintelligence.ui.Ansi;
import com.careerintelligence.ui.DashboardRenderer;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the guided demo.
 *
 * <p>The demo's credibility rests on two claims: that the numbers actually
 * improve across the pipeline (rather than being three unrelated
 * screenshots), and that it renders through the same production view-model
 * and renderer the live app uses. Both are asserted here.
 */
class DemoFlowTest {

    DemoFlowTest() {
        Ansi.setEnabled(false);
    }

    // -----------------------------------------------------------------
    // The arc actually improves
    // -----------------------------------------------------------------

    @Test
    void readinessImprovesMonotonicallyAcrossTheStages() {
        double baseline = DemoDataFactory.readiness(DemoDataFactory.Stage.BASELINE).getOverallScore();
        double practice = DemoDataFactory.readiness(DemoDataFactory.Stage.AFTER_PRACTICE).getOverallScore();
        double mock = DemoDataFactory.readiness(DemoDataFactory.Stage.AFTER_MOCK).getOverallScore();
        assertTrue(practice > baseline, "practice should raise readiness");
        assertTrue(mock > practice, "the mock interview should raise readiness further");
    }

    @Test
    void readinessBandImprovesFromNotReadyToAlmostReady() {
        assertEquals("NOT READY", DemoDataFactory.readiness(DemoDataFactory.Stage.BASELINE).getBand());
        assertEquals("ALMOST READY", DemoDataFactory.readiness(DemoDataFactory.Stage.AFTER_MOCK).getBand());
    }

    @Test
    void mockInterviewIsWhatLiftsCommunicationAndBehavioral() {
        // This is the demo's key narrative beat: technical practice alone cannot move these two.
        var before = DemoDataFactory.readiness(DemoDataFactory.Stage.AFTER_PRACTICE).getDimensions();
        var after = DemoDataFactory.readiness(DemoDataFactory.Stage.AFTER_MOCK).getDimensions();
        double commBefore = before.get("communication").getValue();
        double commAfter = after.get("communication").getValue();
        double behBefore = before.get("behavioral").getValue();
        double behAfter = after.get("behavioral").getValue();
        assertTrue(commAfter - commBefore > 20.0, "communication should jump after the mock interview");
        assertTrue(behAfter - behBefore > 20.0, "behavioral should jump after the mock interview");
    }

    @Test
    void weakTopicsAreClearedByTheEndOfTheDemo() {
        assertFalse(DemoDataFactory.weakTopics(DemoDataFactory.Stage.BASELINE).isEmpty());
        assertTrue(DemoDataFactory.weakTopics(DemoDataFactory.Stage.AFTER_MOCK).isEmpty(),
                "every topic should be above the competency line by the end");
    }

    @Test
    void unresolvedMistakesFallAcrossTheStages() {
        int baseline = DemoDataFactory.mistakeCounts(DemoDataFactory.Stage.BASELINE).unresolved();
        int end = DemoDataFactory.mistakeCounts(DemoDataFactory.Stage.AFTER_MOCK).unresolved();
        assertTrue(end < baseline, "retrying mistakes should reduce the unresolved count");
    }

    @Test
    void mistakeCountsAreInternallyConsistent() {
        for (DemoDataFactory.Stage stage : DemoDataFactory.Stage.values()) {
            var c = DemoDataFactory.mistakeCounts(stage);
            assertEquals(c.total(), c.resolved() + c.unresolved(),
                    "resolved + unresolved must equal total at stage " + stage);
        }
    }

    @Test
    void jobMatchScoreImprovesAsGapsClose() {
        double baseline = DemoDataFactory.jobMatch(DemoDataFactory.Stage.BASELINE).matchScore();
        double end = DemoDataFactory.jobMatch(DemoDataFactory.Stage.AFTER_MOCK).matchScore();
        assertTrue(end > baseline);
    }

    @Test
    void roadmapItemsGetCompletedAsTheDemoProgresses() {
        assertEquals(0, DemoFlow.completedRoadmapItems(DemoDataFactory.Stage.BASELINE));
        assertTrue(DemoFlow.completedRoadmapItems(DemoDataFactory.Stage.AFTER_MOCK)
                > DemoFlow.completedRoadmapItems(DemoDataFactory.Stage.AFTER_PRACTICE));
    }

    @Test
    void topicAccuracyNeverRegressesBetweenStages() {
        var baseline = DemoDataFactory.topicTrends(DemoDataFactory.Stage.BASELINE);
        var end = DemoDataFactory.topicTrends(DemoDataFactory.Stage.AFTER_MOCK);
        for (var b : baseline) {
            var match = end.stream().filter(t -> t.topicName().equals(b.topicName())).findFirst();
            assertTrue(match.isPresent(), "topic " + b.topicName() + " should still exist at the end");
            assertTrue(match.get().accuracyPercent() >= b.accuracyPercent(),
                    b.topicName() + " should not regress");
        }
    }

    // -----------------------------------------------------------------
    // The demo runs through real production code
    // -----------------------------------------------------------------

    @Test
    void demoBuildsARealDashboardViewThroughTheProductionFactory() {
        DashboardView view = DemoDataFactory.dashboardView(DemoDataFactory.Stage.AFTER_MOCK);
        assertEquals(DemoDataFactory.CANDIDATE_NAME, view.candidateName());
        assertFalse(view.isEmptyProfile(), "the populated demo must not look like an empty account");
        assertNotNull(view.nextAction(), "the explainer should have produced a recommendation");
        assertTrue(view.nextAction().hasEvidence(), "the demo should have enough data to explain itself");
    }

    @Test
    void demoRecommendationChangesBetweenStages() {
        String baseline = DemoDataFactory.dashboardView(DemoDataFactory.Stage.BASELINE).nextAction().action();
        String end = DemoDataFactory.dashboardView(DemoDataFactory.Stage.AFTER_MOCK).nextAction().action();
        assertNotEquals(baseline, end, "the recommendation must adapt as the candidate progresses");
    }

    @Test
    void demoDashboardRendersEverySectionWithoutThrowing() {
        List<String> lines = DashboardRenderer.render(
                DemoDataFactory.dashboardView(DemoDataFactory.Stage.AFTER_MOCK));
        String all = String.join("\n", lines);
        assertTrue(all.contains("INTERVIEW READINESS"));
        assertTrue(all.contains("JOB MATCH"));
        assertTrue(all.contains("STRENGTHS & SKILL GAPS"));
        assertTrue(all.contains("TOPIC MASTERY"));
        assertTrue(all.contains("ASSESSMENTS & MOCK INTERVIEWS"));
        assertTrue(all.contains("ROADMAP PROGRESS"));
        assertTrue(all.contains("INTERVIEWMENTOR UPDATES"));
        assertTrue(all.contains("RECOMMENDED NEXT ACTION"));
        assertTrue(all.contains("ACHIEVEMENTS"));
    }

    @Test
    void beforeAfterComparisonReportsTheRealDeltas() {
        List<String> rows = DemoFlow.comparisonRows(
                DemoDataFactory.dashboardView(DemoDataFactory.Stage.BASELINE),
                DemoDataFactory.dashboardView(DemoDataFactory.Stage.AFTER_MOCK));
        String all = String.join("\n", rows);
        assertTrue(all.contains("Readiness score"));
        assertTrue(all.contains("Job match"));
        assertTrue(all.contains("Recommendation changed"));
    }

    @Test
    void anEmptyDashboardRendersOnboardingInsteadOfBlankPanels() {
        DashboardView empty = new DashboardView("New User", null, null, List.of(), null,
                DashboardView.JobMatchView.none(), List.of(), List.of(), List.of(),
                List.of(), null, DashboardView.MockView.empty(), null,
                DashboardView.RoadmapView.empty(), List.of(), null, null, List.of(), "local");
        String all = String.join("\n", DashboardRenderer.render(empty));
        assertTrue(all.contains("GETTING STARTED"), "a new account should get onboarding guidance");
    }

    @Test
    void demoResumeSkillsCoverTheCoreBackendStack() {
        List<String> skills = DemoFlow.demoResumeSkillNames();
        assertTrue(skills.contains("Java"));
        assertTrue(skills.contains("Spring Boot"));
    }

    @Test
    void demoJdGapsAreGenuinelyAbsentFromTheResume() {
        List<String> resume = DemoFlow.demoResumeSkillNames();
        for (String missing : DemoDataFactory.missingSkills()) {
            assertFalse(resume.contains(missing),
                    "\"" + missing + "\" is listed as missing but appears on the demo resume");
        }
    }

    @Test
    void claimedButWeakSkillsAreActuallyClaimedOnTheResume() {
        List<String> resume = DemoFlow.demoResumeSkillNames();
        for (String weak : DemoDataFactory.weakSkills()) {
            assertTrue(resume.contains(weak),
                    "\"" + weak + "\" is flagged as claimed-but-weak but is not on the demo resume");
        }
    }

    @Test
    void careerUpdatesArePresentAtEveryStage() {
        for (DemoDataFactory.Stage stage : DemoDataFactory.Stage.values()) {
            assertFalse(DemoDataFactory.careerUpdates(stage).isEmpty(), "no updates at stage " + stage);
        }
    }

    // -----------------------------------------------------------------
    // AI health / LLM failure handling
    // -----------------------------------------------------------------

    @Test
    void aiHealthReportsLocalEngineWhenNoApiKeyIsConfigured() {
        AIHealth.reset();
        AIHealth.setLiveConfigured(false);
        assertTrue(AIHealth.describeMode().contains("local semantic engine"));
    }

    @Test
    void aiHealthReportsLiveWhenConfiguredAndNothingHasFailed() {
        AIHealth.reset();
        AIHealth.setLiveConfigured(true);
        assertEquals("live LLM", AIHealth.describeMode());
    }

    @Test
    void aiHealthReportsDegradedModeAfterAFallback() {
        AIHealth.reset();
        AIHealth.setLiveConfigured(true);
        AIHealth.recordLive("call");
        AIHealth.recordFallback("Answer evaluation", "connection timed out");
        assertTrue(AIHealth.describeMode().contains("fallback"));
        assertEquals(1, AIHealth.fallbackCallCount());
    }

    @Test
    void degradationNoticeNamesTheOperationAndReason() {
        AIHealth.reset();
        AIHealth.recordFallback("Answer evaluation", "HTTP 503");
        String notice = AIHealth.degradationNotice();
        assertNotNull(notice);
        assertTrue(notice.contains("Answer evaluation"));
        assertTrue(notice.contains("HTTP 503"));
    }

    @Test
    void degradationNoticeIsNullWhenNothingHasDegraded() {
        AIHealth.reset();
        assertNull(AIHealth.degradationNotice());
    }

    @Test
    void longFailureReasonsAreTruncatedForDisplay() {
        AIHealth.reset();
        AIHealth.recordFallback("op", "x".repeat(500));
        AIHealth.Event event = AIHealth.recentEvents().get(0);
        assertTrue(event.reason().length() <= 80, "reason should be trimmed to fit a console line");
    }

    @Test
    void nullFailureReasonDoesNotBreakTracking() {
        AIHealth.reset();
        AIHealth.recordFallback("op", null);
        assertEquals(1, AIHealth.fallbackCallCount());
        assertNotNull(AIHealth.recentEvents().get(0).reason());
    }

    @Test
    void eventHistoryIsBoundedSoItCannotGrowForever() {
        AIHealth.reset();
        for (int i = 0; i < 50; i++) {
            AIHealth.recordFallback("op" + i, "reason");
        }
        assertTrue(AIHealth.recentEvents().size() <= 10);
    }
}
