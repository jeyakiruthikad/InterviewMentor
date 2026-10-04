package com.careerintelligence.ai;

import com.careerintelligence.model.DifficultyLevel;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the Personalized Questions feature: the skill ordering that
 * prioritises skill gaps and JD-required-but-missing skills over plain
 * resume skills, and the difficulty-specific question phrasing. No database
 * or API key required - these exercise the local heuristic engine directly.
 * Run with: mvn test
 */
class AIServiceImplPersonalizedQuestionsTest {

    private final AIServiceImpl aiService = new AIServiceImpl();

    @Test
    void skillOrderPrioritisesGapsThenMissingJdSkillsThenResumeSkills() {
        AIService.PersonalizationContext context = new AIService.PersonalizationContext(
                "Backend Developer",
                List.of("Java", "Python"),
                List.of("Java", "Kubernetes", "SQL"),
                List.of("Kubernetes"),
                List.of(),
                DifficultyLevel.MEDIUM);

        List<String> order = AIServiceImpl.buildPersonalizedSkillOrder(context);

        assertEquals(List.of("Kubernetes", "SQL", "Java", "Python"), order,
                "skill gaps first, then JD-required skills not already on the resume, then resume skills");
    }

    @Test
    void skillGapsAreNotDuplicatedIfAlsoJobRequired() {
        AIService.PersonalizationContext context = new AIService.PersonalizationContext(
                null, List.of(), List.of("Docker"), List.of("Docker"), List.of(), DifficultyLevel.MEDIUM);

        List<String> order = AIServiceImpl.buildPersonalizedSkillOrder(context);

        assertEquals(List.of("Docker"), order, "the same skill should not appear twice");
    }

    @Test
    void generatesRequestedNumberOfQuestions() {
        AIService.PersonalizationContext context = new AIService.PersonalizationContext(
                "Backend Developer", List.of("Java"), List.of("Java", "AWS"), List.of("AWS"),
                List.of(), DifficultyLevel.MEDIUM);

        List<String> questions = aiService.generatePersonalizedQuestions(context, 3);

        assertEquals(3, questions.size());
        assertTrue(questions.stream().allMatch(q -> q != null && !q.isBlank()));
    }

    @Test
    void leadsWithARecapOfThePreviousMistakeTopic() {
        AIService.PersonalizationContext context = new AIService.PersonalizationContext(
                "Backend Developer", List.of("Java"), List.of("Java"), List.of(),
                List.of("database indexing"), DifficultyLevel.MEDIUM);

        List<String> questions = aiService.generatePersonalizedQuestions(context, 2);

        assertFalse(questions.isEmpty());
        assertTrue(questions.get(0).toLowerCase().contains("database indexing"),
                "the very first question should revisit the topic the candidate previously got wrong");
    }

    @Test
    void hardDifficultyProducesMateriallyDifferentPhrasingThanEasy() {
        AIService.PersonalizationContext easyContext = new AIService.PersonalizationContext(
                null, List.of(), List.of(), List.of("SQL"), List.of(), DifficultyLevel.EASY);
        AIService.PersonalizationContext hardContext = new AIService.PersonalizationContext(
                null, List.of(), List.of(), List.of("SQL"), List.of(), DifficultyLevel.HARD);

        List<String> easyQuestions = aiService.generatePersonalizedQuestions(easyContext, 1);
        List<String> hardQuestions = aiService.generatePersonalizedQuestions(hardContext, 1);

        assertNotEquals(easyQuestions.get(0), hardQuestions.get(0),
                "the same skill should produce different question phrasing at different difficulty levels");
    }

    @Test
    void emptyContextWithNoRoleReturnsNoQuestions() {
        AIService.PersonalizationContext context = new AIService.PersonalizationContext(
                null, List.of(), List.of(), List.of(), List.of(), DifficultyLevel.MEDIUM);

        List<String> questions = aiService.generatePersonalizedQuestions(context, 3);

        assertTrue(questions.isEmpty(), "with no skills anywhere and no target role, there's nothing to personalise against");
    }

    @Test
    void nullContextReturnsEmptyList() {
        assertTrue(aiService.generatePersonalizedQuestions(null, 5).isEmpty());
    }
}
