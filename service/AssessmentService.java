package com.careerintelligence.service;

import com.careerintelligence.ai.AIServiceImpl;
import com.careerintelligence.dao.AssessmentDAO;
import com.careerintelligence.dao.MistakeLogDAO;
import com.careerintelligence.dao.QuestionDAO;
import com.careerintelligence.dao.TopicPerformanceDAO;
import com.careerintelligence.dao.UserAnswerDAO;
import com.careerintelligence.model.*;
import com.careerintelligence.util.EnvLoader;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Core business logic for the assessment engine:
 *  - building a new multi-topic assessment, either with uniformly random
 *    questions (STANDARD) or with a difficulty mix driven by the user's
 *    historical per-topic accuracy (ADAPTIVE), or from a fixed list of
 *    previously-incorrect questions (RETRY)
 *  - grading objective answers (MCQ, True/False) automatically
 *  - grading descriptive answers via AI semantic evaluation (meaning-based,
 *    not exact wording) with stored feedback covering correct/missing/
 *    incorrect points and an improvement suggestion
 *  - finalising the attempt (manual submit or timer auto-submit), computing
 *    the score, and updating the adaptive-difficulty / mistake-analyzer
 *    tracking tables used by the Performance Dashboard
 *  - retrieving assessment history
 */
public class AssessmentService {

    private final AssessmentDAO assessmentDAO = new AssessmentDAO();
    private final QuestionDAO questionDAO = new QuestionDAO();
    private final UserAnswerDAO userAnswerDAO = new UserAnswerDAO();
    private final TopicPerformanceDAO topicPerformanceDAO = new TopicPerformanceDAO();
    private final MistakeLogDAO mistakeLogDAO = new MistakeLogDAO();
    private final AIServiceImpl aiService = new AIServiceImpl();

    /** Minimum AI semantic score (0-100) for a descriptive answer to count as "correct". Configurable via .env. */
    private final int aiPassThreshold = EnvLoader.getInt("AI_PASS_THRESHOLD", 60);

    /** Simple holder returned after grading a single answer, used by the console UI for instant feedback. */
    public record AnswerOutcome(boolean graded, boolean correct, BigDecimal marksObtained, String message) {
    }

    /**
     * Creates a brand-new assessment: persists the assessments row, its
     * topic links, and a fixed randomised set of questions (so re-loading
     * the assessment always shows the same questions, even after a
     * disconnect).
     */
    public Assessment createAssessment(Long userId, List<Integer> topicIds, List<String> topicNames,
                                        int totalQuestions, int durationMinutes, String title) throws SQLException {
        List<Question> questions = questionDAO.findRandomQuestionsByTopics(topicIds, totalQuestions);
        if (questions.isEmpty()) {
            throw new IllegalStateException("No active questions found for the selected topics.");
        }

        Assessment assessment = new Assessment();
        assessment.setUserId(userId);
        assessment.setTitle(title);
        assessment.setTotalQuestions(questions.size());
        assessment.setDurationMinutes(durationMinutes);
        assessment.setStatus(AssessmentStatus.IN_PROGRESS);
        assessment.setStartTime(LocalDateTime.now());
        assessment.setTopicIds(topicIds);
        assessment.setTopicNames(topicNames);

        assessmentDAO.create(assessment);
        assessmentDAO.linkTopics(assessment.getAssessmentId(), topicIds);

        List<Long> questionIds = new ArrayList<>();
        for (Question q : questions) {
            questionIds.add(q.getQuestionId());
        }
        assessmentDAO.addQuestions(assessment.getAssessmentId(), questionIds);

        return assessment;
    }

    /**
     * Creates an ADAPTIVE assessment: instead of pure uniform randomness,
     * each topic's questions are drawn with a difficulty mix centred on
     * that user's current recommended difficulty for the topic (see
     * {@link TopicPerformanceDAO}), which is derived from their running
     * accuracy on that topic across past assessments. Topics the user has
     * not attempted enough yet default to EASY.
     */
    public Assessment createAdaptiveAssessment(Long userId, List<Integer> topicIds, List<String> topicNames,
                                                int totalQuestions, int durationMinutes, String title) throws SQLException {
        Map<Integer, DifficultyLevel> topicDifficulties = new HashMap<>();
        for (Integer topicId : topicIds) {
            DifficultyLevel difficulty = topicPerformanceDAO.find(userId, topicId)
                    .map(TopicPerformance::getCurrentDifficulty)
                    .orElse(DifficultyLevel.EASY);
            topicDifficulties.put(topicId, difficulty);
        }

        List<Question> questions = questionDAO.findAdaptiveQuestionsByTopics(topicDifficulties, totalQuestions);
        if (questions.isEmpty()) {
            throw new IllegalStateException("No active questions found for the selected topics.");
        }

        Assessment assessment = new Assessment();
        assessment.setUserId(userId);
        assessment.setTitle(title);
        assessment.setTotalQuestions(questions.size());
        assessment.setDurationMinutes(durationMinutes);
        assessment.setStatus(AssessmentStatus.IN_PROGRESS);
        assessment.setMode(AssessmentMode.ADAPTIVE);
        assessment.setStartTime(LocalDateTime.now());
        assessment.setTopicIds(topicIds);
        assessment.setTopicNames(topicNames);

        assessmentDAO.create(assessment);
        assessmentDAO.linkTopics(assessment.getAssessmentId(), topicIds);

        List<Long> questionIds = new ArrayList<>();
        for (Question q : questions) {
            questionIds.add(q.getQuestionId());
        }
        assessmentDAO.addQuestions(assessment.getAssessmentId(), questionIds);

        return assessment;
    }

    /**
     * Creates a RESUME-based assessment (current implementation): questions are drawn
     * uniformly at random from the topics that the user's AI-extracted
     * resume skills matched onto the existing question bank (see
     * service.ResumeService). Reuses the exact same question-selection,
     * grading and result-tracking pipeline as {@link #createAssessment} -
     * only the mode and title differ - so it plugs straight into the
     * existing AssessmentMenu#runAssessment engine, dashboard and mistake
     * tracking without any special-casing there.
     */
    public Assessment createResumeAssessment(Long userId, List<Integer> topicIds, List<String> topicNames,
                                              int totalQuestions, int durationMinutes, String title) throws SQLException {
        List<Question> questions = questionDAO.findRandomQuestionsByTopics(topicIds, totalQuestions);
        if (questions.isEmpty()) {
            throw new IllegalStateException("No active questions found for the topics matched to your resume skills.");
        }

        Assessment assessment = new Assessment();
        assessment.setUserId(userId);
        assessment.setTitle(title);
        assessment.setTotalQuestions(questions.size());
        assessment.setDurationMinutes(durationMinutes);
        assessment.setStatus(AssessmentStatus.IN_PROGRESS);
        assessment.setMode(AssessmentMode.RESUME);
        assessment.setStartTime(LocalDateTime.now());
        assessment.setTopicIds(topicIds);
        assessment.setTopicNames(topicNames);

        assessmentDAO.create(assessment);
        assessmentDAO.linkTopics(assessment.getAssessmentId(), topicIds);

        List<Long> questionIds = new ArrayList<>();
        for (Question q : questions) {
            questionIds.add(q.getQuestionId());
        }
        assessmentDAO.addQuestions(assessment.getAssessmentId(), questionIds);

        return assessment;
    }

    /**
     * Creates a RETRY assessment made up of an exact set of previously
     * incorrect/unanswered questions (from the mistake log), so the user
     * can immediately try them again.
     */
    public Assessment createRetryAssessment(Long userId, List<Long> questionIds, int durationMinutes, String title) throws SQLException {
        if (questionIds == null || questionIds.isEmpty()) {
            throw new IllegalStateException("No mistakes available to retry.");
        }
        List<Question> questions = questionDAO.findByIds(questionIds);
        if (questions.isEmpty()) {
            throw new IllegalStateException("The questions to retry could not be loaded (they may have been removed).");
        }

        List<Integer> topicIds = new ArrayList<>();
        List<String> topicNames = new ArrayList<>();
        for (Question q : questions) {
            if (!topicIds.contains(q.getTopicId())) {
                topicIds.add(q.getTopicId());
                topicNames.add(q.getTopicName());
            }
        }

        Assessment assessment = new Assessment();
        assessment.setUserId(userId);
        assessment.setTitle(title);
        assessment.setTotalQuestions(questions.size());
        assessment.setDurationMinutes(durationMinutes);
        assessment.setStatus(AssessmentStatus.IN_PROGRESS);
        assessment.setMode(AssessmentMode.RETRY);
        assessment.setStartTime(LocalDateTime.now());
        assessment.setTopicIds(topicIds);
        assessment.setTopicNames(topicNames);

        assessmentDAO.create(assessment);
        assessmentDAO.linkTopics(assessment.getAssessmentId(), topicIds);
        List<Long> orderedIds = new ArrayList<>();
        for (Question q : questions) {
            orderedIds.add(q.getQuestionId());
        }
        assessmentDAO.addQuestions(assessment.getAssessmentId(), orderedIds);

        return assessment;
    }

    public List<AssessmentQuestion> getQuestions(Long assessmentId) throws SQLException {
        return assessmentDAO.getAssessmentQuestions(assessmentId);
    }

    /**
     * Grades and stores a single answer.
     *
     * @param selectedOptionLabel for MCQ questions, the letter chosen (A/B/C/D); ignored otherwise
     * @param freeTextAnswer      for TRUE_FALSE ("TRUE"/"FALSE") or DESCRIPTIVE (free text) questions
     */
    public AnswerOutcome submitAnswer(Long assessmentId, Question question, String selectedOptionLabel, String freeTextAnswer) throws SQLException {
        UserAnswer answer = new UserAnswer();
        answer.setAssessmentId(assessmentId);
        answer.setQuestionId(question.getQuestionId());

        switch (question.getQuestionType()) {
            case MCQ -> {
                Optional<QuestionOption> chosen = question.getOptions().stream()
                        .filter(o -> o.getOptionLabel().equalsIgnoreCase(selectedOptionLabel))
                        .findFirst();
                if (chosen.isEmpty()) {
                    // Unanswered / invalid choice -> store as wrong, 0 marks
                    answer.setCorrect(false);
                    answer.setMarksObtained(BigDecimal.ZERO);
                    userAnswerDAO.saveOrUpdateAnswer(answer);
                    return new AnswerOutcome(true, false, BigDecimal.ZERO, "No valid option selected - marked incorrect.");
                }
                QuestionOption option = chosen.get();
                boolean correct = option.isCorrect();
                answer.setSelectedOptionId(option.getOptionId());
                answer.setCorrect(correct);
                answer.setMarksObtained(correct ? BigDecimal.valueOf(question.getMarks()) : BigDecimal.ZERO);
                userAnswerDAO.saveOrUpdateAnswer(answer);
                return new AnswerOutcome(true, correct,
                        answer.getMarksObtained(),
                        correct ? "Correct!" : "Incorrect. Correct answer: " + question.getCorrectAnswer());
            }
            case TRUE_FALSE -> {
                String normalizedAnswer = normalizeBoolean(freeTextAnswer);
                String normalizedCorrect = normalizeBoolean(question.getCorrectAnswer());
                boolean correct = normalizedAnswer != null && normalizedAnswer.equals(normalizedCorrect);
                answer.setAnswerText(normalizedAnswer);
                answer.setCorrect(correct);
                answer.setMarksObtained(correct ? BigDecimal.valueOf(question.getMarks()) : BigDecimal.ZERO);
                userAnswerDAO.saveOrUpdateAnswer(answer);
                return new AnswerOutcome(true, correct,
                        answer.getMarksObtained(),
                        correct ? "Correct!" : "Incorrect. Correct answer: " + question.getCorrectAnswer());
            }
            case DESCRIPTIVE -> {
                // AI semantic evaluation: scores by meaning/completeness/relevance against
                // the model answer rather than exact wording (see ai.AIServiceImpl).
                AIServiceImpl.Evaluation evaluation = aiService
                        .evaluate(question.getQuestionText(), question.getCorrectAnswer(), freeTextAnswer);
                boolean correct = evaluation.score() >= aiPassThreshold;
                BigDecimal marks = BigDecimal.valueOf(question.getMarks())
                        .multiply(BigDecimal.valueOf(evaluation.score()))
                        .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);

                answer.setAnswerText(freeTextAnswer);
                answer.setCorrect(correct);
                answer.setMarksObtained(marks);
                answer.setAiScore(evaluation.score());
                answer.setAiFeedback(evaluation.feedback());
                userAnswerDAO.saveOrUpdateAnswer(answer);

                String message = (correct ? "Accepted (AI-evaluated). " : "Needs improvement (AI-evaluated). ")
                        + evaluation.feedback();
                return new AnswerOutcome(true, correct, marks, message);
            }
            default -> throw new IllegalStateException("Unknown question type: " + question.getQuestionType());
        }
    }

    /**
     * Finalises the assessment (manual submit or timer expiry): aggregates
     * the stored answers into totals and persists the final status/score.
     */
    public Assessment finalizeAssessment(Assessment assessment, List<AssessmentQuestion> assessmentQuestions,
                                          boolean autoSubmitted) throws SQLException {
        List<UserAnswer> answers = userAnswerDAO.findByAssessment(assessment.getAssessmentId());

        BigDecimal totalScore = BigDecimal.ZERO;
        BigDecimal maxScore = BigDecimal.ZERO;
        int correct = 0;
        int wrong = 0;
        int unanswered = 0;

        for (AssessmentQuestion aq : assessmentQuestions) {
            Question q = aq.getQuestion();
            maxScore = maxScore.add(BigDecimal.valueOf(q.getMarks()));

            Optional<UserAnswer> maybeAnswer = answers.stream()
                    .filter(a -> a.getQuestionId().equals(q.getQuestionId()))
                    .findFirst();

            Integer topicId = q.getTopicId();

            if (maybeAnswer.isEmpty()) {
                unanswered++;
                // An unanswered question is treated as a miss for adaptive-difficulty
                // and mistake-analyzer purposes, so it surfaces as a weak spot too.
                recordTopicAndMistake(assessment.getUserId(), q.getQuestionId(), topicId, false);
                continue;
            }
            UserAnswer answer = maybeAnswer.get();
            totalScore = totalScore.add(answer.getMarksObtained() == null ? BigDecimal.ZERO : answer.getMarksObtained());
            if (Boolean.TRUE.equals(answer.getCorrect())) {
                correct++;
                recordTopicAndMistake(assessment.getUserId(), q.getQuestionId(), topicId, true);
            } else if (Boolean.FALSE.equals(answer.getCorrect())) {
                wrong++;
                recordTopicAndMistake(assessment.getUserId(), q.getQuestionId(), topicId, false);
            } else {
                // Should not normally happen any more (all question types are graded at
                // submission time), but left as a safety net - not counted either way.
            }
        }

        assessment.setTotalScore(totalScore);
        assessment.setMaxScore(maxScore);
        assessment.setCorrectCount(correct);
        assessment.setWrongCount(wrong);
        assessment.setUnansweredCount(unanswered);
        assessment.setEndTime(LocalDateTime.now());
        assessment.setStatus(autoSubmitted ? AssessmentStatus.AUTO_SUBMITTED : AssessmentStatus.COMPLETED);

        assessmentDAO.completeAssessment(assessment);
        return assessment;
    }

    /** Updates the adaptive-difficulty tracking table and the mistake log for one graded question. */
    private void recordTopicAndMistake(Long userId, Long questionId, Integer topicId, boolean correct) throws SQLException {
        if (topicId == null) {
            return;
        }
        topicPerformanceDAO.recordResult(userId, topicId, correct);
        if (correct) {
            mistakeLogDAO.recordCorrect(userId, questionId);
        } else {
            mistakeLogDAO.recordWrong(userId, questionId, topicId);
        }
    }

    public List<Assessment> getHistory(Long userId) throws SQLException {
        return assessmentDAO.findHistoryByUser(userId);
    }

    /** Returns the graded answers (including AI score/feedback for descriptive ones) for a completed assessment. */
    public List<UserAnswer> getAnswers(Long assessmentId) throws SQLException {
        return userAnswerDAO.findByAssessment(assessmentId);
    }

    public Optional<Assessment> getAssessment(Long assessmentId) throws SQLException {
        return assessmentDAO.findById(assessmentId);
    }

    private String normalizeBoolean(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim().toUpperCase();
        if (trimmed.equals("T") || trimmed.equals("TRUE")) {
            return "TRUE";
        }
        if (trimmed.equals("F") || trimmed.equals("FALSE")) {
            return "FALSE";
        }
        return null;
    }
}
