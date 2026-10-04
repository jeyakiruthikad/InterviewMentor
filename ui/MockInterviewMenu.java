package com.careerintelligence.ui;

import com.careerintelligence.ai.AIService;
import com.careerintelligence.dao.MockInterviewDAO;
import com.careerintelligence.dao.TopicDAO;
import com.careerintelligence.model.CareerRefreshTrigger;
import com.careerintelligence.model.LearningRoadmap;
import com.careerintelligence.model.MockInterviewQuestion;
import com.careerintelligence.model.MockInterviewSession;
import com.careerintelligence.model.MockSessionStatus;
import com.careerintelligence.model.Topic;
import com.careerintelligence.model.User;
import com.careerintelligence.service.CareerIntelligenceService;
import com.careerintelligence.service.GamificationService;
import com.careerintelligence.service.MockInterviewService;
import com.careerintelligence.service.ResumeService;

import java.sql.SQLException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Scanner;

/**
 * UI for the AI Mock Interview feature: an automatically prepared
 * interview based on the user's profile, resume, job description, weak
 * topics and previous performance, followed by a live question-answer
 * loop with dynamic AI follow-ups and a detailed final feedback report. Every
 * completed session feeds back into the Career Intelligence pipeline so
 * the roadmap and readiness score reflect it immediately.
 */
public class MockInterviewMenu {

    private final Scanner scanner;
    private final MockInterviewService mockInterviewService;
    private final ResumeService resumeService;
    private final GamificationService gamificationService;
    private final CareerIntelligenceService careerIntelligenceService;
    private final TopicDAO topicDAO = new TopicDAO();
    private final MockInterviewDAO mockInterviewDAO = new MockInterviewDAO();

    public MockInterviewMenu(Scanner scanner, MockInterviewService mockInterviewService, ResumeService resumeService,
                              GamificationService gamificationService) {
        this(scanner, mockInterviewService, resumeService, gamificationService, new CareerIntelligenceService());
    }

    public MockInterviewMenu(Scanner scanner, MockInterviewService mockInterviewService, ResumeService resumeService,
                              GamificationService gamificationService, CareerIntelligenceService careerIntelligenceService) {
        this.scanner = scanner;
        this.mockInterviewService = mockInterviewService;
        this.resumeService = resumeService;
        this.gamificationService = gamificationService;
        this.careerIntelligenceService = careerIntelligenceService;
    }

    public void show(User user) {
        boolean back = false;
        while (!back) {
            ConsoleIO.printHeader("AI MOCK INTERVIEW");
            System.out.println("1. Start New Mock Interview");
            System.out.println("2. Mock Interview History");
            System.out.println("3. Back to main menu");
            int choice = ConsoleIO.promptInt(scanner, "Choose an option", 1, 3);

            switch (choice) {
                case 1 -> startInterview(user);
                case 2 -> showHistory(user);
                case 3 -> back = true;
            }
        }
    }

    private void startInterview(User user) {
        try {
            ConsoleIO.printHeader("STARTING YOUR INTERVIEW");
            CareerIntelligenceService.MockInterviewDefaults defaults =
                    careerIntelligenceService.recommendMockInterviewDefaults(user.getUserId());

            String roleName = defaults.targetRole();
            if (roleName == null || roleName.isBlank()) {
                roleName = "Software Engineer";
            }
            String companyName = defaults.targetCompany();

            List<Integer> topicIds = new ArrayList<>(defaults.topicIds());
            List<String> topicNames = new ArrayList<>(defaults.topicNames());
            List<String> resumeSkills = defaults.resumeSkillNames();

            // The application quietly prepares the interview from the candidate's profile, resume,
            // job description, mistakes and performance history. There is no configuration form.
            int availableQuestions = 0;
            for (Integer topicId : topicIds) {
                availableQuestions += topicDAO.countQuestionsForTopic(topicId);
            }

            if (availableQuestions == 0) {
                // Recommendations can be empty for a new account, so make one quiet
                // fallback pass through active topics before giving up.
                topicIds.clear();
                topicNames.clear();
                for (Topic topic : topicDAO.findAllActive()) {
                    int count = topicDAO.countQuestionsForTopic(topic.getTopicId());
                    if (count > 0) {
                        topicIds.add(topic.getTopicId());
                        topicNames.add(topic.getTopicName());
                        availableQuestions += count;
                    }
                    if (topicIds.size() >= 3) break;
                }
            }

            if (availableQuestions == 0) {
                ConsoleIO.printError("No interview questions are available yet.");
                ConsoleIO.pause(scanner);
                return;
            }

            // 2-3 behavioral openers + personalised technical questions (follow-ups are added dynamically).
            int numQuestions = Math.max(5, Math.min(8, availableQuestions + 3));

            // Personalized Questions: build the candidate's full context (resume, JD, target role,
            // skill gaps, previous mistakes, current difficulty) so the AI-generated portion of the
            // question set is tailored to them specifically, not just a generic skill-name template.
            AIService.PersonalizationContext personalizationContext =
                    careerIntelligenceService.buildInterviewPersonalizationContext(user.getUserId());
            double readinessBefore = mockInterviewService.currentReadinessScore(user.getUserId());

            MockInterviewService.SessionBundle bundle = mockInterviewService.startSession(
                    user.getUserId(), roleName, companyName,
                    topicIds, topicNames, resumeSkills, numQuestions, personalizationContext);

            runInterview(bundle, readinessBefore);
        } catch (SQLException e) {
            ConsoleIO.printError("Database error: " + e.getMessage());
            ConsoleIO.pause(scanner);
        } catch (IllegalStateException e) {
            ConsoleIO.printError(e.getMessage());
            ConsoleIO.pause(scanner);
        }
    }

    private void runInterview(MockInterviewService.SessionBundle bundle, double readinessBefore) throws SQLException {
        MockInterviewSession session = bundle.session();
        String interviewTitle = session.getRoleName() == null ? "General Interview" : session.getRoleName();
        if (session.getCompanyName() != null && !session.getCompanyName().isBlank()) {
            interviewTitle += " - " + session.getCompanyName();
        }
        ConsoleIO.printHeader("INTERVIEW IN PROGRESS");
        System.out.println("Position: " + interviewTitle);
        System.out.println("Take a moment to think before answering. Answer the way you would in a real interview.");
        System.out.println();
        System.out.println("Interviewer: Hi, thanks for joining me today. Let's start with a few questions about you "
                + "and your experience.");

        Deque<MockInterviewService.QuestionAsked> queue = new ArrayDeque<>(bundle.questions());
        AIService.InterviewPhase currentPhase = AIService.InterviewPhase.BEHAVIORAL;
        while (!queue.isEmpty()) {
            MockInterviewService.QuestionAsked asked = queue.poll();

            if (asked.phase() != currentPhase) {
                currentPhase = asked.phase();
                System.out.println();
                System.out.println("Interviewer: Thanks for walking me through that. Now I'd like to move on to some "
                        + "technical questions based on your background.");
            }

            System.out.println();
            ConsoleIO.printDivider();
            System.out.println("Interviewer: " + asked.question().getQuestionText());
            ConsoleIO.printDivider();
            System.out.print("You: ");
            String answer = scanner.nextLine();

            MockInterviewService.AnswerResult result = mockInterviewService.submitAnswer(session, asked, answer);

            if (result.followUp() != null) {
                // The follow-up text already reacts to what the candidate actually said.
                queue.addFirst(result.followUp());
            } else if (!queue.isEmpty()) {
                System.out.println("\nInterviewer: " + MockInterviewService.bridgeLine(result.score()));
            }
        }

        System.out.println("\nInterviewer: Thank you. That concludes the interview.\n");
        MockInterviewService.FinalFeedback feedback = mockInterviewService.finalizeSession(session.getSessionId());
        awardGamification(session.getUserId());
        refreshCareerIntelligence(session.getUserId());
        showMockInterviewReport(session.getUserId(), feedback, session.getSessionId(), readinessBefore);
    }

    /** Closes the loop after a mock interview finishes: refreshes the roadmap and readiness score. Never blocks the feedback screen. */
    private void refreshCareerIntelligence(Long userId) {
        try {
            LearningRoadmap roadmap = careerIntelligenceService.refreshAfterActivity(
                    userId, CareerRefreshTrigger.MOCK_INTERVIEW_COMPLETED);
            ConsoleIO.printInfo(String.format(
                    "Your skill gaps, roadmap and readiness score have been updated (readiness now %.1f/100 - %s).",
                    roadmap.getReadinessScore() == null ? 0.0 : roadmap.getReadinessScore(), roadmap.getReadinessBand()));
        } catch (SQLException e) {
            System.err.println("[InterviewMentor] Could not refresh roadmap/readiness: " + e.getMessage());
        }
    }

    /** Awards gamification points/badges for a just-completed mock interview session. Never blocks the feedback screen. */
    private void awardGamification(Long userId) {
        try {
            int completedCount = (int) mockInterviewDAO.findByUser(userId).stream()
                    .filter(s -> s.getStatus() == MockSessionStatus.COMPLETED).count();
            GamificationService.AwardResult award = gamificationService.awardMockInterviewCompleted(userId, completedCount);
            GamificationUi.printAward(award);
        } catch (SQLException e) {
            System.err.println("[Gamification] Could not award mock interview points: " + e.getMessage());
        }
    }

    private void showMockInterviewReport(Long userId, MockInterviewService.FinalFeedback feedback, Long sessionId,
                                          double readinessBefore) {
        ConsoleIO.printHeader("MOCK INTERVIEW REPORT");
        try {
            MockInterviewService.MockInterviewReport report =
                    mockInterviewService.buildReport(userId, feedback, sessionId, readinessBefore);

            System.out.println("Overall score               : " + report.overallScore() + "/100");
            System.out.println("Readiness score (before)    : " + String.format("%.1f", report.readinessScoreBefore()) + "/100");
            System.out.println("Readiness score (after)     : " + String.format("%.1f", report.readinessScoreAfter())
                    + "/100 (" + report.readinessBand() + ")");
            System.out.printf("Readiness impact             : %s%.1f pts%n",
                    report.readinessDelta() >= 0 ? "+" : "", report.readinessDelta());

            if (!report.strengths().isEmpty()) {
                System.out.println("\nStrengths:");
                for (MockInterviewQuestion q : report.strengths()) {
                    System.out.println("  [" + q.getAiScore() + "/100] " + q.getQuestionText());
                }
            }
            if (!report.weaknesses().isEmpty()) {
                System.out.println("\nWeaknesses:");
                for (MockInterviewQuestion q : report.weaknesses()) {
                    System.out.println("  [" + q.getAiScore() + "/100] " + q.getQuestionText());
                }
            }
            if (!report.keyMistakes().isEmpty()) {
                System.out.println("\nKey mistakes this session:");
                for (String mistake : report.keyMistakes()) {
                    System.out.println("  - " + mistake);
                }
            }
            if (report.unresolvedMistakesElsewhere() > 0) {
                System.out.println("\nNote: you also have " + report.unresolvedMistakesElsewhere()
                        + " unresolved mistake(s) logged from other assessments - see Mistake Analyzer.");
            }

            System.out.println("\nRecommended next action: " + report.recommendedNextAction());
        } catch (SQLException e) {
            // The report is a value-add on top of finalizeSession, which has already persisted the
            // session's own score - never let a report-building failure hide that the session completed.
            ConsoleIO.printError("Could not build the full report (session results were still saved): " + e.getMessage());
            System.out.println("Average AI score        : " + feedback.averageScore() + "/100");
            System.out.println("Readiness contribution   : " + feedback.readinessScore() + "/100");
        }
        ConsoleIO.pause(scanner);
    }

    private void showHistory(User user) {
        ConsoleIO.printHeader("MOCK INTERVIEW HISTORY");
        try {
            List<MockInterviewSession> history = mockInterviewService.getHistory(user.getUserId());
            if (history.isEmpty()) {
                System.out.println("No mock interviews taken yet.");
            } else {
                System.out.printf("%-4s %-22s %-16s %-10s %-10s %-10s %-20s%n",
                        "ID", "Role", "Company", "Status", "Avg", "Qs", "Date");
                ConsoleIO.printDivider();
                for (MockInterviewSession s : history) {
                    System.out.printf("%-4d %-22s %-16s %-10s %-10s %-10d %-20s%n",
                            s.getSessionId(),
                            truncate(s.getRoleName() == null ? "-" : s.getRoleName(), 22),
                            truncate(s.getCompanyName() == null ? "-" : s.getCompanyName(), 16),
                            s.getStatus(),
                            s.getAverageScore() == null ? "-" : s.getAverageScore().toString(),
                            s.getTotalQuestions(),
                            s.getCreatedAt());
                }
            }
        } catch (SQLException e) {
            ConsoleIO.printError("Database error: " + e.getMessage());
        }
        ConsoleIO.pause(scanner);
    }

    private String truncate(String text, int maxLen) {
        if (text == null) return "";
        return text.length() <= maxLen ? text : text.substring(0, maxLen - 3) + "...";
    }
}
