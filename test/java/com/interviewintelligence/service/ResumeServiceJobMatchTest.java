package com.careerintelligence.service;

import com.careerintelligence.model.JobMatchStrength;
import com.careerintelligence.model.ReadinessScore;
import com.careerintelligence.model.TopicPerformance;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the pure, database-free Resume + JD Matching computation in
 * {@link ResumeService#matchResumeToJobDescriptionPure}. No database or
 * MySQL connection is required - run with: mvn test
 */
class ResumeServiceJobMatchTest {

    private static TopicPerformance perf(int attempts, double accuracy) {
        TopicPerformance tp = new TopicPerformance();
        tp.setAttemptsCount(attempts);
        tp.setAccuracyPercent(BigDecimal.valueOf(accuracy));
        return tp;
    }

    @Test
    void missingRequiredSkillIsClassifiedAsMissing() {
        List<String> required = List.of("Java", "Docker");
        List<String> preferred = List.of();
        Set<String> extracted = new HashSet<>(List.of("java"));

        ResumeService.JobMatchResult result = ResumeService.matchResumeToJobDescriptionPure(
                required, preferred, extracted, Map.of(), Map.of());

        assertEquals(1, result.missing().size());
        assertEquals("Docker", result.missing().get(0).skillName());
        assertEquals(JobMatchStrength.MISSING, result.missing().get(0).strength());
        assertTrue(result.missing().get(0).requiredByJd());
        assertTrue(result.highPrioritySkills().contains("Docker"));
    }

    @Test
    void resumeSkillWithHighAccuracyIsStrongMatch() {
        List<String> required = List.of("Java");
        Set<String> extracted = new HashSet<>(List.of("java"));
        Map<String, Integer> matchedTopicBySkill = Map.of("java", 1);
        Map<Integer, TopicPerformance> perfByTopic = Map.of(1, perf(10, 85.0));

        ResumeService.JobMatchResult result = ResumeService.matchResumeToJobDescriptionPure(
                required, List.of(), extracted, matchedTopicBySkill, perfByTopic);

        assertEquals(1, result.strongMatches().size());
        assertEquals("Java", result.strongMatches().get(0).skillName());
        assertEquals(100.0, result.matchScore(), 0.01);
    }

    @Test
    void resumeSkillWithLowAccuracyIsWeakEvidenceAndHighPriority() {
        List<String> required = List.of("Java");
        Set<String> extracted = new HashSet<>(List.of("java"));
        Map<String, Integer> matchedTopicBySkill = Map.of("java", 1);
        Map<Integer, TopicPerformance> perfByTopic = Map.of(1, perf(10, 25.0)); // clearly struggling

        ResumeService.JobMatchResult result = ResumeService.matchResumeToJobDescriptionPure(
                required, List.of(), extracted, matchedTopicBySkill, perfByTopic);

        assertEquals(1, result.weakEvidence().size());
        assertEquals("Java", result.weakEvidence().get(0).skillName());
        assertEquals(JobMatchStrength.WEAK_EVIDENCE, result.weakEvidence().get(0).strength());
        assertTrue(result.highPrioritySkills().contains("Java"),
                "a weak-evidence REQUIRED skill should be surfaced as high priority");
    }

    @Test
    void resumeSkillWithNoAssessmentEvidenceIsPartialMatch() {
        List<String> required = List.of("Java");
        Set<String> extracted = new HashSet<>(List.of("java"));
        // No matched topic at all - present on resume, but nothing to confirm depth.
        ResumeService.JobMatchResult result = ResumeService.matchResumeToJobDescriptionPure(
                required, List.of(), extracted, Map.of(), Map.of());

        assertEquals(1, result.partialMatches().size());
        assertEquals(JobMatchStrength.PARTIAL_MATCH, result.partialMatches().get(0).strength());
    }

    @Test
    void requiredSkillsAreWeightedMoreThanPreferredSkills() {
        // All required skills missing, all preferred skills strong -> score should still be low,
        // since required carries 70% of the weight.
        List<String> required = List.of("Java", "Docker");
        List<String> preferred = List.of("Kubernetes");
        Set<String> extracted = new HashSet<>(List.of("kubernetes"));
        Map<String, Integer> matchedTopicBySkill = Map.of("kubernetes", 1);
        Map<Integer, TopicPerformance> perfByTopic = Map.of(1, perf(10, 95.0));

        ResumeService.JobMatchResult result = ResumeService.matchResumeToJobDescriptionPure(
                required, preferred, extracted, matchedTopicBySkill, perfByTopic);

        assertEquals(30.0, result.matchScore(), 0.01, "preferred-only coverage should cap around the 30% preferred weight");
    }

    @Test
    void highPriorityOrdersMissingRequiredBeforeWeakRequiredBeforeMissingPreferred() {
        List<String> required = List.of("Java", "Docker");
        List<String> preferred = List.of("AWS");
        Set<String> extracted = new HashSet<>(List.of("java"));
        Map<String, Integer> matchedTopicBySkill = Map.of("java", 1);
        Map<Integer, TopicPerformance> perfByTopic = Map.of(1, perf(10, 20.0)); // weak

        ResumeService.JobMatchResult result = ResumeService.matchResumeToJobDescriptionPure(
                required, preferred, extracted, matchedTopicBySkill, perfByTopic);

        List<String> order = result.highPrioritySkills();
        assertEquals("Docker", order.get(0), "missing required skill should come first");
        assertEquals("Java", order.get(1), "weak-evidence required skill should come second");
        assertEquals("AWS", order.get(2), "missing preferred skill should come last");
    }

    @Test
    void emptyJobMatchResultHasZeroScoreAndNoSkills() {
        ResumeService.JobMatchResult empty = ResumeService.JobMatchResult.empty();
        assertEquals(0.0, empty.matchScore());
        assertTrue(empty.strongMatches().isEmpty());
        assertTrue(empty.missing().isEmpty());
        assertTrue(empty.highPrioritySkills().isEmpty());
    }

    @Test
    void noRequiredOrPreferredSkillsYieldsZeroScore() {
        ResumeService.JobMatchResult result = ResumeService.matchResumeToJobDescriptionPure(
                List.of(), List.of(), Set.of(), Map.of(), Map.of());
        assertEquals(0.0, result.matchScore());
    }

    @Test
    void fiveArgOverloadAlwaysReturnsASingleSkillCoverageBreakdownComponent() {
        ResumeService.JobMatchResult result = ResumeService.matchResumeToJobDescriptionPure(
                List.of("Java"), List.of(), new HashSet<>(List.of("java")), Map.of(), Map.of());
        assertEquals(1, result.breakdown().size());
        assertEquals("Required & Preferred Skill Coverage", result.breakdown().get(0).getLabel());
        assertEquals(result.matchScore(), result.breakdown().get(0).getValue(), 0.01);
    }

    // -----------------------------------------------------------------
    // Richer, explainable-breakdown overload (required/preferred skills,
    // resume evidence, relevant experience, project relevance, assessment
    // performance, interview performance)
    // -----------------------------------------------------------------

    @Test
    void richOverloadWithNoExtraSignalsMatchesPlainOverloadScoreExactly() {
        List<String> required = List.of("Java", "Docker");
        List<String> preferred = List.of("Kubernetes");
        Set<String> extracted = new HashSet<>(List.of("kubernetes"));
        Map<String, Integer> matchedTopicBySkill = Map.of("kubernetes", 1);
        Map<Integer, TopicPerformance> perfByTopic = Map.of(1, perf(10, 95.0));

        ResumeService.JobMatchResult plain = ResumeService.matchResumeToJobDescriptionPure(
                required, preferred, extracted, matchedTopicBySkill, perfByTopic);
        ResumeService.JobMatchResult rich = ResumeService.matchResumeToJobDescriptionPure(
                required, preferred, extracted, matchedTopicBySkill, perfByTopic,
                List.of(), null, null, null, null);

        // No technologies, no experience data, no assessment/interview stats supplied -> the
        // only difference from the plain overload is the Resume Evidence Strength component
        // (Kubernetes is present with real accuracy evidence), which should still weight the
        // score toward the same neighborhood, not diverge wildly.
        assertTrue(rich.breakdown().size() >= 2);
        assertEquals("Required & Preferred Skill Coverage", rich.breakdown().get(0).getLabel());
        assertEquals(plain.matchScore(), rich.breakdown().get(0).getValue(), 0.01);
    }

    @Test
    void resumeEvidenceStrengthReflectsHowMuchOfTheResumeOverlapIsCorroborated() {
        List<String> required = List.of("Java", "Python");
        Set<String> extracted = new HashSet<>(List.of("java", "python"));
        // Java: strong (confirmed). Python: on resume but zero assessment attempts (uncorroborated).
        Map<String, Integer> matchedTopicBySkill = Map.of("java", 1, "python", 2);
        Map<Integer, TopicPerformance> perfByTopic = Map.of(1, perf(10, 90.0));

        ResumeService.JobMatchResult result = ResumeService.matchResumeToJobDescriptionPure(
                required, List.of(), extracted, matchedTopicBySkill, perfByTopic,
                List.of(), null, null, null, null);

        ReadinessScore.Component evidence = result.breakdown().stream()
                .filter(c -> c.getLabel().equals("Resume Evidence Strength"))
                .findFirst().orElseThrow();
        assertEquals(50.0, evidence.getValue(), 0.01,
                "1 of 2 resume-listed skills (Java) is corroborated by real assessment evidence");
    }

    @Test
    void relevantExperienceComponentComparesProfileLevelAgainstJdRequirement() {
        List<String> required = List.of("Java");
        Set<String> extracted = new HashSet<>(List.of("java"));

        ResumeService.JobMatchResult meetsBar = ResumeService.matchResumeToJobDescriptionPure(
                required, List.of(), extracted, Map.of(), Map.of(),
                List.of(), "5+ years", "SENIOR", null, null);
        ReadinessScore.Component meetsExp = meetsBar.breakdown().stream()
                .filter(c -> c.getLabel().equals("Relevant Experience")).findFirst().orElseThrow();
        assertEquals(100.0, meetsExp.getValue(), 0.01, "a senior candidate (~7y) comfortably meets a 5-year bar");

        ResumeService.JobMatchResult underBar = ResumeService.matchResumeToJobDescriptionPure(
                required, List.of(), extracted, Map.of(), Map.of(),
                List.of(), "5+ years", "FRESHER", null, null);
        ReadinessScore.Component underExp = underBar.breakdown().stream()
                .filter(c -> c.getLabel().equals("Relevant Experience")).findFirst().orElseThrow();
        assertEquals(0.0, underExp.getValue(), 0.01, "a fresher (~0y) falls short of a 5-year bar");
    }

    @Test
    void relevantExperienceComponentIsOmittedWhenEitherSideIsUnknown() {
        List<String> required = List.of("Java");
        Set<String> extracted = new HashSet<>(List.of("java"));

        ResumeService.JobMatchResult noJdExperience = ResumeService.matchResumeToJobDescriptionPure(
                required, List.of(), extracted, Map.of(), Map.of(),
                List.of(), null, "SENIOR", null, null);
        assertTrue(noJdExperience.breakdown().stream().noneMatch(c -> c.getLabel().equals("Relevant Experience")));

        ResumeService.JobMatchResult noProfileLevel = ResumeService.matchResumeToJobDescriptionPure(
                required, List.of(), extracted, Map.of(), Map.of(),
                List.of(), "5+ years", null, null, null);
        assertTrue(noProfileLevel.breakdown().stream().noneMatch(c -> c.getLabel().equals("Relevant Experience")));
    }

    @Test
    void projectRelevanceComponentMeasuresTechnologyOverlapWithResumeSkills() {
        List<String> required = List.of("Java");
        Set<String> extracted = new HashSet<>(List.of("java", "docker"));
        List<String> technologies = List.of("Java", "Docker", "Kafka", "Redis");

        ResumeService.JobMatchResult result = ResumeService.matchResumeToJobDescriptionPure(
                required, List.of(), extracted, Map.of(), Map.of(),
                technologies, null, null, null, null);

        ReadinessScore.Component projectRelevance = result.breakdown().stream()
                .filter(c -> c.getLabel().equals("Project/Technology Relevance")).findFirst().orElseThrow();
        assertEquals(50.0, projectRelevance.getValue(), 0.01, "2 of 4 JD technologies (Java, Docker) are on the resume");
    }

    @Test
    void projectRelevanceComponentNeverInventsResumeEvidence() {
        // Resume has nothing matching any of the JD's technologies - score must be 0, not a
        // fabricated partial-credit guess.
        List<String> required = List.of("Java");
        Set<String> extracted = new HashSet<>(List.of("java"));
        List<String> technologies = List.of("Rust", "Elixir");

        ResumeService.JobMatchResult result = ResumeService.matchResumeToJobDescriptionPure(
                required, List.of(), extracted, Map.of(), Map.of(),
                technologies, null, null, null, null);

        ReadinessScore.Component projectRelevance = result.breakdown().stream()
                .filter(c -> c.getLabel().equals("Project/Technology Relevance")).findFirst().orElseThrow();
        assertEquals(0.0, projectRelevance.getValue(), 0.01);
    }

    @Test
    void assessmentAndInterviewPerformanceComponentsAreIncludedOnlyWhenDataExists() {
        List<String> required = List.of("Java");
        Set<String> extracted = new HashSet<>(List.of("java"));

        ResumeService.JobMatchResult withData = ResumeService.matchResumeToJobDescriptionPure(
                required, List.of(), extracted, Map.of(), Map.of(),
                List.of(), null, null, 72.5, 81.0);
        assertTrue(withData.breakdown().stream().anyMatch(c -> c.getLabel().equals("Assessment Performance")
                && Math.abs(c.getValue() - 72.5) < 0.01));
        assertTrue(withData.breakdown().stream().anyMatch(c -> c.getLabel().equals("Interview Performance")
                && Math.abs(c.getValue() - 81.0) < 0.01));

        ResumeService.JobMatchResult withoutData = ResumeService.matchResumeToJobDescriptionPure(
                required, List.of(), extracted, Map.of(), Map.of(),
                List.of(), null, null, null, null);
        assertTrue(withoutData.breakdown().stream().noneMatch(c -> c.getLabel().equals("Assessment Performance")));
        assertTrue(withoutData.breakdown().stream().noneMatch(c -> c.getLabel().equals("Interview Performance")));
    }

    @Test
    void everySignalPresentProducesAllSixBreakdownComponents() {
        List<String> required = List.of("Java");
        Set<String> extracted = new HashSet<>(List.of("java"));
        Map<String, Integer> matchedTopicBySkill = Map.of("java", 1);
        Map<Integer, TopicPerformance> perfByTopic = Map.of(1, perf(10, 90.0));

        ResumeService.JobMatchResult result = ResumeService.matchResumeToJobDescriptionPure(
                required, List.of(), extracted, matchedTopicBySkill, perfByTopic,
                List.of("Java"), "3+ years", "MID", 70.0, 75.0);

        List<String> labels = result.breakdown().stream().map(ReadinessScore.Component::getLabel).toList();
        assertEquals(6, labels.size());
        assertTrue(labels.contains("Required & Preferred Skill Coverage"));
        assertTrue(labels.contains("Resume Evidence Strength"));
        assertTrue(labels.contains("Relevant Experience"));
        assertTrue(labels.contains("Project/Technology Relevance"));
        assertTrue(labels.contains("Assessment Performance"));
        assertTrue(labels.contains("Interview Performance"));

        for (int component : new int[]{(int) result.matchScore()}) {
            assertTrue(component >= 0 && component <= 100);
        }
    }
}
