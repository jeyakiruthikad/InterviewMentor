package com.careerintelligence.service;

import com.careerintelligence.ai.AIService;
import com.careerintelligence.ai.AIServiceImpl;
import com.careerintelligence.dao.*;
import com.careerintelligence.model.*;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Computes the Interview Readiness Score: a single 0-100 number, blended
 * from five raw signals already tracked elsewhere in the app -
 *
 *  1. Assessment performance - average score % across completed assessments
 *     (dao.AssessmentDAO#getOverallStats).
 *  2. Topic performance - average per-topic accuracy across attempted
 *     topics (dao.TopicPerformanceDAO).
 *  3. Mistakes - the resolved/unresolved ratio in the mistake log
 *     (dao.MistakeLogDAO); more unresolved mistakes lowers this component.
 *  4. Resume skills - coverage of the user's target-role required skills
 *     by their AI-extracted resume skills (service.ResumeService).
 *  5. Mock interview - average AI score across the user's recent completed
 *     mock interview sessions (dao.MockInterviewDAO).
 *
 * - and, on top of that, Explainable Readiness: the same five raw signals
 * re-blended into five human-facing dimensions (Technical, Problem
 * Solving, Communication, Behavioral, Role Alignment - see
 * {@link #computeDimensions}), which is what actually drives the overall
 * score and band now, plus the user's single biggest strength, biggest
 * risk, and one concrete Next Best Action - so the score explains *what*
 * is and isn't ready, not just *how* ready.
 *
 * The Behavioral dimension in particular is driven by real behavioral
 * interview performance, not just app-usage proxies: it scans the user's
 * own AI Mock Interview answers for behavioral/situational questions
 * ("tell me about a time...") and blends their STAR (Situation/Task/
 * Action/Result) completeness with their AI-graded score (see
 * {@link #collectBehavioralSignals}). Mistake-resolution discipline and
 * practice streak only step in as a clearly-labelled placeholder proxy
 * before the user has answered a real behavioral question.
 *
 * A raw component/dimension is only included in its weighted average once
 * the user has actually produced data for it (e.g. the mock-interview
 * signal is skipped for a user who has never done one), with the
 * remaining weights renormalised to fill the gap - so a brand-new feature
 * the user hasn't tried yet doesn't unfairly drag the score to zero.
 * Every computed score is persisted to `readiness_score_history` so the
 * UI can show a trend over time, not just the latest number.
 */
public class ReadinessScoreService {

    private static final int WEIGHT_ASSESSMENT = 30;
    private static final int WEIGHT_TOPIC = 20;
    private static final int WEIGHT_MISTAKE = 15;
    private static final int WEIGHT_RESUME = 15;
    private static final int WEIGHT_MOCK = 20;

    // Explainable Readiness dimension weights (used for the overall score/band now).
    private static final int WEIGHT_TECHNICAL = 30;
    private static final int WEIGHT_PROBLEM_SOLVING = 20;
    private static final int WEIGHT_COMMUNICATION = 20;
    private static final int WEIGHT_BEHAVIORAL = 15;
    private static final int WEIGHT_ROLE_ALIGNMENT = 15;

    /** Topic taxonomy categories (see database/seed_data.sql) that represent algorithmic/analytical problem solving rather than plain technical recall. */
    private static final Set<String> PROBLEM_SOLVING_CATEGORIES = Set.of("CS Fundamentals");

    /** How many of the user's most recent answered mock-interview questions to scan for behavioral/STAR signal. */
    private static final int BEHAVIORAL_ANSWER_LOOKBACK = 30;
    /** Matches the "STAR: 75%]" fragment {@code AIServiceImpl} embeds in the AI feedback of a graded behavioral answer. */
    private static final Pattern STAR_SCORE_PATTERN = Pattern.compile("STAR:\\s*(\\d+)%");

    private final AssessmentDAO assessmentDAO = new AssessmentDAO();
    private final TopicDAO topicDAO = new TopicDAO();
    private final TopicPerformanceDAO topicPerformanceDAO = new TopicPerformanceDAO();
    private final MistakeLogDAO mistakeLogDAO = new MistakeLogDAO();
    private final ResumeSkillDAO resumeSkillDAO = new ResumeSkillDAO();
    private final RoleSkillRequirementDAO roleSkillRequirementDAO = new RoleSkillRequirementDAO();
    private final UserProfileDAO userProfileDAO = new UserProfileDAO();
    private final MockInterviewDAO mockInterviewDAO = new MockInterviewDAO();
    private final ReadinessScoreDAO readinessScoreDAO = new ReadinessScoreDAO();
    private final UserAnswerDAO userAnswerDAO = new UserAnswerDAO();
    private final GamificationDAO gamificationDAO = new GamificationDAO();
    private final JobMatchResultDAO jobMatchResultDAO = new JobMatchResultDAO();
    private final ResumeService resumeService = new ResumeService();
    /** Pure/stateless here - only used for {@link AIService#isBehavioralQuestion}, never calls the live LLM. */
    private final AIService aiService = new AIServiceImpl();

    public ReadinessScore compute(Long userId) throws SQLException {
        ReadinessScore result = new ReadinessScore();

        // 1. Assessment performance
        OverallStats stats = assessmentDAO.getOverallStats(userId);
        if (stats.getTotalAssessments() > 0) {
            result.addComponent("assessment", new ReadinessScore.Component(
                    "Assessment performance", stats.getAverageScorePercent(), WEIGHT_ASSESSMENT,
                    stats.getTotalAssessments() + " assessment(s) completed"));
        }

        // 2. Topic performance (also split by taxonomy category for the Technical/Problem Solving dimensions below)
        List<TopicPerformance> topicPerf = topicPerformanceDAO.findAllByUser(userId).stream()
                .filter(tp -> tp.getAttemptsCount() > 0)
                .toList();
        if (!topicPerf.isEmpty()) {
            double avgAccuracy = topicPerf.stream()
                    .mapToDouble(tp -> tp.getAccuracyPercent().doubleValue())
                    .average().orElse(0.0);
            result.addComponent("topic", new ReadinessScore.Component(
                    "Topic performance", avgAccuracy, WEIGHT_TOPIC,
                    topicPerf.size() + " topic(s) attempted"));
        }

        // 3. Mistakes (resolved ratio - fewer unresolved mistakes is better)
        MistakeLogDAO.MistakeCounts mistakeCounts = mistakeLogDAO.countsByUser(userId);
        if (mistakeCounts.total() > 0) {
            double resolvedRatio = mistakeCounts.resolved() * 100.0 / mistakeCounts.total();
            result.addComponent("mistake", new ReadinessScore.Component(
                    "Mistake resolution", resolvedRatio, WEIGHT_MISTAKE,
                    mistakeCounts.resolved() + "/" + mistakeCounts.total() + " logged mistakes resolved"));
        }

        // 4. Resume skill coverage vs target role
        List<ResumeSkill> resumeSkills = resumeSkillDAO.findByUser(userId);
        String targetRole = null;
        if (!resumeSkills.isEmpty()) {
            Optional<UserProfile> profile = userProfileDAO.findByUserId(userId);
            targetRole = profile.map(UserProfile::getTargetRole).orElse(null);
            List<String> required = (targetRole == null || targetRole.isBlank())
                    ? List.of() : roleSkillRequirementDAO.findSkillsForRole(targetRole);

            if (!required.isEmpty()) {
                Set<String> extracted = resumeSkills.stream()
                        .map(s -> s.getSkillName().toLowerCase(Locale.ROOT))
                        .collect(Collectors.toSet());
                long covered = required.stream().filter(s -> extracted.contains(s.toLowerCase(Locale.ROOT))).count();
                double coveragePercent = covered * 100.0 / required.size();
                result.addComponent("resume", new ReadinessScore.Component(
                        "Resume skill coverage", coveragePercent, WEIGHT_RESUME,
                        covered + "/" + required.size() + " required skills for '" + targetRole + "' found on resume"));
            } else {
                // No target role / no taxonomy for it yet - give partial credit just for having an analysed resume.
                double flatCredit = Math.min(100.0, resumeSkills.size() * 10.0);
                result.addComponent("resume", new ReadinessScore.Component(
                        "Resume skill coverage", flatCredit, WEIGHT_RESUME,
                        resumeSkills.size() + " skill(s) extracted (set a target role in your profile for a precise coverage score)"));
            }
        }

        // 5. Mock interview performance
        List<MockInterviewSession> recentMocks = mockInterviewDAO.findRecentCompleted(userId, 5);
        if (!recentMocks.isEmpty()) {
            double avgMockScore = recentMocks.stream()
                    .filter(s -> s.getAverageScore() != null)
                    .mapToDouble(s -> s.getAverageScore().doubleValue())
                    .average().orElse(0.0);
            result.addComponent("mock", new ReadinessScore.Component(
                    "Mock interview performance", avgMockScore, WEIGHT_MOCK,
                    recentMocks.size() + " recent completed mock interview(s)"));
        }

        // --- Explainable Readiness: re-blend the same signals above into five human-facing dimensions ---
        Map<Integer, Topic> topicsById = topicDAO.findAllIncludingInactive().stream()
                .collect(Collectors.toMap(Topic::getTopicId, t -> t, (a, b) -> a));
        UserAnswerDAO.DescriptiveScoreStats descriptiveStats = userAnswerDAO.getDescriptiveScoreStats(userId);
        int currentStreakDays = gamificationDAO.findOrCreate(userId).getCurrentStreakDays();
        Optional<JobMatchResultDAO.Row> latestJobMatch = jobMatchResultDAO.findLatestByUser(userId);
        Double latestJobMatchScore = latestJobMatch.map(JobMatchResultDAO.Row::matchScore).orElse(null);
        BehavioralSignals behavioral = collectBehavioralSignals(userId);

        computeDimensions(result, topicPerf, topicsById, stats, descriptiveStats, recentMocks, mistakeCounts,
                currentStreakDays, targetRole, latestJobMatchScore, behavioral);

        int totalDimensionWeight = result.getDimensions().values().stream()
                .mapToInt(ReadinessScore.Component::getWeight).sum();
        double overall;
        if (totalDimensionWeight == 0) {
            overall = 0.0;
        } else {
            double weightedSum = result.getDimensions().values().stream()
                    .mapToDouble(c -> c.getValue() * c.getWeight()).sum();
            overall = weightedSum / totalDimensionWeight;
        }
        overall = Math.round(overall * 100.0) / 100.0;

        result.setOverallScore(overall);
        result.setBand(bandFor(overall, totalDimensionWeight));
        result.setComputedAt(java.time.LocalDateTime.now());

        applyStrengthRiskAndNextAction(result, mistakeCounts, resumeService.getMissingSkills(userId),
                recentMocks.isEmpty(), behavioral.answeredCount() == 0);

        readinessScoreDAO.insertSnapshot(userId, result);
        return result;
    }

    /**
     * Builds the five Explainable Readiness dimensions from signals already
     * gathered by {@link #compute}, adding each dimension to {@code result}
     * only when the user actually has data for it (same "don't fake a
     * zero" rule the raw components already follow).
     *
     *  - Technical: assessment performance + topic accuracy on non-CS-fundamentals
     *    topics (Programming/Databases/etc.) - raw knowledge and application.
     *  - Problem Solving: topic accuracy specifically on CS Fundamentals topics
     *    (Data Structures & Algorithms, Operating Systems, Networks) - analytical reasoning.
     *  - Communication: AI-graded score on descriptive/open-ended answers blended
     *    with mock interview performance - how well the user explains themselves.
     *  - Behavioral: primarily the candidate's actual behavioral/STAR
     *    interview performance - STAR (Situation/Task/Action/Result)
     *    completeness blended with the AI-graded score on the "tell me
     *    about a time..." style questions they've answered in an AI Mock
     *    Interview (see {@link #collectBehavioralSignals}). Until the user
     *    has answered at least one real behavioral question, this falls
     *    back to mistake-resolution discipline and daily-practice streak
     *    as a rough placeholder proxy, clearly labelled as such.
     *  - Role Alignment: resume-to-target-role skill coverage blended with the
     *    latest Resume + JD Job Match Score, when one exists.
     */
    private void computeDimensions(ReadinessScore result, List<TopicPerformance> topicPerf,
                                    Map<Integer, Topic> topicsById, OverallStats stats,
                                    UserAnswerDAO.DescriptiveScoreStats descriptiveStats,
                                    List<MockInterviewSession> recentMocks, MistakeLogDAO.MistakeCounts mistakeCounts,
                                    int currentStreakDays, String targetRole, Double latestJobMatchScore,
                                    BehavioralSignals behavioral) {

        List<Double> technicalSignals = new ArrayList<>();
        List<Double> problemSolvingSignals = new ArrayList<>();
        for (TopicPerformance tp : topicPerf) {
            Topic topic = topicsById.get(tp.getTopicId());
            String category = topic == null ? null : topic.getCategory();
            if (category != null && PROBLEM_SOLVING_CATEGORIES.contains(category)) {
                problemSolvingSignals.add(tp.getAccuracyPercent().doubleValue());
            } else {
                technicalSignals.add(tp.getAccuracyPercent().doubleValue());
            }
        }
        if (stats.getTotalAssessments() > 0) {
            technicalSignals.add(stats.getAverageScorePercent());
        }

        if (!technicalSignals.isEmpty()) {
            result.addDimension("technical", new ReadinessScore.Component(
                    "Technical", average(technicalSignals), WEIGHT_TECHNICAL,
                    "Blends overall assessment performance with accuracy on programming/database topics."));
        }
        if (!problemSolvingSignals.isEmpty()) {
            result.addDimension("problem_solving", new ReadinessScore.Component(
                    "Problem Solving", average(problemSolvingSignals), WEIGHT_PROBLEM_SOLVING,
                    "Accuracy on Data Structures & Algorithms / CS Fundamentals topics."));
        }

        List<Double> communicationSignals = new ArrayList<>();
        String communicationNote;
        if (descriptiveStats.count() > 0) {
            communicationSignals.add(descriptiveStats.averageAiScore());
        }
        if (!recentMocks.isEmpty()) {
            recentMocks.stream().filter(s -> s.getAverageScore() != null)
                    .mapToDouble(s -> s.getAverageScore().doubleValue())
                    .average().ifPresent(communicationSignals::add);
        }
        if (!communicationSignals.isEmpty()) {
            communicationNote = descriptiveStats.count() > 0 && !recentMocks.isEmpty()
                    ? "Blends AI-graded descriptive answers with mock interview performance."
                    : descriptiveStats.count() > 0
                        ? "Based on " + descriptiveStats.count() + " AI-graded descriptive answer(s)."
                        : "Based on " + recentMocks.size() + " recent mock interview(s).";
            result.addDimension("communication", new ReadinessScore.Component(
                    "Communication", average(communicationSignals), WEIGHT_COMMUNICATION, communicationNote));
        }

        List<Double> behavioralSignals = new ArrayList<>();
        String behavioralNote;
        if (behavioral.answeredCount() > 0) {
            if (!behavioral.starScores().isEmpty()) {
                behavioralSignals.add(average(behavioral.starScores()));
            }
            if (!behavioral.aiScores().isEmpty()) {
                behavioralSignals.add(average(behavioral.aiScores()));
            }
            behavioralNote = "Based on " + behavioral.answeredCount() + " real behavioral interview answer(s): "
                    + "blends STAR (Situation/Task/Action/Result) completeness with the AI-graded score on "
                    + "\"tell me about a time...\" style questions from your Mock Interviews.";
        } else {
            // No behavioral mock-interview answers yet - fall back to a rough proxy so the
            // dimension isn't just missing, but say plainly that it's a placeholder.
            if (mistakeCounts.total() > 0) {
                behavioralSignals.add(mistakeCounts.resolved() * 100.0 / mistakeCounts.total());
            }
            if (currentStreakDays > 0) {
                behavioralSignals.add(Math.min(100.0, currentStreakDays * 10.0));
            }
            behavioralNote = "Placeholder score based on mistake-resolution discipline and your " + currentStreakDays
                    + "-day practice streak - answer a behavioral question (e.g. \"Tell me about a time you...\") "
                    + "in an AI Mock Interview for a real STAR-based score.";
        }
        if (!behavioralSignals.isEmpty()) {
            result.addDimension("behavioral", new ReadinessScore.Component(
                    "Behavioral", average(behavioralSignals), WEIGHT_BEHAVIORAL, behavioralNote));
        }

        List<Double> roleAlignmentSignals = new ArrayList<>();
        ReadinessScore.Component resumeComponent = result.getComponents().get("resume");
        if (resumeComponent != null) {
            roleAlignmentSignals.add(resumeComponent.getValue());
        }
        if (latestJobMatchScore != null) {
            roleAlignmentSignals.add(latestJobMatchScore);
        }
        if (!roleAlignmentSignals.isEmpty()) {
            String note = latestJobMatchScore != null && resumeComponent != null
                    ? "Blends target-role resume coverage with your latest Job Match Score."
                    : latestJobMatchScore != null
                        ? "Based on your latest Resume + JD Job Match Score."
                        : "Based on resume skill coverage for '" + targetRole + "'.";
            result.addDimension("role_alignment", new ReadinessScore.Component(
                    "Role Alignment", average(roleAlignmentSignals), WEIGHT_ROLE_ALIGNMENT, note));
        }
    }

    private static double average(List<Double> values) {
        return values.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
    }

    /** Real behavioral/STAR signal extracted from the user's own mock-interview answers, for the Behavioral dimension. */
    private record BehavioralSignals(int answeredCount, List<Double> starScores, List<Double> aiScores) {
    }

    /**
     * Scans the user's most recently answered mock-interview questions
     * (across every session, not just completed ones - a candidate should
     * get credit the moment they answer a behavioral question, not only
     * once they finish the whole session), keeps only the ones that read
     * as behavioral/situational ({@link AIService#isBehavioralQuestion}),
     * and pulls out two numbers per answer:
     *
     *  - its STAR completeness, from the "[Behavioral - ... STAR: NN%]"
     *    fragment {@code AIServiceImpl} appends to the AI feedback of every
     *    graded behavioral answer;
     *  - its overall AI-graded score.
     *
     * This is the real behavioral-interview performance data the Behavioral
     * readiness dimension is meant to reflect, as opposed to proxies like
     * mistake-resolution or login streak.
     */
    private BehavioralSignals collectBehavioralSignals(Long userId) throws SQLException {
        List<MockInterviewQuestion> recentAnswered =
                mockInterviewDAO.findRecentAnsweredByUser(userId, BEHAVIORAL_ANSWER_LOOKBACK);
        List<Double> starScores = new ArrayList<>();
        List<Double> aiScores = new ArrayList<>();
        int answeredCount = 0;
        for (MockInterviewQuestion q : recentAnswered) {
            if (!aiService.isBehavioralQuestion(q.getQuestionText())) {
                continue;
            }
            answeredCount++;
            if (q.getAiScore() != null) {
                aiScores.add(q.getAiScore().doubleValue());
            }
            if (q.getAiFeedback() != null) {
                Matcher m = STAR_SCORE_PATTERN.matcher(q.getAiFeedback());
                if (m.find()) {
                    starScores.add(Double.parseDouble(m.group(1)));
                }
            }
        }
        return new BehavioralSignals(answeredCount, starScores, aiScores);
    }

    private void applyStrengthRiskAndNextAction(ReadinessScore result, MistakeLogDAO.MistakeCounts mistakeCounts,
                                                 List<String> missingSkills, boolean noMockTaken,
                                                 boolean noBehavioralAnswered) {
        Map<String, ReadinessScore.Component> dims = result.getDimensions();
        String strongestKey = null, weakestKey = null;
        for (Map.Entry<String, ReadinessScore.Component> e : dims.entrySet()) {
            if (strongestKey == null || e.getValue().getValue() > dims.get(strongestKey).getValue()) {
                strongestKey = e.getKey();
            }
            if (weakestKey == null || e.getValue().getValue() < dims.get(weakestKey).getValue()) {
                weakestKey = e.getKey();
            }
        }
        if (strongestKey != null) {
            ReadinessScore.Component c = dims.get(strongestKey);
            result.setBiggestStrength(String.format(Locale.ROOT, "%s (%.1f/100)", c.getLabel(), c.getValue()));
        }
        if (weakestKey != null) {
            ReadinessScore.Component c = dims.get(weakestKey);
            result.setBiggestRisk(String.format(Locale.ROOT, "%s (%.1f/100)", c.getLabel(), c.getValue()));
        }

        result.setNextRecommendedAction(determineNextBestAction(mistakeCounts.unresolved(), missingSkills,
                weakestKey, dims.get(weakestKey), noMockTaken, noBehavioralAnswered, result.getOverallScore()));
    }

    /**
     * Pure Next Best Action logic (Adaptive Career Intelligence): a single,
     * concrete recommendation, in priority order -
     *   1. unresolved repeated/logged mistakes (fastest, highest-leverage fix)
     *   2. skills missing entirely for the user's target role
     *   3. the weakest scoring dimension, if it's clearly behind (&lt; 60)
     *   4. never having taken a mock interview (a whole readiness signal is missing)
     *   5. no real behavioral/STAR answer yet, so Behavioral is still running on the placeholder proxy
     *   6. a nudge toward Interview Ready if none of the above apply but the score isn't there yet
     *   7. "keep up the momentum" once everything above is satisfied.
     *
     * Kept as a 6-arg overload (delegating with {@code noBehavioralAnswered=false}) so existing
     * callers/tests that predate the Behavioral/STAR signal keep compiling unchanged.
     */
    static String determineNextBestAction(int unresolvedMistakes, List<String> missingSkills, String weakestDimensionKey,
                                           ReadinessScore.Component weakestDimension, boolean noMockTaken,
                                           double overallScore) {
        return determineNextBestAction(unresolvedMistakes, missingSkills, weakestDimensionKey, weakestDimension,
                noMockTaken, false, overallScore);
    }

    static String determineNextBestAction(int unresolvedMistakes, List<String> missingSkills, String weakestDimensionKey,
                                           ReadinessScore.Component weakestDimension, boolean noMockTaken,
                                           boolean noBehavioralAnswered, double overallScore) {
        if (unresolvedMistakes > 0) {
            return "Resolve your " + unresolvedMistakes + " unresolved mistake(s) in Mistake Analyzer & Retry.";
        }
        if (missingSkills != null && !missingSkills.isEmpty()) {
            String sample = String.join(", ", missingSkills.subList(0, Math.min(3, missingSkills.size())));
            return "Learn or add evidence of these missing skill(s) for your target role: " + sample
                    + " - this directly raises your Role Alignment score.";
        }
        if (weakestDimension != null && weakestDimension.getValue() < 60.0) {
            return "Focus on " + weakestDimension.getLabel() + " next - it's currently your weakest area at "
                    + String.format(Locale.ROOT, "%.1f/100", weakestDimension.getValue()) + ".";
        }
        if (noMockTaken) {
            return "Take your first AI Mock Interview - it fills in your Communication signal, which is missing entirely right now.";
        }
        if (noBehavioralAnswered) {
            return "Answer a behavioral question (e.g. \"Tell me about a time you...\") in an AI Mock Interview - "
                    + "your Behavioral score is still a mistake/streak placeholder until you have a real STAR answer.";
        }
        if (overallScore < 80.0) {
            return "Retake an assessment on your weakest topic to push your Interview Readiness Score toward 80+.";
        }
        return "Keep up the momentum - you're Interview Ready. Revisit periodically to stay sharp.";
    }

    private String bandFor(double overall, int totalWeight) {
        if (totalWeight == 0) {
            return "Not enough data yet";
        }
        if (overall >= 80) return "Interview Ready";
        if (overall >= 60) return "Almost There";
        if (overall >= 40) return "Needs More Practice";
        return "Just Getting Started";
    }

    public List<ReadinessScoreDAO.Snapshot> getHistory(Long userId, int limit) throws SQLException {
        return readinessScoreDAO.findRecentByUser(userId, limit);
    }
}
