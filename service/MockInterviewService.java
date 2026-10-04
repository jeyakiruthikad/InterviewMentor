package com.careerintelligence.service;

import com.careerintelligence.ai.AIService;
import com.careerintelligence.ai.AIServiceImpl;
import com.careerintelligence.dao.MockInterviewDAO;
import com.careerintelligence.dao.QuestionDAO;
import com.careerintelligence.model.*;
import com.careerintelligence.util.TextSimilarity;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Business logic for the AI Mock Interview feature (current implementation).
 * Interviews now flow Behavioral (2-3 questions) -> Personalized Technical ->
 * answer analysis -> context-aware follow-up (clarify / deepen / STAR probe,
 * capped and de-duplicated) -> final evaluation, powered by the OpenAI-backed
 * {@link AIServiceImpl} with its local fallback.
 *
 * role/topic selection, sourcing questions (preferring real descriptive
 * questions from the existing question bank, then AI-generated questions
 * personalised to the candidate's resume/JD/skill-gaps/past-mistakes when
 * the bank doesn't have enough - see {@link AIService.PersonalizationContext}),
 * grading each free-text answer with {@link AIServiceImpl}'s semantic
 * evaluator (score, accuracy/completeness/relevance/technical-concept/depth/
 * communication breakdown, strengths, missing concepts, improvement advice,
 * and - for behavioral questions - a STAR analysis), generating a Smart
 * Follow-up that reacts to how well the candidate did (targeted probing for
 * a weak answer, a harder/deeper question for a strong one), and producing
 * the final Mock Interview Report (score, strengths/weaknesses, key
 * mistakes, readiness impact, and a recommended next action).
 */
public class MockInterviewService {

    /** Answers scoring below this are "weak" - they trigger a targeted probing follow-up. */
    private static final int FOLLOWUP_SCORE_THRESHOLD = 70;
    /** Answers scoring at/above this are "strong" - they trigger a harder/deeper follow-up instead. */
    private static final int STRONG_FOLLOWUP_THRESHOLD = 85;

    /** Hard limits so the interview keeps moving instead of interrogating one topic forever. */
    static final int MAX_FOLLOWUPS_PER_SESSION = 4;
    static final int MAX_DEPTH_TECHNICAL = 2;
    static final int MAX_DEPTH_BEHAVIORAL = 1;
    /** A behavioral answer whose STAR score is below this (i.e. a missing S/T/A/R element) is probed. */
    private static final int STAR_COMPLETE_SCORE = 75;

    private static final String BEHAVIORAL_MODEL_ANSWER =
            "A specific, real example told as a clear story: the situation and context, the candidate's own "
                    + "task or responsibility, the concrete actions they personally took and why, and the measurable "
                    + "result or lesson learned.";

    private static final String[] GENERIC_FALLBACK_QUESTIONS = {
            "Tell me about a project you're proud of, and describe your specific individual contribution to it."
    };
    private static final String GENERIC_FALLBACK_MODEL_ANSWER =
            "A clear description of a specific, real project: the problem it solved, the candidate's own "
                    + "individual contribution (not just the team's), key technical decisions made, challenges "
                    + "faced and how they were resolved, and the measurable outcome or impact.";

    private final MockInterviewDAO mockInterviewDAO = new MockInterviewDAO();
    private final QuestionDAO questionDAO = new QuestionDAO();
    private final AIServiceImpl aiService = new AIServiceImpl();
    private final ReadinessScoreService readinessScoreService = new ReadinessScoreService();
    private final MistakeAnalyzerService mistakeAnalyzerService = new MistakeAnalyzerService();
    /** Candidate context per live session (in memory only - no schema change), used to personalise follow-ups. */
    private final Map<Long, AIService.PersonalizationContext> sessionContexts = new ConcurrentHashMap<>();

    /** A question actually shown to the candidate, paired with the (never-displayed) reference text used to AI-grade the answer. */
    public record QuestionAsked(MockInterviewQuestion question, String modelAnswerForGrading,
                                 AIService.InterviewPhase phase, int followUpDepth) {
        /** Backward-compatible: a plain technical main question (depth 0). */
        public QuestionAsked(MockInterviewQuestion question, String modelAnswerForGrading) {
            this(question, modelAnswerForGrading, AIService.InterviewPhase.TECHNICAL, 0);
        }
    }

    public record SessionBundle(MockInterviewSession session, List<QuestionAsked> questions) {
    }

    /**
     * Full AI Answer Evaluation result for one submitted answer: the overall
     * score/feedback, the accuracy/completeness/relevance/technical-concept/
     * depth/communication breakdown, the concrete strengths and missing
     * concepts behind those numbers, a targeted improvement suggestion, a
     * STAR analysis (only non-null for behavioral questions), and the Smart
     * Follow-up question this answer triggered, if any.
     */
    public record AnswerResult(int score, String feedback, int accuracy, int completeness, int relevance,
                                int technicalConceptScore, int depth, int communication, List<String> strengths,
                                List<String> missingConcepts, String improvementAdvice,
                                AIServiceImpl.StarAnalysis starAnalysis, QuestionAsked followUp) {
    }

    public record FinalFeedback(BigDecimal averageScore, BigDecimal readinessScore,
                                 List<MockInterviewQuestion> strengths, List<MockInterviewQuestion> weaknesses) {
    }

    /**
     * The Mock Interview Report (current implementation): the session's
     * overall score and strongest/weakest answers (from {@link FinalFeedback}),
     * the concrete key mistakes made during the session (which question, what
     * score, and the AI feedback explaining why), how many previously-logged
     * mistakes are still unresolved elsewhere in the app (via
     * {@link MistakeAnalyzerService}), the Interview Readiness Score's
     * before/after movement and band (via {@link ReadinessScoreService}), and
     * the system's single Next Best Action recommendation - so a candidate
     * finishing a session sees not just a score, but what to do next.
     */
    public record MockInterviewReport(BigDecimal overallScore, List<MockInterviewQuestion> strengths,
                                       List<MockInterviewQuestion> weaknesses, List<String> keyMistakes,
                                       int unresolvedMistakesElsewhere, double readinessScoreBefore,
                                       double readinessScoreAfter, String readinessBand,
                                       String recommendedNextAction) {

        /** The Interview Readiness Score delta this session produced, e.g. +3.5 or -1.2. */
        public double readinessDelta() {
            return Math.round((readinessScoreAfter - readinessScoreBefore) * 100.0) / 100.0;
        }
    }

    /**
     * Starts a new mock interview session using only explicit topic/skill
     * selections (no candidate-context personalisation). Kept as the
     * original, backward-compatible entry point.
     */
    public SessionBundle startSession(Long userId, String roleName, String companyName,
                                       List<Integer> topicIds, List<String> topicNames,
                                       List<String> resumeSkills, int numQuestions) throws SQLException {
        return startSession(userId, roleName, companyName, topicIds, topicNames, resumeSkills, numQuestions, null);
    }

    /**
     * Starts a new mock interview session: builds the initial question set
     * (real descriptive questions from the matched topics first, then - if
     * a {@code personalizationContext} is supplied - AI-generated questions
     * personalised to the candidate's resume/JD/skill-gaps/past-mistakes/
     * difficulty for any remaining slots, falling back to the plain
     * skill-name generator if no context was supplied) and persists the
     * session + questions.
     */
    public SessionBundle startSession(Long userId, String roleName, String companyName,
                                       List<Integer> topicIds, List<String> topicNames,
                                       List<String> resumeSkills, int numQuestions,
                                       AIService.PersonalizationContext personalizationContext) throws SQLException {
        int behavioralCount = behavioralCountFor(numQuestions);
        int technicalCount = Math.max(1, numQuestions - behavioralCount);

        // Stage 1: 2-3 behavioral questions (introduction, project experience, teamwork, challenges ...).
        AIService.PersonalizationContext behavioralContext = personalizationContext != null ? personalizationContext
                : new AIService.PersonalizationContext(roleName, resumeSkills, List.of(), List.of(), List.of(), null);
        List<PreparedQuestion> prepared = new ArrayList<>();
        for (String text : aiService.generateBehavioralQuestions(behavioralContext, behavioralCount)) {
            prepared.add(new PreparedQuestion(text, BEHAVIORAL_MODEL_ANSWER, AIService.InterviewPhase.BEHAVIORAL));
        }

        // Stage 2: personalised technical questions (topic bank, resume, skill gaps, past mistakes, difficulty).
        for (PreparedQuestion tq : buildQuestionPool(topicIds, topicNames, resumeSkills, roleName,
                technicalCount, personalizationContext)) {
            if (prepared.stream().noneMatch(p -> TextSimilarity.isNearDuplicate(p.text(), tq.text()))) {
                prepared.add(tq);
            }
        }

        MockInterviewSession session = new MockInterviewSession();
        session.setUserId(userId);
        session.setRoleName(roleName);
        session.setCompanyName(companyName);
        session.setTopicIds(topicIds == null ? List.of() : topicIds);
        session.setStatus(MockSessionStatus.IN_PROGRESS);
        session.setTotalQuestions(prepared.size());
        mockInterviewDAO.createSession(session);
        sessionContexts.put(session.getSessionId(), behavioralContext);

        List<QuestionAsked> asked = new ArrayList<>();
        int order = 1;
        for (PreparedQuestion pq : prepared) {
            MockInterviewQuestion mq = new MockInterviewQuestion();
            mq.setSessionId(session.getSessionId());
            mq.setQuestionOrder(order++);
            mq.setQuestionText(pq.text());
            mq.setFollowup(false);
            mockInterviewDAO.addQuestion(mq);
            asked.add(new QuestionAsked(mq, pq.modelAnswer(), pq.phase(), 0));
        }
        return new SessionBundle(session, asked);
    }

    /** 2-3 behavioral openers: three for a full-length interview, two for shorter ones, none only for a single question. */
    static int behavioralCountFor(int totalQuestions) {
        if (totalQuestions <= 1) return 0;
        return Math.min(totalQuestions - 1, totalQuestions >= 6 ? 3 : 2);
    }

    /**
     * Decides whether (and how) the interviewer follows up on an answer.
     * Weak technical answer -> CLARIFY; strong -> DEEPEN; behavioral answer
     * missing STAR elements -> STAR_PROBE. Returns {@code null} (move on) once
     * the per-chain or per-session limits are reached or the answer was
     * simply fine, so the interview progresses naturally.
     */
    static AIService.FollowUpType decideFollowUp(int score, AIService.InterviewPhase phase, int depth,
                                                  int followUpsSoFar, AIServiceImpl.StarAnalysis star) {
        if (followUpsSoFar >= MAX_FOLLOWUPS_PER_SESSION) {
            return null;
        }
        boolean weak = score < FOLLOWUP_SCORE_THRESHOLD;
        boolean strong = score >= STRONG_FOLLOWUP_THRESHOLD;

        if (phase == AIService.InterviewPhase.BEHAVIORAL) {
            if (depth >= MAX_DEPTH_BEHAVIORAL) return null;
            if (star == null) { // e.g. the introduction question: only clarify a thin answer
                return weak ? AIService.FollowUpType.CLARIFY : null;
            }
            if (star.starScore() < STAR_COMPLETE_SCORE) return AIService.FollowUpType.STAR_PROBE;
            return strong ? AIService.FollowUpType.DEEPEN : null;
        }

        if (depth >= MAX_DEPTH_TECHNICAL) return null;
        if (weak) {
            // One clarification per chain: if they are still weak after probing, move on rather than grind.
            return depth == 0 ? AIService.FollowUpType.CLARIFY : null;
        }
        return strong ? AIService.FollowUpType.DEEPEN : null;
    }

    /** A short, varied bridge line used between main questions (when no follow-up is asked). */
    public static String bridgeLine(int score) {
        if (score >= STRONG_FOLLOWUP_THRESHOLD) return "That was a clear, well-structured answer. Let's keep going.";
        if (score >= FOLLOWUP_SCORE_THRESHOLD) return "Okay, that makes sense. Moving on.";
        return "Alright, let's come back to that another time and move on.";
    }

    /**
     * Grades one answer with the same AI semantic evaluator used by the
     * assessment engine (now returning the full accuracy/completeness/
     * relevance/technical-concept/depth/communication breakdown, strengths,
     * missing concepts, improvement advice, and STAR analysis for behavioral
     * questions), persists the score/feedback, and - unless this answer was
     * itself already a follow-up - generates exactly one Smart Follow-up:
     * a targeted probe into what was missing for a weak answer, or a
     * harder/deeper question for a strong one.
     */
    public AnswerResult submitAnswer(MockInterviewSession session, QuestionAsked asked, String answerText) throws SQLException {
        AIServiceImpl.Evaluation evaluation = aiService.evaluate(
                asked.question().getQuestionText(), asked.modelAnswerForGrading(), answerText);

        mockInterviewDAO.saveAnswer(asked.question().getId(), answerText, evaluation.score(), evaluation.feedback());

        QuestionAsked followUp = null;
        List<MockInterviewQuestion> sessionSoFar = mockInterviewDAO.findQuestions(session.getSessionId());
        int followUpsSoFar = (int) sessionSoFar.stream().filter(MockInterviewQuestion::isFollowup).count();
        AIService.FollowUpType type = decideFollowUp(evaluation.score(), asked.phase(), asked.followUpDepth(),
                followUpsSoFar, evaluation.starAnalysis());

        if (type != null) {
            List<String> alreadyAsked = sessionSoFar.stream().map(MockInterviewQuestion::getQuestionText).toList();
            AIService.PersonalizationContext candidate = sessionContexts.get(session.getSessionId());
            AIService.InterviewTurnContext turn = new AIService.InterviewTurnContext(
                    session.getRoleName(), asked.phase(), type,
                    candidate == null ? null : candidate.currentDifficulty(),
                    candidate == null ? List.of() : candidate.resumeSkills(),
                    candidate == null ? List.of() : candidate.skillGaps(),
                    asked.question().getQuestionText(), answerText, evaluation.score(),
                    evaluation.missingConcepts(), missingStarElements(evaluation.starAnalysis()), alreadyAsked);
            String followUpText = aiService.generateInterviewerFollowUp(turn);

            boolean repeat = followUpText == null || followUpText.isBlank()
                    || alreadyAsked.stream().anyMatch(q -> TextSimilarity.isNearDuplicate(q, followUpText));
            if (!repeat) {
                int nextOrder = mockInterviewDAO.maxQuestionOrder(session.getSessionId()) + 1;
                MockInterviewQuestion mq = new MockInterviewQuestion();
                mq.setSessionId(session.getSessionId());
                mq.setQuestionOrder(nextOrder);
                mq.setQuestionText(followUpText.trim());
                mq.setFollowup(true);
                mq.setParentQuestionId(asked.question().getId());
                mockInterviewDAO.addQuestion(mq);

                // The follow-up digs into the same underlying concept, so the same reference text is still
                // a reasonable (if imperfect) grading target - far better than grading with no reference at all.
                followUp = new QuestionAsked(mq, asked.modelAnswerForGrading(), asked.phase(),
                        asked.followUpDepth() + 1);
            }
        }

        return new AnswerResult(evaluation.score(), evaluation.feedback(), evaluation.accuracy(),
                evaluation.completeness(), evaluation.relevance(), evaluation.technicalConceptScore(),
                evaluation.depth(), evaluation.communication(), evaluation.strengths(), evaluation.missingConcepts(),
                evaluation.improvementAdvice(), evaluation.starAnalysis(), followUp);
    }

    private static List<String> missingStarElements(AIServiceImpl.StarAnalysis star) {
        List<String> missing = new ArrayList<>();
        if (star == null) return missing;
        if (!star.situationPresent()) missing.add("Situation");
        if (!star.taskPresent()) missing.add("Task");
        if (!star.actionPresent()) missing.add("Action");
        if (!star.resultPresent()) missing.add("Result");
        return missing;
    }

    /** Finalises the session: average score across all answered questions, a readiness contribution, and strengths/weaknesses. */
    public FinalFeedback finalizeSession(Long sessionId) throws SQLException {
        List<MockInterviewQuestion> all = mockInterviewDAO.findQuestions(sessionId);
        List<MockInterviewQuestion> graded = all.stream().filter(q -> q.getAiScore() != null).toList();

        BigDecimal average = BigDecimal.ZERO;
        if (!graded.isEmpty()) {
            int sum = graded.stream().mapToInt(MockInterviewQuestion::getAiScore).sum();
            average = BigDecimal.valueOf(sum).divide(BigDecimal.valueOf(graded.size()), 2, RoundingMode.HALF_UP);
        }
        BigDecimal readiness = average; // the mock interview's own readiness contribution IS its average AI score

        mockInterviewDAO.completeSession(sessionId, average, readiness);
        sessionContexts.remove(sessionId);

        List<MockInterviewQuestion> strengths = graded.stream()
                .sorted(Comparator.comparingInt(MockInterviewQuestion::getAiScore).reversed())
                .limit(3).toList();
        List<MockInterviewQuestion> weaknesses = graded.stream()
                .sorted(Comparator.comparingInt(MockInterviewQuestion::getAiScore))
                .limit(3).toList();

        return new FinalFeedback(average, readiness, strengths, weaknesses);
    }

    /** Current Interview Readiness Score, for capturing the "before" snapshot right before a session starts (see {@link #buildReport}). */
    public double currentReadinessScore(Long userId) throws SQLException {
        return readinessScoreService.compute(userId).getOverallScore();
    }

    /**
     * Builds the Mock Interview Report: {@code finalizeSession}'s result
     * plus the concrete key mistakes (each weak answer, its score, and the
     * AI feedback explaining what it missed - drawn straight from the
     * session, no extra AI call needed), how many previously-logged
     * mistakes are still unresolved elsewhere, and the Interview Readiness
     * Score's movement/band/next-best-action after this session. Call this
     * (with {@code readinessBefore} captured via {@link #currentReadinessScore}
     * before the session started, and after any roadmap/readiness refresh
     * such as {@code CareerIntelligenceService#refreshAfterActivity} has
     * run) so {@code readinessScoreAfter} reflects this session's impact.
     */
    public MockInterviewReport buildReport(Long userId, FinalFeedback feedback, Long sessionId,
                                            double readinessBefore) throws SQLException {
        List<MockInterviewQuestion> allQuestions = mockInterviewDAO.findQuestions(sessionId);
        List<String> keyMistakes = allQuestions.stream()
                .filter(q -> q.getAiScore() != null && q.getAiScore() < FOLLOWUP_SCORE_THRESHOLD)
                .map(q -> "[" + q.getAiScore() + "/100] " + q.getQuestionText()
                        + (q.getAiFeedback() == null || q.getAiFeedback().isBlank() ? "" : " - " + q.getAiFeedback()))
                .collect(Collectors.toList());

        int unresolvedElsewhere = mistakeAnalyzerService.getUnresolvedMistakes(userId).size();

        ReadinessScore readinessAfter = readinessScoreService.compute(userId);

        return new MockInterviewReport(feedback.averageScore(), feedback.strengths(), feedback.weaknesses(),
                keyMistakes, unresolvedElsewhere, readinessBefore, readinessAfter.getOverallScore(),
                readinessAfter.getBand(), readinessAfter.getNextRecommendedAction());
    }

    public List<MockInterviewSession> getHistory(Long userId) throws SQLException {
        return mockInterviewDAO.findByUser(userId);
    }

    public List<MockInterviewQuestion> getQuestions(Long sessionId) throws SQLException {
        return mockInterviewDAO.findQuestions(sessionId);
    }

    // -----------------------------------------------------------------
    // Question sourcing
    // -----------------------------------------------------------------

    private record PreparedQuestion(String text, String modelAnswer, AIService.InterviewPhase phase) {
        PreparedQuestion(String text, String modelAnswer) {
            this(text, modelAnswer, AIService.InterviewPhase.TECHNICAL);
        }
    }

    private List<PreparedQuestion> buildQuestionPool(List<Integer> topicIds, List<String> topicNames,
                                                       List<String> resumeSkills, String roleName, int numQuestions,
                                                       AIService.PersonalizationContext personalizationContext) throws SQLException {
        List<PreparedQuestion> pool = new ArrayList<>();

        if (topicIds != null && !topicIds.isEmpty()) {
            List<Question> candidates = questionDAO.findRandomQuestionsByTopics(topicIds, Math.max(numQuestions * 4, 10));
            for (Question q : candidates) {
                if (pool.size() >= numQuestions) break;
                if (q.getQuestionType() == QuestionType.DESCRIPTIVE) {
                    pool.add(new PreparedQuestion(q.getQuestionText(), q.getCorrectAnswer()));
                }
            }
        }

        if (pool.size() < numQuestions && personalizationContext != null) {
            int needed = numQuestions - pool.size();
            List<String> personalizedQuestions = aiService.generatePersonalizedQuestions(personalizationContext, needed);
            List<String> gradingSkillOrder = buildGradingSkillOrder(personalizationContext, resumeSkills, roleName);
            for (int i = 0; i < personalizedQuestions.size() && pool.size() < numQuestions; i++) {
                String skillForGrading = gradingSkillOrder.isEmpty() ? roleName
                        : gradingSkillOrder.get(i % gradingSkillOrder.size());
                if (skillForGrading == null || skillForGrading.isBlank()) {
                    skillForGrading = "the relevant skill for this question";
                }
                pool.add(new PreparedQuestion(personalizedQuestions.get(i), aiService.blurbForSkill(skillForGrading)));
            }
        }

        if (pool.size() < numQuestions) {
            List<String> fillSkills = new ArrayList<>();
            if (resumeSkills != null && !resumeSkills.isEmpty()) {
                fillSkills.addAll(resumeSkills);
            } else if (topicNames != null && !topicNames.isEmpty()) {
                fillSkills.addAll(topicNames);
            } else if (roleName != null && !roleName.isBlank()) {
                fillSkills.add(roleName);
            }

            int needed = numQuestions - pool.size();
            for (int i = 0; i < needed && !fillSkills.isEmpty(); i++) {
                String skill = fillSkills.get(i % fillSkills.size());
                List<String> generated = aiService.generateQuestionsForSkills(List.of(skill), 1);
                if (!generated.isEmpty()) {
                    pool.add(new PreparedQuestion(generated.get(0), aiService.blurbForSkill(skill)));
                }
            }
        }

        if (pool.isEmpty()) {
            for (String q : GENERIC_FALLBACK_QUESTIONS) {
                pool.add(new PreparedQuestion(q, GENERIC_FALLBACK_MODEL_ANSWER));
            }
        }

        return pool;
    }

    /**
     * A best-effort skill order used only to pick a reasonable grading
     * target (via {@code aiService.blurbForSkill}) for each AI-generated
     * personalised question - the generator itself doesn't return which
     * skill each question came from, so this mirrors its own prioritisation
     * (skill gaps, then resume skills, then the role name as a last resort)
     * closely enough to be a fair reference answer. Package-private +
     * pure/static so it's unit-testable without a database.
     */
    static List<String> buildGradingSkillOrder(AIService.PersonalizationContext context, List<String> resumeSkills,
                                                String roleName) {
        Set<String> order = new LinkedHashSet<>();
        if (context != null) {
            order.addAll(context.skillGaps());
            order.addAll(context.previousMistakeTopics());
            order.addAll(context.resumeSkills());
            order.addAll(context.jobRequiredSkills());
        }
        if (resumeSkills != null) {
            order.addAll(resumeSkills);
        }
        if (order.isEmpty() && roleName != null && !roleName.isBlank()) {
            order.add(roleName);
        }
        return new ArrayList<>(order);
    }
}
