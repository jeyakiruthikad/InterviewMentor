package com.careerintelligence.ui;

import com.careerintelligence.dao.TopicDAO;
import com.careerintelligence.dao.TopicPerformanceDAO;
import com.careerintelligence.model.*;
import com.careerintelligence.service.AssessmentService;
import com.careerintelligence.service.AssessmentTimer;
import com.careerintelligence.service.CareerIntelligenceService;
import com.careerintelligence.service.GamificationService;
import com.careerintelligence.util.TimedInputReader;

import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Handles: automatically personalised topic/question selection, running a timed
 * multi-topic assessment (auto-submitting when time runs out) in STANDARD, ADAPTIVE or
 * RETRY mode, scoring, and showing assessment history. This is the heart
 * of the assessment engine's UI. Topic selection is personalised via
 * {@link CareerIntelligenceService#recommendFocusTopics}, and every
 * finalized assessment (including resume-based and mistake-retry ones,
 * which both run through {@link #runAssessment}) feeds back into the
 * Career Intelligence pipeline via
 * {@link CareerIntelligenceService#refreshAfterActivity}, so skill gaps,
 * the roadmap and the readiness score are never stale.
 */
public class AssessmentMenu {

    private final Scanner scanner;
    private final AssessmentService assessmentService;
    private final GamificationService gamificationService;
    private final CareerIntelligenceService careerIntelligenceService;
    private final TopicDAO topicDAO = new TopicDAO();
    private final TopicPerformanceDAO topicPerformanceDAO = new TopicPerformanceDAO();
    private final com.careerintelligence.dao.AssessmentDAO assessmentDAO = new com.careerintelligence.dao.AssessmentDAO();

    public AssessmentMenu(Scanner scanner, AssessmentService assessmentService, GamificationService gamificationService) {
        this(scanner, assessmentService, gamificationService, new CareerIntelligenceService());
    }

    public AssessmentMenu(Scanner scanner, AssessmentService assessmentService, GamificationService gamificationService,
                           CareerIntelligenceService careerIntelligenceService) {
        this.scanner = scanner;
        this.assessmentService = assessmentService;
        this.gamificationService = gamificationService;
        this.careerIntelligenceService = careerIntelligenceService;
    }

    public void startNewAssessment(User user) {
        try {
            List<Topic> topics = topicDAO.findAllActive();
            if (topics.isEmpty()) {
                ConsoleIO.printError("No assessment content is available yet.");
                ConsoleIO.pause(scanner);
                return;
            }

            ConsoleIO.printHeader("START ASSESSMENT");
            CareerIntelligenceService.RecommendedFocus recommended =
                    careerIntelligenceService.recommendFocusTopics(user.getUserId());

            // The application chooses the assessment setup automatically so the user can
            // get straight into practice instead of configuring a test like an administrator.
            List<Integer> topicIds = new ArrayList<>();
            List<String> topicNames = new ArrayList<>();
            if (!recommended.isEmpty()) {
                for (Integer topicId : recommended.topicIds()) {
                    for (Topic t : topics) {
                        if (t.getTopicId().equals(topicId) && topicDAO.countQuestionsForTopic(t.getTopicId()) > 0) {
                            topicIds.add(t.getTopicId());
                            topicNames.add(t.getTopicName());
                            break;
                        }
                    }
                }
            }
            if (topicIds.isEmpty()) {
                for (Topic t : topics) {
                    if (topicDAO.countQuestionsForTopic(t.getTopicId()) > 0) {
                        topicIds.add(t.getTopicId());
                        topicNames.add(t.getTopicName());
                    }
                    if (topicIds.size() >= 3) break;
                }
            }

            int totalAvailable = 0;
            for (Integer topicId : topicIds) {
                totalAvailable += topicDAO.countQuestionsForTopic(topicId);
            }
            if (totalAvailable == 0) {
                ConsoleIO.printError("No assessment questions are available yet.");
                ConsoleIO.pause(scanner);
                return;
            }

            int questionCount = Math.min(10, totalAvailable);
            // One minute per question is a simple interview-style pace; no setup prompt is needed.
            int durationMinutes = Math.max(1, questionCount);
            String title = "Adaptive Assessment - " + String.join(", ", topicNames);

            System.out.println("Personalized assessment ready.");
            System.out.println("Topics: " + String.join(", ", topicNames));
            System.out.println("Questions: " + questionCount + " | Time: " + durationMinutes + " minutes");

            Assessment assessment = assessmentService.createAdaptiveAssessment(
                    user.getUserId(), topicIds, topicNames, questionCount, durationMinutes, title);
            runAssessment(assessment);

        } catch (SQLException e) {
            ConsoleIO.printError("Database error: " + e.getMessage());
            ConsoleIO.pause(scanner);
        } catch (IllegalStateException e) {
            ConsoleIO.printError(e.getMessage());
            ConsoleIO.pause(scanner);
        }
    }

    /**
     * Runs any already-created assessment (STANDARD, ADAPTIVE or RETRY) to
     * completion: the timed question loop, grading, and the result screen.
     * Public so other menus (e.g. the Mistake Analyzer's retry flow) can
     * reuse the exact same assessment-taking experience.
     */
    public void runAssessment(Assessment assessment) throws SQLException {
        List<AssessmentQuestion> questions = assessmentService.getQuestions(assessment.getAssessmentId());

        ConsoleIO.printHeader("ASSESSMENT STARTED: " + assessment.getTitle() + " [" + assessment.getMode() + "]");
        System.out.println("Questions: " + questions.size() + " | Time: " + assessment.getDurationMinutes() + " minutes");
        System.out.println("The interview starts now.");

        AtomicBoolean timeExpiredFlag = new AtomicBoolean(false);
        AssessmentTimer timer = new AssessmentTimer(assessment.getDurationMinutes() * 60L, () -> {
            timeExpiredFlag.set(true);
            System.out.println("\n[TIME'S UP] The assessment time has ended - auto-submitting...");
        });
        timer.start();

        boolean manuallySubmitted = false;
        int questionNumber = 0;

        for (AssessmentQuestion aq : questions) {
            questionNumber++;
            if (timer.hasExpired()) {
                break;
            }

            Question question = aq.getQuestion();
            System.out.println();
            ConsoleIO.printDivider();
            System.out.printf("Time remaining: %s | Question %d of %d%n",
                    timer.formattedRemaining(), questionNumber, questions.size());
            ConsoleIO.printDivider();
            System.out.println(question.getQuestionText());

            String rawAnswer;
            String selectedOptionLabel = null;

            switch (question.getQuestionType()) {
                case MCQ -> {
                    for (QuestionOption option : question.getOptions()) {
                        System.out.println("  " + option);
                    }
                    System.out.print("Your answer (A/B/C/D): ");
                    rawAnswer = TimedInputReader.readLineWithTimeout(scanner, timer.remainingSeconds(), TimeUnit.SECONDS);
                    selectedOptionLabel = rawAnswer;
                }
                case TRUE_FALSE -> {
                    System.out.print("Your answer (TRUE/FALSE): ");
                    rawAnswer = TimedInputReader.readLineWithTimeout(scanner, timer.remainingSeconds(), TimeUnit.SECONDS);
                }
                default -> {
                    System.out.print("Your answer: ");
                    rawAnswer = TimedInputReader.readLineWithTimeout(scanner, timer.remainingSeconds(), TimeUnit.SECONDS);
                }
            }

            if (rawAnswer == null) {
                System.out.println("\n[TIME'S UP] No time left to answer - moving to submission.");
                timeExpiredFlag.set(true);
                break;
            }
            if (rawAnswer.trim().equalsIgnoreCase("SUBMIT")) {
                manuallySubmitted = true;
                break;
            }

            AssessmentService.AnswerOutcome outcome =
                    assessmentService.submitAnswer(assessment.getAssessmentId(), question, selectedOptionLabel, rawAnswer);
            System.out.println(outcome.message());
        }

        timer.stop();
        boolean autoSubmitted = timeExpiredFlag.get() && !manuallySubmitted;
        Assessment finalized = assessmentService.finalizeAssessment(assessment, questions, autoSubmitted);
        awardGamification(finalized);
        showResult(finalized);
        refreshCareerIntelligence(finalized);
    }

    /**
     * Closes the loop after ANY assessment finishes (STANDARD, ADAPTIVE,
     * RESUME-based or RETRY - they all run through this method), so skill
     * gaps, the learning roadmap and the readiness score always reflect
     * the user's latest performance. Never blocks the result screen.
     */
    private void refreshCareerIntelligence(Assessment assessment) {
        try {
            CareerRefreshTrigger trigger = assessment.getMode() == AssessmentMode.RETRY
                    ? CareerRefreshTrigger.MISTAKE_RETRY_COMPLETED
                    : CareerRefreshTrigger.ASSESSMENT_COMPLETED;
            LearningRoadmap roadmap = careerIntelligenceService.refreshAfterActivity(assessment.getUserId(), trigger);
            ConsoleIO.printInfo(String.format(
                    "Your skill gaps, roadmap and readiness score have been updated (readiness now %.1f/100 - %s).",
                    roadmap.getReadinessScore() == null ? 0.0 : roadmap.getReadinessScore(), roadmap.getReadinessBand()));
        } catch (SQLException e) {
            System.err.println("[InterviewMentor] Could not refresh roadmap/readiness: " + e.getMessage());
        }
    }

    /** Awards gamification points/badges for a just-finalized assessment (any mode). Never blocks the result screen. */
    private void awardGamification(Assessment assessment) {
        try {
            double scorePercent = 0.0;
            if (assessment.getMaxScore() != null && assessment.getMaxScore().signum() > 0) {
                scorePercent = assessment.getTotalScore().doubleValue() / assessment.getMaxScore().doubleValue() * 100.0;
            }
            int completedCount = assessmentDAO.countCompletedByUser(assessment.getUserId());
            GamificationService.AwardResult award =
                    gamificationService.awardAssessmentCompleted(assessment.getUserId(), scorePercent, completedCount);
            GamificationUi.printAward(award);

            if (assessment.getMode() == AssessmentMode.RETRY && assessment.getCorrectCount() > 0) {
                int totalResolved = new com.careerintelligence.dao.MistakeLogDAO().countsByUser(assessment.getUserId()).resolved();
                GamificationService.AwardResult mistakeAward = gamificationService.awardMistakesResolved(
                        assessment.getUserId(), assessment.getCorrectCount(), totalResolved);
                GamificationUi.printAward(mistakeAward);
            }
        } catch (SQLException e) {
            System.err.println("[Gamification] Could not award assessment points: " + e.getMessage());
        }
    }

    private void showResult(Assessment assessment) {
        ConsoleIO.printHeader("ASSESSMENT RESULT");
        System.out.println("Title           : " + assessment.getTitle());
        System.out.println("Mode            : " + assessment.getMode());
        System.out.println("Status          : " + assessment.getStatus());
        System.out.println("Total questions : " + assessment.getTotalQuestions());
        System.out.println("Correct         : " + assessment.getCorrectCount());
        System.out.println("Wrong           : " + assessment.getWrongCount());
        System.out.println("Unanswered      : " + assessment.getUnansweredCount());
        System.out.println("Score           : " + assessment.getTotalScore() + " / " + assessment.getMaxScore());
        if (assessment.getMaxScore() != null && assessment.getMaxScore().signum() > 0) {
            double percentage = assessment.getTotalScore().doubleValue() / assessment.getMaxScore().doubleValue() * 100.0;
            System.out.printf("Percentage      : %.2f%%%n", percentage);
        }

        try {
            List<UserAnswer> answers = assessmentService.getAnswers(assessment.getAssessmentId());
            List<UserAnswer> descriptiveAnswers = answers.stream()
                    .filter(a -> a.getAiScore() != null)
                    .toList();
            if (!descriptiveAnswers.isEmpty()) {
                ConsoleIO.printDivider();
                System.out.println("AI FEEDBACK (descriptive answers):");
                for (UserAnswer a : descriptiveAnswers) {
                    System.out.println("  Q#" + a.getQuestionId() + " (AI score " + a.getAiScore() + "/100): "
                            + a.getAiFeedback());
                }
            }
        } catch (SQLException e) {
            ConsoleIO.printError("Could not load AI feedback: " + e.getMessage());
        }

        ConsoleIO.pause(scanner);
    }

    public void showHistory(User user) {
        try {
            List<Assessment> history = assessmentService.getHistory(user.getUserId());
            ConsoleIO.printHeader("ASSESSMENT HISTORY");
            if (history.isEmpty()) {
                System.out.println("No assessments taken yet. Start one from the main menu!");
            } else {
                System.out.printf("%-4s %-28s %-8s %-10s %-14s %-10s %-20s%n",
                        "ID", "Title", "Mode", "Score", "Status", "Correct", "Date");
                ConsoleIO.printDivider();
                for (Assessment a : history) {
                    System.out.printf("%-4d %-28s %-8s %-10s %-14s %-10s %-20s%n",
                            a.getAssessmentId(),
                            truncate(a.getTitle(), 28),
                            a.getMode(),
                            a.getTotalScore() + "/" + a.getMaxScore(),
                            a.getStatus(),
                            a.getCorrectCount() + "/" + a.getTotalQuestions(),
                            a.getCreatedAt());
                }
            }
            ConsoleIO.pause(scanner);
        } catch (SQLException e) {
            ConsoleIO.printError("Database error while loading history: " + e.getMessage());
            ConsoleIO.pause(scanner);
        }
    }

    private String truncate(String text, int maxLen) {
        if (text == null) return "";
        return text.length() <= maxLen ? text : text.substring(0, maxLen - 3) + "...";
    }
}
