package com.careerintelligence.ai;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for behavioral-question detection and the STAR
 * (Situation/Task/Action/Result) analysis added to
 * {@link AIServiceImpl.Evaluation} (no database, no API key required -
 * these exercise the local heuristic evaluator directly). Run with:
 * mvn test
 */
class AIServiceImplStarAnalysisTest {

    private final AIServiceImpl aiService = new AIServiceImpl();

    @Test
    void detectsCommonBehavioralQuestionPhrasings() {
        assertTrue(aiService.isBehavioralQuestion("Tell me about a time you had to deal with a difficult team member."));
        assertTrue(aiService.isBehavioralQuestion("Describe a situation where you disagreed with your manager."));
        assertTrue(aiService.isBehavioralQuestion("Give me an example of a time you missed a deadline."));
        assertTrue(aiService.isBehavioralQuestion("How did you handle a conflict with a coworker?"));
    }

    @Test
    void doesNotFlagPlainTechnicalQuestionsAsBehavioral() {
        assertFalse(aiService.isBehavioralQuestion("Explain how a HashMap works internally in Java."));
        assertFalse(aiService.isBehavioralQuestion("What is the time complexity of quicksort?"));
        assertFalse(aiService.isBehavioralQuestion(null));
    }

    @Test
    void nonBehavioralQuestionGetsNoStarAnalysis() {
        String question = "Explain the difference between SQL and NoSQL databases.";
        String modelAnswer = "SQL databases are relational with fixed schemas; NoSQL databases are "
                + "non-relational and more flexible for unstructured data.";
        AIServiceImpl.Evaluation eval = aiService.evaluate(question, modelAnswer, modelAnswer);
        assertNull(eval.starAnalysis(), "STAR analysis should not run for a purely technical question");
    }

    @Test
    void fullStarAnswerScoresHighOnEveryStarElement() {
        String question = "Tell me about a time you had to fix a critical production issue under pressure.";
        String modelAnswer = "A structured account of a real incident, including the impact, the steps taken "
                + "to resolve it, and the measurable outcome.";
        String candidateAnswer = "At my previous job, my task was to fix a critical bug before a client demo. "
                + "I decided to isolate the issue by adding logging, then rewrote the retry logic. As a result, "
                + "we reduced error rates by 40% and shipped on time.";

        AIServiceImpl.Evaluation eval = aiService.evaluate(question, modelAnswer, candidateAnswer);
        AIServiceImpl.StarAnalysis star = eval.starAnalysis();

        assertNotNull(star, "a behavioral question should always produce a STAR analysis");
        assertTrue(star.situationPresent(), "should detect the Situation cue");
        assertTrue(star.taskPresent(), "should detect the Task cue");
        assertTrue(star.actionPresent(), "should detect the Action cue");
        assertTrue(star.resultPresent(), "should detect the Result cue");
        assertEquals(100, star.starScore());
    }

    @Test
    void partialStarAnswerFlagsWhatIsMissing() {
        String question = "Describe a challenge you faced while leading a project.";
        String modelAnswer = "A description of the challenge, the candidate's specific actions, and the result.";
        String candidateAnswer = "I decided to isolate the issue by adding more logging around the payment flow.";

        AIServiceImpl.Evaluation eval = aiService.evaluate(question, modelAnswer, candidateAnswer);
        AIServiceImpl.StarAnalysis star = eval.starAnalysis();

        assertNotNull(star);
        assertTrue(star.actionPresent(), "the answer clearly describes an action taken");
        assertFalse(star.situationPresent(), "no context/situation was given");
        assertFalse(star.resultPresent(), "no outcome was given");
        assertTrue(star.starScore() < 100);
        assertTrue(star.feedback().toLowerCase().contains("situation") || star.feedback().toLowerCase().contains("result"),
                "feedback should call out what's missing");
    }

    @Test
    void emptyAnswerToBehavioralQuestionHasNoStarElements() {
        String question = "Tell me about a time you failed and what you learned from it.";
        String modelAnswer = "A candid account of a real failure and the concrete lesson learned from it.";

        AIServiceImpl.Evaluation eval = aiService.evaluate(question, modelAnswer, "");
        AIServiceImpl.StarAnalysis star = eval.starAnalysis();

        assertNotNull(star);
        assertEquals(0, star.starScore());
        assertFalse(star.situationPresent());
        assertFalse(star.taskPresent());
        assertFalse(star.actionPresent());
        assertFalse(star.resultPresent());
    }
}
