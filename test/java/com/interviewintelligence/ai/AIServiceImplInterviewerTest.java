package com.careerintelligence.ai;

import com.careerintelligence.ai.AIService.FollowUpType;
import com.careerintelligence.ai.AIService.InterviewPhase;
import com.careerintelligence.ai.AIService.InterviewTurnContext;
import com.careerintelligence.model.DifficultyLevel;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Local-fallback behaviour of the realistic interviewer (no API key or database needed). */
class AIServiceImplInterviewerTest {

    private final AIServiceImpl ai = new AIServiceImpl();

    private InterviewTurnContext ctx(InterviewPhase phase, FollowUpType type, String question, String answer,
                                      int score, List<String> missing, List<String> star, List<String> asked) {
        return new InterviewTurnContext("Backend Developer", phase, type, DifficultyLevel.MEDIUM,
                List.of("Java", "Spring Boot"), List.of("Kafka"), question, answer, score, missing, star, asked);
    }

    @Test
    void behavioralQuestionsStartWithIntroductionAndAreDistinct() {
        AIService.PersonalizationContext pc = new AIService.PersonalizationContext("Backend Developer",
                List.of("Java"), List.of(), List.of(), List.of(), null);
        List<String> qs = ai.generateBehavioralQuestions(pc, 3);
        assertEquals(3, qs.size());
        assertTrue(qs.get(0).toLowerCase().contains("about yourself"));
        assertTrue(qs.get(1).contains("Java"));
        assertTrue(ai.isBehavioralQuestion(qs.get(1)) && ai.isBehavioralQuestion(qs.get(2)));
        assertEquals(qs.size(), qs.stream().distinct().count());
    }

    @Test
    void weakAnswerGetsProbeAboutTheMissingConceptNotGenericFiller() {
        String out = ai.generateInterviewerFollowUp(ctx(InterviewPhase.TECHNICAL, FollowUpType.CLARIFY,
                "How does Spring Boot manage transactions?",
                "I use Spring Boot annotations on service methods and it handles things for me automatically.",
                40, List.of("proxy-based transaction management"), List.of(), List.of()));
        assertTrue(out.contains("proxy-based transaction management"), out);
        assertFalse(out.toLowerCase().contains("let me follow up"), out);
        assertTrue(out.contains("Spring Boot"), "should reference something the candidate said: " + out);
    }

    @Test
    void strongAnswerIsPushedDeeper() {
        String out = ai.generateInterviewerFollowUp(ctx(InterviewPhase.TECHNICAL, FollowUpType.DEEPEN,
                "Explain database indexing.",
                "Indexes use B-trees so lookups are logarithmic, with a write cost and selectivity trade-offs.",
                92, List.of(), List.of(), List.of()));
        String lower = out.toLowerCase();
        assertTrue(lower.contains("trade-off") || lower.contains("10x") || lower.contains("alternative"), out);
    }

    @Test
    void behavioralAnswerMissingResultIsProbedForTheResult() {
        String out = ai.generateInterviewerFollowUp(ctx(InterviewPhase.BEHAVIORAL, FollowUpType.STAR_PROBE,
                "Tell me about a time you used Java on a real project.",
                "At my last company I built a Java service for order processing and I designed the retry logic.",
                55, List.of(), List.of("Result"), List.of()));
        assertTrue(out.toLowerCase().contains("turn out") || out.toLowerCase().contains("outcome"), out);
        assertTrue(out.contains("Java"), out);
    }

    @Test
    void behavioralAnswerMissingActionIsProbedForActions() {
        String out = ai.generateInterviewerFollowUp(ctx(InterviewPhase.BEHAVIORAL, FollowUpType.STAR_PROBE,
                "Tell me about a challenge you faced.",
                "When our deployment pipeline kept failing the team was under pressure and results were mixed.",
                50, List.of(), List.of("Action"), List.of()));
        assertTrue(out.toLowerCase().contains("steps you personally took"), out);
    }

    @Test
    void veryShortAnswerGetsAConcreteExamplePrompt() {
        String out = ai.generateInterviewerFollowUp(ctx(InterviewPhase.TECHNICAL, FollowUpType.CLARIFY,
                "Explain REST.", "It is an API.", 10, List.of("statelessness"), List.of(), List.of()));
        assertFalse(out.isBlank());
        assertTrue(out.contains("?"));
    }

    @Test
    void followUpIsNeverBlankForAnyType() {
        for (FollowUpType type : FollowUpType.values()) {
            for (InterviewPhase phase : InterviewPhase.values()) {
                String out = ai.generateInterviewerFollowUp(ctx(phase, type, "Q?", "", 0, List.of(), List.of(), List.of()));
                assertNotNull(out);
                assertFalse(out.isBlank());
            }
        }
    }
}
