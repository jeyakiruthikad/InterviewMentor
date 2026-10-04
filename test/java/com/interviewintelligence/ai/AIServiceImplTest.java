package com.careerintelligence.ai;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the local heuristic path of the AI semantic evaluator
 * (no database, no API key required - these exercise ai.AIServiceImpl
 * directly). Run with: mvn test
 */
class AIServiceImplTest {

    private final AIServiceImpl aiService = new AIServiceImpl();

    private static final String QUESTION = "Describe the concept of garbage collection in Java.";
    private static final String MODEL_ANSWER =
            "Garbage collection is the automatic process by which the JVM reclaims memory occupied by "
                    + "objects that are no longer reachable from any live thread or static reference, freeing "
                    + "developers from manual memory deallocation.";

    @Test
    void emptyAnswerScoresZero() {
        AIServiceImpl.Evaluation eval = aiService.evaluate(QUESTION, MODEL_ANSWER, "");
        assertEquals(0, eval.score());
    }

    @Test
    void irrelevantAnswerScoresZero() {
        AIServiceImpl.Evaluation eval = aiService.evaluate(QUESTION, MODEL_ANSWER,
                "I like pizza and going to the beach on weekends with my friends.");
        assertEquals(0, eval.score());
    }

    @Test
    void exactModelAnswerScoresFull() {
        AIServiceImpl.Evaluation eval = aiService.evaluate(QUESTION, MODEL_ANSWER, MODEL_ANSWER);
        assertEquals(100, eval.score());
    }

    @Test
    void differentWordingSameMeaningScoresWell() {
        String reworded = "It's when the JVM automatically frees up memory used by objects that nothing "
                + "in the program references anymore, so the developer doesn't need to manually free memory.";
        AIServiceImpl.Evaluation eval = aiService.evaluate(QUESTION, MODEL_ANSWER, reworded);
        // Different wording, same meaning: should score well above the empty/irrelevant floor
        // and be reported as at least a partial/good answer, not a failure.
        assertTrue(eval.score() >= 55, "Expected a reasonably high score for a faithful paraphrase, got " + eval.score());
        assertTrue(eval.feedback().contains("Correct points covered"));
    }

    @Test
    void shortOneWordAnswerIsCapped() {
        AIServiceImpl.Evaluation eval = aiService.evaluate(QUESTION, MODEL_ANSWER, "memory");
        assertTrue(eval.score() <= 40, "A single keyword should not be scored as a complete explanation");
    }

    @Test
    void feedbackListsMissingConceptsForPartialAnswer() {
        AIServiceImpl.Evaluation eval = aiService.evaluate(QUESTION, MODEL_ANSWER,
                "It frees memory that is not used any more.");
        assertTrue(eval.feedback().contains("Missing points"));
    }
}
