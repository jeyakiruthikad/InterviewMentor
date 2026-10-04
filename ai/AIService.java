package com.careerintelligence.ai;

import com.careerintelligence.model.DifficultyLevel;

import java.util.List;

/**
 * Contract for AI/LLM-backed capabilities used by the application.
 * Provides the abstraction for all AI-assisted capabilities used by the
 * application, including resume intelligence, answer evaluation and mock
 * interview support.
 *
 *   - Resume parsing & skill extraction
 *   - Resume-based question generation
 *   - Semantic evaluation of descriptive answers
 *   - Mistake analysis & personalised feedback
 *   - Adaptive difficulty recommendation
 *   - AI mock interview with follow-up questions
 *   - Interview readiness scoring
 *
 * Implementations should call the LLM API configured via AI_API_KEY /
 * AI_API_BASE_URL / AI_MODEL in the .env file (see util.EnvLoader).
 */
public interface AIService {

    /** Extracts a normalised list of technical skills from raw resume text. */
    List<String> extractSkillsFromResume(String resumeText);

    /**
     * Analyses a free-text job description and extracts its structured
     * fields: required skills, preferred/nice-to-have skills, concrete
     * technologies, responsibilities, experience required, and soft
     * skills - the Job Description Analysis feature. Implementations use
     * the live LLM path when configured, transparently falling back to a
     * local heuristic (keyword/section + {@link SkillTaxonomy} matching)
     * otherwise, exactly like {@link #extractSkillsFromResume}.
     */
    JobDescriptionExtraction analyzeJobDescription(String jobDescriptionText);

    /**
     * Result of {@link #analyzeJobDescription}: every structured field the
     * Job Description Analysis feature extracts from raw JD text, plus a
     * best-effort short title (e.g. "Backend Developer") used only as a
     * display label.
     */
    record JobDescriptionExtraction(String title, List<String> requiredSkills, List<String> preferredSkills,
                                     List<String> technologies, List<String> responsibilities,
                                     List<String> softSkills, String experienceRequired) {
    }

    /** Generates descriptive/technical interview questions tailored to the given skills. */
    List<String> generateQuestionsForSkills(List<String> skills, int count);

    /**
     * Compares a candidate's free-text answer against a model answer and
     * returns a 0-100 semantic similarity/correctness score.
     */
    int evaluateDescriptiveAnswer(String questionText, String modelAnswer, String candidateAnswer);

    /** Produces a short natural-language explanation of why an answer was wrong and how to improve. */
    String generateMistakeFeedback(String questionText, String correctAnswer, String candidateAnswer);

    /** Generates a follow-up interview question based on the candidate's previous answer. */
    String generateFollowUpQuestion(String previousQuestion, String previousAnswer);

    /**
     * True if a question reads as a behavioral/situational interview question
     * ("Tell me about a time...", "Describe a situation where...", etc.),
     * i.e. one where a STAR (Situation/Task/Action/Result) answer structure
     * is expected. Used to decide whether {@link #evaluateDescriptiveAnswer}
     * / the richer evaluation path should also run STAR analysis.
     */
    boolean isBehavioralQuestion(String questionText);

    /**
     * Everything the Personalized Questions feature needs to tailor an
     * interview question set to one specific candidate, rather than a flat
     * skill list: their target role, the skills their resume already shows,
     * the skills the job description they're preparing for actually
     * requires, the skill gaps between the two (what to probe hardest),
     * topics they have previously gotten wrong (what to revisit), and the
     * difficulty the question set should currently be pitched at. Every
     * list defaults to empty (never null) and {@code currentDifficulty}
     * defaults to {@link DifficultyLevel#MEDIUM} if not supplied.
     */
    record PersonalizationContext(String targetRole, List<String> resumeSkills, List<String> jobRequiredSkills,
                                   List<String> skillGaps, List<String> previousMistakeTopics,
                                   DifficultyLevel currentDifficulty) {
        public PersonalizationContext {
            resumeSkills = resumeSkills == null ? List.of() : List.copyOf(resumeSkills);
            jobRequiredSkills = jobRequiredSkills == null ? List.of() : List.copyOf(jobRequiredSkills);
            skillGaps = skillGaps == null ? List.of() : List.copyOf(skillGaps);
            previousMistakeTopics = previousMistakeTopics == null ? List.of() : List.copyOf(previousMistakeTopics);
            currentDifficulty = currentDifficulty == null ? DifficultyLevel.MEDIUM : currentDifficulty;
        }
    }

    /**
     * Generates interview questions personalised against the candidate's
     * full context (resume, job description, target role, skill gaps,
     * previous mistakes and current difficulty) - the Personalized
     * Questions feature. Skill gaps and previously-mistaken topics are
     * prioritised first (the highest-leverage things to probe), and the
     * question phrasing itself adapts to {@code context.currentDifficulty()}.
     */
    List<String> generatePersonalizedQuestions(PersonalizationContext context, int count);

    /**
     * Smart follow-up (Smart Follow-ups feature): reacts to how well the
     * candidate actually did on the previous question, not just its text.
     * A weak/incomplete answer (low {@code previousScore}) gets a targeted
     * probing question aimed at the concepts it missed
     * ({@code missingConcepts}); a strong answer gets a harder, deeper
     * question instead of repeating the same difficulty level.
     */
    String generateAdaptiveFollowUpQuestion(String previousQuestion, String previousAnswer, int previousScore,
                                             List<String> missingConcepts);

    // -----------------------------------------------------------------
    // Realistic mock interview: behavioral opening + intelligent follow-ups
    // -----------------------------------------------------------------

    /** The two stages of a realistic interview: behavioral questions first, then personalised technical ones. */
    enum InterviewPhase { BEHAVIORAL, TECHNICAL }

    /**
     * What the interviewer wants to do next with the candidate's last answer:
     * {@code CLARIFY} - the answer was weak/vague, probe the gap;
     * {@code DEEPEN} - the answer was strong, ask something harder;
     * {@code STAR_PROBE} - a behavioral answer missed Situation/Task/Action/Result elements.
     */
    enum FollowUpType { CLARIFY, DEEPEN, STAR_PROBE }

    /**
     * Everything needed to write one context-aware interviewer reaction: the
     * stage, what kind of follow-up is wanted, the candidate profile, the
     * question/answer just exchanged, how the answer was assessed (score,
     * missing concepts, missing STAR elements) and every question already
     * asked in this session (so nothing is repeated). Lists never null.
     */
    record InterviewTurnContext(String targetRole, InterviewPhase phase, FollowUpType followUpType,
                                 DifficultyLevel difficulty, List<String> resumeSkills, List<String> skillGaps,
                                 String lastQuestion, String lastAnswer, int lastScore,
                                 List<String> missingConcepts, List<String> missingStarElements,
                                 List<String> alreadyAsked) {
        public InterviewTurnContext {
            difficulty = difficulty == null ? DifficultyLevel.MEDIUM : difficulty;
            resumeSkills = resumeSkills == null ? List.of() : List.copyOf(resumeSkills);
            skillGaps = skillGaps == null ? List.of() : List.copyOf(skillGaps);
            missingConcepts = missingConcepts == null ? List.of() : List.copyOf(missingConcepts);
            missingStarElements = missingStarElements == null ? List.of() : List.copyOf(missingStarElements);
            alreadyAsked = alreadyAsked == null ? List.of() : List.copyOf(alreadyAsked);
        }
    }

    /**
     * Opening behavioral questions (introduction, project experience,
     * teamwork, challenges ...), in that order, lightly personalised to the
     * candidate's target role and resume skills. Never returns more than
     * {@code count} questions and never blank entries.
     */
    List<String> generateBehavioralQuestions(PersonalizationContext context, int count);

    /**
     * Writes the interviewer's next utterance after a candidate answer: a
     * brief, specific reaction to what the candidate actually said followed
     * by exactly one question, shaped by {@link InterviewTurnContext#followUpType()}.
     * Uses the OpenAI-backed path when configured and a local context-aware
     * heuristic otherwise. Never returns blank.
     */
    String generateInterviewerFollowUp(InterviewTurnContext context);
}
