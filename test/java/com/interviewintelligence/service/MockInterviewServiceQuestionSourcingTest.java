package com.careerintelligence.service;

import com.careerintelligence.ai.AIService;
import com.careerintelligence.model.DifficultyLevel;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the pure, database-free logic in
 * {@link MockInterviewService}: the grading-skill order used to pick a
 * reasonable reference/model answer for each AI-generated personalised
 * question. No database or MySQL connection is required. Run with:
 * mvn test
 */
class MockInterviewServiceQuestionSourcingTest {

    @Test
    void prioritisesSkillGapsAndPreviousMistakesOverResumeSkills() {
        AIService.PersonalizationContext context = new AIService.PersonalizationContext(
                "Backend Developer", List.of("Java"), List.of("Java", "Kubernetes"), List.of("Kubernetes"),
                List.of("SQL joins"), DifficultyLevel.MEDIUM);

        List<String> order = MockInterviewService.buildGradingSkillOrder(context, List.of("Java"), "Backend Developer");

        assertEquals(List.of("Kubernetes", "SQL joins", "Java"), order);
    }

    @Test
    void fallsBackToExplicitResumeSkillsWhenNoContextGiven() {
        List<String> order = MockInterviewService.buildGradingSkillOrder(null, List.of("Python", "Django"), "Backend Developer");

        assertEquals(List.of("Python", "Django"), order);
    }

    @Test
    void fallsBackToRoleNameWhenNothingElseIsAvailable() {
        List<String> order = MockInterviewService.buildGradingSkillOrder(null, List.of(), "Data Analyst");

        assertEquals(List.of("Data Analyst"), order);
    }

    @Test
    void returnsEmptyListWhenNothingIsAvailableAtAll() {
        List<String> order = MockInterviewService.buildGradingSkillOrder(null, List.of(), null);

        assertTrue(order.isEmpty());
    }
}
