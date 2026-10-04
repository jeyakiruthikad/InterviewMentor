package com.careerintelligence.ai;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the accuracy/completeness/relevance/technical-concept
 * breakdown added to {@link AIServiceImpl.Evaluation} (no database, no API
 * key required - these exercise the local heuristic evaluator directly).
 * Run with: mvn test
 */
class AIServiceImplEvaluationBreakdownTest {

    private final AIServiceImpl aiService = new AIServiceImpl();

    private static final String QUESTION = "Describe the concept of garbage collection in Java.";
    private static final String MODEL_ANSWER =
            "Garbage collection is the automatic process by which the JVM reclaims memory occupied by "
                    + "objects that are no longer reachable from any live thread or static reference, freeing "
                    + "developers from manual memory deallocation.";

    @Test
    void twoArgConstructorFillsEveryComponentWithScoreForBackwardCompatibility() {
        AIServiceImpl.Evaluation eval = new AIServiceImpl.Evaluation(77, "some feedback");
        assertEquals(77, eval.score());
        assertEquals(77, eval.accuracy());
        assertEquals(77, eval.completeness());
        assertEquals(77, eval.relevance());
        assertEquals(77, eval.technicalConceptScore());
    }

    @Test
    void exactModelAnswerScoresHighOnEveryComponent() {
        AIServiceImpl.Evaluation eval = aiService.evaluate(QUESTION, MODEL_ANSWER, MODEL_ANSWER);
        assertEquals(100, eval.score());
        assertTrue(eval.completeness() >= 90, "completeness should be near-full for an exact match");
        assertTrue(eval.relevance() >= 90, "relevance should be near-full for an exact match");
        assertTrue(eval.technicalConceptScore() >= 90, "technical concept coverage should be near-full");
        assertTrue(eval.feedback().contains("Breakdown"));
    }

    @Test
    void emptyAnswerScoresZeroOnEveryComponent() {
        AIServiceImpl.Evaluation eval = aiService.evaluate(QUESTION, MODEL_ANSWER, "");
        assertEquals(0, eval.score());
        assertEquals(0, eval.accuracy());
        assertEquals(0, eval.completeness());
    }

    @Test
    void partialParaphraseHasLowerCompletenessThanExactMatch() {
        String reworded = "It's when the JVM automatically frees up memory used by objects that nothing "
                + "in the program references anymore, so the developer doesn't need to manually free memory.";
        AIServiceImpl.Evaluation exact = aiService.evaluate(QUESTION, MODEL_ANSWER, MODEL_ANSWER);
        AIServiceImpl.Evaluation partial = aiService.evaluate(QUESTION, MODEL_ANSWER, reworded);

        assertTrue(partial.completeness() < exact.completeness(),
                "a partial paraphrase should cover fewer of the model answer's concepts than an exact match");
        assertTrue(partial.completeness() > 0, "a genuine paraphrase should still register some completeness");
    }

    @Test
    void breakdownComponentsStayWithinValidRange() {
        AIServiceImpl.Evaluation eval = aiService.evaluate(QUESTION, MODEL_ANSWER, "memory is freed automatically");
        for (int component : new int[]{eval.score(), eval.accuracy(), eval.completeness(),
                eval.relevance(), eval.technicalConceptScore()}) {
            assertTrue(component >= 0 && component <= 100, "every component must be a 0-100 score, got " + component);
        }
    }

    // -----------------------------------------------------------------
    // Live LLM JSON response parsing (parseLiveEvaluationResponse) - no
    // network/API key required, exercises the independent-dimension
    // validation + incomplete-response fallback trigger directly.
    // -----------------------------------------------------------------

    private static final String TECHNICAL_QUESTION = "Explain how a hash map works.";

    @Test
    void wellFormedLiveResponseWithDistinctDimensionsIsUsedAsIs() {
        String json = "{\"score\": 82, \"accuracy\": 90, \"completeness\": 75, \"relevance\": 88, "
                + "\"concept_coverage\": 70, \"depth\": 65, \"communication\": 95, "
                + "\"correct_points\": [\"hashing\"], \"missing_points\": [\"collision handling\"], "
                + "\"incorrect_points\": [], \"suggestion\": \"mention collisions\"}";
        AIServiceImpl.Evaluation eval = aiService.parseLiveEvaluationResponse(
                TECHNICAL_QUESTION, "A hash map hashes keys into buckets.", false, json);

        assertNotNull(eval, "a complete, non-uniform response should be accepted");
        assertEquals(82, eval.score());
        assertEquals(90, eval.accuracy());
        assertEquals(75, eval.completeness());
        assertEquals(88, eval.relevance());
        assertEquals(70, eval.technicalConceptScore());
        assertEquals(65, eval.depth());
        assertEquals(95, eval.communication());
        // At least one dimension must differ from the overall score - proof this isn't the old bug.
        assertTrue(eval.accuracy() != eval.score() || eval.completeness() != eval.score()
                || eval.relevance() != eval.score(), "dimensions should not all collapse to the overall score");
    }

    @Test
    void liveResponseMissingDimensionsFallsBackToNull() {
        // Old/incomplete shape: only "score", no per-dimension breakdown at all.
        String json = "{\"score\": 60, \"correct_points\": [], \"missing_points\": [], "
                + "\"incorrect_points\": [], \"suggestion\": \"ok\"}";
        AIServiceImpl.Evaluation eval = aiService.parseLiveEvaluationResponse(
                TECHNICAL_QUESTION, "some answer", false, json);

        assertNull(eval, "a response with no independent dimension breakdown must be rejected, "
                + "not patched by copying the overall score into every dimension");
    }

    @Test
    void liveResponseWithIdenticalDimensionsFallsBackToNull() {
        // Every dimension present, but all identical to each other (and to the overall score) -
        // exactly the bug this fix targets - must still be rejected.
        String json = "{\"score\": 70, \"accuracy\": 70, \"completeness\": 70, \"relevance\": 70, "
                + "\"concept_coverage\": 70, \"depth\": 70, \"communication\": 70, "
                + "\"correct_points\": [], \"missing_points\": [], \"incorrect_points\": [], \"suggestion\": \"\"}";
        AIServiceImpl.Evaluation eval = aiService.parseLiveEvaluationResponse(
                TECHNICAL_QUESTION, "some answer", false, json);

        assertNull(eval, "identical values across every dimension must be rejected as non-independent");
    }

    @Test
    void malformedLiveJsonFallsBackToNull() {
        AIServiceImpl.Evaluation eval = aiService.parseLiveEvaluationResponse(
                TECHNICAL_QUESTION, "some answer", false, "not valid json at all");
        assertNull(eval, "malformed JSON must be rejected rather than throw out of the caller's try block");
    }

    @Test
    void behavioralQuestionRequiresStarSpecificityAndImpactTooOrFallsBack() {
        String questionText = "Tell me about a time you resolved a conflict with a teammate.";
        String answer = "When I was working on a team, my task was to resolve the disagreement. "
                + "I decided to set up a meeting and communicated openly. As a result, we improved "
                + "delivery by 20%.";
        // Missing star/specificity/impact even though it's a behavioral question.
        String incompleteJson = "{\"score\": 80, \"accuracy\": 85, \"completeness\": 80, \"relevance\": 90, "
                + "\"concept_coverage\": 75, \"depth\": 70, \"communication\": 88, "
                + "\"correct_points\": [], \"missing_points\": [], \"incorrect_points\": [], \"suggestion\": \"\"}";
        assertNull(aiService.parseLiveEvaluationResponse(questionText, answer, true, incompleteJson),
                "a behavioral question missing star/specificity/impact must be treated as incomplete");

        String completeJson = "{\"score\": 80, \"accuracy\": 85, \"completeness\": 80, \"relevance\": 90, "
                + "\"concept_coverage\": 75, \"depth\": 70, \"communication\": 88, "
                + "\"star\": 100, \"specificity\": 65, \"impact\": 72, "
                + "\"correct_points\": [], \"missing_points\": [], \"incorrect_points\": [], \"suggestion\": \"\"}";
        AIServiceImpl.Evaluation eval = aiService.parseLiveEvaluationResponse(questionText, answer, true, completeJson);
        assertNotNull(eval, "a complete behavioral response should be accepted");
        assertNotNull(eval.starAnalysis(), "behavioral questions should carry a STAR analysis");
        assertTrue(eval.feedback().contains("Specificity"), "feedback should surface the independently-scored specificity");
        assertTrue(eval.feedback().contains("Impact"), "feedback should surface the independently-scored impact");
    }
}
