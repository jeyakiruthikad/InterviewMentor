package com.careerintelligence.ai;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the Smart Follow-ups feature: a weak answer should get a
 * targeted probe into what it missed, and a strong answer should get a
 * harder/deeper question rather than the generic follow-up. No database or
 * API key required. Run with: mvn test
 */
class AIServiceImplAdaptiveFollowUpTest {

    private final AIServiceImpl aiService = new AIServiceImpl();

    private static final String PREVIOUS_QUESTION = "Explain how database indexing improves query performance.";
    private static final String PREVIOUS_ANSWER = "Indexes make lookups faster.";

    @Test
    void weakAnswerWithKnownMissingConceptGetsTargetedProbe() {
        String followUp = aiService.generateAdaptiveFollowUpQuestion(
                PREVIOUS_QUESTION, PREVIOUS_ANSWER, 40, List.of("B-tree structure"));

        assertTrue(followUp.toLowerCase().contains("b-tree structure"),
                "a weak answer with a known missing concept should be redirected straight to that concept");
    }

    @Test
    void strongAnswerGetsPushedDeeperRatherThanRepeatingTheSameLevel() {
        String followUp = aiService.generateAdaptiveFollowUpQuestion(
                PREVIOUS_QUESTION, "A thorough, well-structured answer covering B-trees, selectivity and trade-offs.",
                92, List.of());

        String lower = followUp.toLowerCase();
        assertTrue(lower.contains("scale") || lower.contains("trade-off") || lower.contains("edge case")
                        || lower.contains("deeper"),
                "a strong answer should be pushed toward a harder/deeper follow-up, got: " + followUp);
    }

    @Test
    void midRangeScoreWithNoMissingConceptsFallsBackToGenericFollowUp() {
        String followUp = aiService.generateAdaptiveFollowUpQuestion(
                PREVIOUS_QUESTION, PREVIOUS_ANSWER, 75, List.of());

        assertNotNull(followUp);
        assertFalse(followUp.isBlank());
    }

    @Test
    void followUpQuestionsAreNeverBlank() {
        for (int score : new int[]{0, 40, 69, 70, 84, 85, 100}) {
            String followUp = aiService.generateAdaptiveFollowUpQuestion(
                    PREVIOUS_QUESTION, PREVIOUS_ANSWER, score, List.of("indexing"));
            assertNotNull(followUp);
            assertFalse(followUp.isBlank(), "follow-up should never be blank at score " + score);
        }
    }
}
