package com.careerintelligence.service;

import com.careerintelligence.dao.RoleSkillRequirementDAO;
import com.careerintelligence.model.TopicPerformance;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the pure, database-free Resume-Role Match computation in
 * {@link ResumeService#computeRoleMatchPure}. No database or MySQL
 * connection is required - run with: mvn test
 */
class ResumeServiceRoleMatchTest {

    private static TopicPerformance perf(int topicId, int attempts, double accuracy) {
        TopicPerformance tp = new TopicPerformance();
        tp.setTopicId(topicId);
        tp.setAttemptsCount(attempts);
        tp.setAccuracyPercent(BigDecimal.valueOf(accuracy));
        return tp;
    }

    @Test
    void matchPercentageCountsOnlyRequiredSkillsFoundOnResume() {
        List<RoleSkillRequirementDAO.Requirement> reqs = List.of(
                new RoleSkillRequirementDAO.Requirement("Java", "Programming Languages", 9),
                new RoleSkillRequirementDAO.Requirement("SQL", "Programming Languages", 9),
                new RoleSkillRequirementDAO.Requirement("Docker", "Cloud & DevOps", 4));
        Set<String> extracted = new HashSet<>(List.of("java", "sql"));

        ResumeService.RoleMatch match = ResumeService.computeRoleMatchPure(
                "Backend Developer", reqs, extracted, Map.of(), Map.of());

        assertEquals(66.67, match.matchPercentage(), 0.01);
        assertTrue(match.matchedSkills().containsAll(List.of("Java", "SQL")));
        assertEquals(List.of("Docker"), match.missingSkills());
    }

    @Test
    void missingSkillsOutrankWeakSkillsInPriorityGaps() {
        List<RoleSkillRequirementDAO.Requirement> reqs = List.of(
                new RoleSkillRequirementDAO.Requirement("Java", "Programming Languages", 9),
                new RoleSkillRequirementDAO.Requirement("Docker", "Cloud & DevOps", 4));
        Set<String> extracted = new HashSet<>(List.of("java"));
        Map<String, Integer> matchedTopicBySkill = Map.of("java", 1);
        Map<Integer, TopicPerformance> perfByTopic = Map.of(1, perf(1, 5, 30.0)); // weak: below 60%

        ResumeService.RoleMatch match = ResumeService.computeRoleMatchPure(
                "Backend Developer", reqs, extracted, matchedTopicBySkill, perfByTopic);

        assertFalse(match.priorityGaps().isEmpty());
        assertEquals("Docker", match.priorityGaps().get(0).skillName(),
                "a missing skill should always rank above a merely-weak one");
        assertTrue(match.priorityGaps().stream().anyMatch(g -> g.skillName().equals("Java")),
                "a claimed-but-weak skill should still be flagged as a priority gap");
    }

    @Test
    void wellPerformingMatchedSkillIsNotFlaggedAsAGap() {
        List<RoleSkillRequirementDAO.Requirement> reqs = List.of(
                new RoleSkillRequirementDAO.Requirement("Java", "Programming Languages", 9));
        Set<String> extracted = new HashSet<>(List.of("java"));
        Map<String, Integer> matchedTopicBySkill = Map.of("java", 1);
        Map<Integer, TopicPerformance> perfByTopic = Map.of(1, perf(1, 5, 90.0)); // strong

        ResumeService.RoleMatch match = ResumeService.computeRoleMatchPure(
                "Backend Developer", reqs, extracted, matchedTopicBySkill, perfByTopic);

        assertTrue(match.priorityGaps().isEmpty());
        assertEquals(100.0, match.matchPercentage(), 0.01);
    }

    @Test
    void emptyRoleMatchHasZeroPercentAndNoGaps() {
        ResumeService.RoleMatch empty = ResumeService.RoleMatch.empty(null);
        assertNull(empty.targetRole());
        assertEquals(0.0, empty.matchPercentage());
        assertTrue(empty.matchedSkills().isEmpty());
        assertTrue(empty.missingSkills().isEmpty());
        assertTrue(empty.priorityGaps().isEmpty());
    }
}
