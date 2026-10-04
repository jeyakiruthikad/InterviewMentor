package com.careerintelligence.service;

import com.careerintelligence.dao.MistakeLogDAO;
import com.careerintelligence.model.ReadinessScore;
import com.careerintelligence.model.ResumeSkill;
import com.careerintelligence.model.TopicPerformance;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the explainability engine. The contract under test is that
 * every "why" line is grounded in real user data - actual skill names,
 * actual accuracy figures, actual mistake counts - rather than generic
 * advice, since that grounding is the whole point of the feature.
 */
class RecommendationExplainerTest {

    private static ReadinessScore readinessWithDimensions(double technical, double communication) {
        ReadinessScore r = new ReadinessScore();
        r.setOverallScore(55.0);
        r.setBand("DEVELOPING");
        r.addDimension("technical", new ReadinessScore.Component("Technical", technical, 30, ""));
        r.addDimension("communication", new ReadinessScore.Component("Communication", communication, 20, ""));
        return r;
    }

    private static TopicPerformance topic(String name, double accuracy, int attempts) {
        TopicPerformance tp = new TopicPerformance();
        tp.setTopicName(name);
        tp.setAccuracyPercent(BigDecimal.valueOf(accuracy));
        tp.setAttemptsCount(attempts);
        return tp;
    }

    private static ResumeSkill skill(String name) {
        ResumeSkill s = new ResumeSkill();
        s.setSkillName(name);
        return s;
    }

    private static RecommendationExplainer.Evidence fullEvidence() {
        return new RecommendationExplainer.Evidence(
                "Senior Backend Engineer",
                readinessWithDimensions(72.0, 31.0),
                List.of(skill("Java"), skill("Docker")),
                List.of("Kubernetes", "Kafka"),
                List.of("Docker"),
                List.of(topic("System Design", 38.0, 8)),
                new MistakeLogDAO.MistakeCounts(14, 5, 9),
                46.5, "Senior Backend Engineer - Northwind",
                List.of("System Design"),
                58.5, 2, 0, 8, 2);
    }

    // -----------------------------------------------------------------
    // Grounding
    // -----------------------------------------------------------------

    @Test
    void weakestDimensionIsCitedWithItsActualValue() {
        List<String> why = RecommendationExplainer.buildWhy(fullEvidence());
        assertTrue(why.stream().anyMatch(w -> w.contains("Communication") && w.contains("31.0")),
                "the lowest dimension and its real value should be named: " + why);
    }

    @Test
    void jobDescriptionGapsAreCitedWithRealSkillNames() {
        List<String> why = RecommendationExplainer.buildWhy(fullEvidence());
        assertTrue(why.stream().anyMatch(w -> w.contains("[Job Description]") && w.contains("Kubernetes")),
                "missing JD skills should be named: " + why);
    }

    @Test
    void claimedButWeakSkillsAreFlaggedAsACredibilityRisk() {
        List<String> why = RecommendationExplainer.buildWhy(fullEvidence());
        assertTrue(why.stream().anyMatch(w -> w.contains("Docker") && w.contains("resume")),
                "a skill claimed on the resume but underperformed should be called out: " + why);
    }

    @Test
    void weakestTopicIsCitedWithItsMeasuredAccuracy() {
        List<String> why = RecommendationExplainer.buildWhy(fullEvidence());
        assertTrue(why.stream().anyMatch(w -> w.contains("System Design") && w.contains("38.0")),
                "the weakest topic and its accuracy should appear: " + why);
    }

    @Test
    void unresolvedMistakeCountIsCited() {
        List<String> why = RecommendationExplainer.buildWhy(fullEvidence());
        assertTrue(why.stream().anyMatch(w -> w.contains("[Mistake Log]") && w.contains("9")),
                "unresolved mistake count should appear: " + why);
    }

    @Test
    void everyWhyLineNamesItsDataSource() {
        List<String> why = RecommendationExplainer.buildWhy(fullEvidence());
        assertFalse(why.isEmpty());
        for (String line : why) {
            assertTrue(line.startsWith("["), "each evidence line should be tagged with its source: " + line);
        }
    }

    @Test
    void missingMockInterviewIsReportedAsACoverageGap() {
        List<String> why = RecommendationExplainer.buildWhy(fullEvidence());
        assertTrue(why.stream().anyMatch(w -> w.contains("[Coverage]") && w.contains("mock interview")),
                "unscored dimensions should be explained: " + why);
    }

    // -----------------------------------------------------------------
    // Action selection
    // -----------------------------------------------------------------

    @Test
    void theSystemsOwnRecommendationIsExplainedNotOverridden() {
        ReadinessScore r = readinessWithDimensions(72.0, 31.0);
        r.setNextRecommendedAction("Run an AI mock interview.");
        RecommendationExplainer.Evidence e = new RecommendationExplainer.Evidence(
                "Role", r, List.of(skill("Java")), List.of(), List.of(), List.of(),
                new MistakeLogDAO.MistakeCounts(0, 0, 0), 50.0, "JD", List.of(), 60.0, 1, 0, 0, 0);
        assertEquals("Run an AI mock interview.", RecommendationExplainer.explainNextAction(e).action());
    }

    @Test
    void brandNewUserIsToldToUploadAResumeFirst() {
        RecommendationExplainer.Evidence empty = new RecommendationExplainer.Evidence(
                null, null, List.of(), List.of(), List.of(), List.of(),
                new MistakeLogDAO.MistakeCounts(0, 0, 0), null, null, List.of(), null, 0, 0, 0, 0);
        assertTrue(RecommendationExplainer.explainNextAction(empty).action().toLowerCase().contains("resume"));
    }

    @Test
    void userWithResumeButNoJobDescriptionIsPointedAtTheJdStep() {
        RecommendationExplainer.Evidence e = new RecommendationExplainer.Evidence(
                null, null, List.of(skill("Java")), List.of(), List.of(), List.of(),
                new MistakeLogDAO.MistakeCounts(0, 0, 0), null, null, List.of(), null, 0, 0, 0, 0);
        assertTrue(RecommendationExplainer.explainNextAction(e).action().toLowerCase().contains("job description"));
    }

    // -----------------------------------------------------------------
    // Next steps
    // -----------------------------------------------------------------

    @Test
    void nextStepsAreConcreteAndCapped() {
        List<String> steps = RecommendationExplainer.buildWhatNext(fullEvidence());
        assertFalse(steps.isEmpty());
        assertTrue(steps.size() <= 5, "the step list must stay scannable");
    }

    @Test
    void nextStepsReferenceTheUsersActualWeakTopic() {
        List<String> steps = RecommendationExplainer.buildWhatNext(fullEvidence());
        assertTrue(steps.stream().anyMatch(s -> s.contains("System Design")), steps.toString());
    }

    @Test
    void aUserWithNoRemainingGapsStillGetsAStep() {
        RecommendationExplainer.Evidence clean = new RecommendationExplainer.Evidence(
                "Role", readinessWithDimensions(88.0, 85.0), List.of(skill("Java")),
                List.of(), List.of(), List.of(), new MistakeLogDAO.MistakeCounts(10, 10, 0),
                92.0, "JD", List.of(), 90.0, 6, 3, 5, 5);
        List<String> steps = RecommendationExplainer.buildWhatNext(clean);
        assertEquals(1, steps.size());
        assertTrue(steps.get(0).toLowerCase().contains("higher difficulty"));
    }

    // -----------------------------------------------------------------
    // Roadmap item explanations
    // -----------------------------------------------------------------

    @Test
    void roadmapItemCitesBothJobRequirementAndMeasuredAccuracy() {
        String text = RecommendationExplainer.explainRoadmapItem(
                "Master System Design", "System Design", 38.0, true, false);
        assertTrue(text.contains("required skill"));
        assertTrue(text.contains("38.0"));
        assertTrue(text.contains("does not appear anywhere on your resume"));
    }

    @Test
    void roadmapItemFlagsClaimedSkillsWithWeakPerformance() {
        String text = RecommendationExplainer.explainRoadmapItem(
                "Strengthen Databases", "Databases", 54.0, false, true);
        assertTrue(text.contains("credibility risk"), text);
    }

    @Test
    void roadmapItemWithNoSpecificGapStillGivesAReason() {
        String text = RecommendationExplainer.explainRoadmapItem("Something", null, null, false, false);
        assertTrue(text.contains("dimension"), text);
        assertTrue(text.endsWith("."));
    }

    // -----------------------------------------------------------------
    // Helpers and null-safety
    // -----------------------------------------------------------------

    @Test
    void joinTopSummarisesLongListsInsteadOfPrintingAllOfThem() {
        assertEquals("a, b and 2 more", RecommendationExplainer.joinTop(List.of("a", "b", "c", "d"), 2));
        assertEquals("a, b", RecommendationExplainer.joinTop(List.of("a", "b"), 3));
        assertEquals("", RecommendationExplainer.joinTop(List.of(), 3));
        assertEquals("", RecommendationExplainer.joinTop(null, 3));
    }

    @Test
    void dimensionHelpersReturnNullWhenNoDimensionsExist() {
        assertNull(RecommendationExplainer.weakestDimension(null));
        assertNull(RecommendationExplainer.weakestDimension(new ReadinessScore()));
        assertNull(RecommendationExplainer.strongestDimension(new ReadinessScore()));
    }

    @Test
    void weakestAndStrongestDimensionsAreIdentifiedCorrectly() {
        ReadinessScore r = readinessWithDimensions(72.0, 31.0);
        assertEquals("Communication", RecommendationExplainer.weakestDimension(r).getLabel());
        assertEquals("Technical", RecommendationExplainer.strongestDimension(r).getLabel());
    }

    @Test
    void explanationWithNoDataHasNoEvidenceButStillHasAnAction() {
        RecommendationExplainer.Evidence empty = new RecommendationExplainer.Evidence(
                null, null, List.of(), List.of(), List.of(), List.of(), null,
                null, null, List.of(), null, 0, 0, 0, 0);
        RecommendationExplainer.Explanation ex = RecommendationExplainer.explainNextAction(empty);
        assertFalse(ex.hasEvidence());
        assertNotNull(ex.action());
        assertFalse(ex.whatNext().isEmpty());
    }
}
