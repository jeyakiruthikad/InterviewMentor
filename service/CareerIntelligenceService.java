package com.careerintelligence.service;

import com.careerintelligence.ai.AIService;
import com.careerintelligence.dao.CareerRefreshLogDAO;
import com.careerintelligence.dao.MistakeLogDAO;
import com.careerintelligence.dao.ReadinessScoreDAO;
import com.careerintelligence.dao.TopicDAO;
import com.careerintelligence.dao.UserProfileDAO;
import com.careerintelligence.model.CareerRefreshTrigger;
import com.careerintelligence.model.DifficultyLevel;
import com.careerintelligence.model.JobDescription;
import com.careerintelligence.model.LearningRoadmap;
import com.careerintelligence.model.MistakeEntry;
import com.careerintelligence.model.ReadinessScore;
import com.careerintelligence.model.ResumeSkill;
import com.careerintelligence.model.RoadmapItem;
import com.careerintelligence.model.Topic;
import com.careerintelligence.model.TopicPerformance;
import com.careerintelligence.model.UserProfile;
import com.careerintelligence.util.AdaptiveDifficultyCalculator;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The single orchestration point that turns the app's separate features
 * into one connected Career Intelligence system:
 *
 * <pre>
 * Profile -&gt; Resume -&gt; Skills -&gt; Skill Gap -&gt; Roadmap -&gt; Recommendations
 *   -&gt; Adaptive Assessment -&gt; AI Evaluation -&gt; Mistakes -&gt; Practice
 *   -&gt; Mock Interview -&gt; Readiness -&gt; Dashboard -&gt; Roadmap Update (loop)
 * </pre>
 *
 * This service does not own any data and re-implements none of the
 * existing feature logic - it purely <em>composes</em> the services that
 * already do (ResumeService, MistakeAnalyzerService, ReadinessScoreService,
 * RoadmapService) so every underlying feature keeps working exactly as it
 * did before. Its job is twofold:
 *
 * <ol>
 *   <li><b>Personalisation reads</b> - {@link #buildSnapshot} for the
 *       Dashboard, and {@link #recommendFocusTopics} /
 *       {@link #recommendMockInterviewDefaults} so the Adaptive Assessment
 *       and AI Mock Interview screens can default their topic/role
 *       selection to what the user's resume and weak spots actually call
 *       for, instead of asking them to guess.</li>
 *   <li><b>Downstream refresh writes</b> - {@link #refreshAfterActivity},
 *       called once an Assessment, Mistake Retry, or Mock Interview
 *       finishes, so skill gaps, the roadmap and the readiness score are
 *       never stale: every practice activity immediately feeds back into
 *       "what should I do next".</li>
 * </ol>
 */
public class CareerIntelligenceService {

    private final ResumeService resumeService = new ResumeService();
    private final MistakeAnalyzerService mistakeAnalyzerService = new MistakeAnalyzerService();
    private final ReadinessScoreService readinessScoreService = new ReadinessScoreService();
    private final RoadmapService roadmapService = new RoadmapService();
    private final TopicDAO topicDAO = new TopicDAO();
    private final UserProfileDAO userProfileDAO = new UserProfileDAO();
    private final MistakeLogDAO mistakeLogDAO = new MistakeLogDAO();
    private final CareerRefreshLogDAO careerRefreshLogDAO = new CareerRefreshLogDAO();
    private final ProgressAnalyticsService progressAnalyticsService = new ProgressAnalyticsService();

    /** Topics the system currently recommends a user focus on, and why (weak performance and/or a resume-skill match). */
    public record RecommendedFocus(List<Integer> topicIds, List<String> topicNames) {
        public boolean isEmpty() {
            return topicIds.isEmpty();
        }
    }

    /** Everything needed to pre-fill a personalised AI Mock Interview without the user re-typing what the system already knows. */
    public record MockInterviewDefaults(String targetRole, String targetCompany, List<String> resumeSkillNames,
                                         List<Integer> topicIds, List<String> topicNames) {
    }

    /** One complete, personalised read of a user's career state - resume/skills/gaps, readiness, and the latest roadmap - for the Dashboard. */
    public record CareerSnapshot(
            Long userId,
            Optional<UserProfile> profile,
            List<ResumeSkill> resumeSkills,
            List<String> missingSkills,
            List<String> weakSkills,
            List<TopicPerformance> weakTopics,
            RecommendedFocus recommendedFocus,
            ReadinessScore readiness,
            Optional<LearningRoadmap> roadmap,
            MistakeLogDAO.MistakeCounts mistakeCounts,
            ResumeService.RoleMatch roleMatch,
            ProgressAnalyticsService.AnalyticsSummary analytics) {

        public String targetRole() {
            return profile.map(UserProfile::getTargetRole).orElse(null);
        }
    }

    /**
     * Builds the complete, personalised career state for one user: resume
     * skills, skill gaps (missing + weak), weak topics, recommended focus
     * topics, the current Interview Readiness Score, the latest roadmap,
     * mistake counts, the Resume-Role Match breakdown, and the Progress
     * Analytics trend summary. This is the read side of the pipeline - it is
     * what {@code IntegratedDashboardService} and the Complete Dashboard
     * screen are built on, so the dashboard always reflects everything the
     * system currently knows about the user.
     */
    public CareerSnapshot buildSnapshot(Long userId) throws SQLException {
        Optional<UserProfile> profile = userProfileDAO.findByUserId(userId);
        List<ResumeSkill> resumeSkills = resumeService.getExtractedSkills(userId);
        List<String> missingSkills = resumeService.getMissingSkills(userId);
        List<String> weakSkills = resumeService.getWeakSkills(userId);
        List<TopicPerformance> weakTopics = mistakeAnalyzerService.getWeakTopics(userId);
        RecommendedFocus focus = recommendFocusTopics(userId);
        ReadinessScore readiness = readinessScoreService.compute(userId);
        Optional<LearningRoadmap> roadmap = roadmapService.getLatest(userId);
        MistakeLogDAO.MistakeCounts mistakeCounts = mistakeLogDAO.countsByUser(userId);
        ResumeService.RoleMatch roleMatch = resumeService.computeRoleMatch(userId);
        ProgressAnalyticsService.AnalyticsSummary analytics = progressAnalyticsService.buildSummary(userId);

        return new CareerSnapshot(userId, profile, resumeSkills, missingSkills, weakSkills, weakTopics,
                focus, readiness, roadmap, mistakeCounts, roleMatch, analytics);
    }

    /**
     * Recommends which question-bank topics an Adaptive Assessment or Mock
     * Interview should focus on: topics the user is currently weak on
     * (from {@code topic_performance}, i.e. Mistakes/Practice history)
     * first, then topics their resume skills matched onto but haven't
     * necessarily been tested on yet (Skills/Skill Gap). Both signals are
     * already tracked by ResumeService and MistakeAnalyzerService - this
     * method only merges and orders them, it introduces no new data
     * source.
     */
    public RecommendedFocus recommendFocusTopics(Long userId) throws SQLException {
        List<Integer> weakTopicIds = mistakeAnalyzerService.getWeakTopics(userId).stream()
                .map(TopicPerformance::getTopicId)
                .collect(Collectors.toList());
        List<Integer> resumeMatchedTopicIds = resumeService.getMatchedTopicIds(userId);

        List<Integer> merged = mergeUnique(weakTopicIds, resumeMatchedTopicIds);
        List<String> names = new ArrayList<>();
        for (Integer topicId : merged) {
            Optional<Topic> topic = topicDAO.findById(topicId);
            topic.ifPresent(t -> names.add(t.getTopicName()));
        }
        return new RecommendedFocus(merged, names);
    }

    /**
     * Recommends role, resume skills, and topics to pre-fill a Mock
     * Interview with, so the interview is personalised to the candidate by
     * default (their target role from Profile, their resume skills, and
     * their current weak/matched topics) instead of a blank, generic form.
     */
    public MockInterviewDefaults recommendMockInterviewDefaults(Long userId) throws SQLException {
        Optional<UserProfile> profile = userProfileDAO.findByUserId(userId);
        String targetRole = profile.map(UserProfile::getTargetRole)
                .filter(role -> role != null && !role.isBlank())
                .orElse(null);
        String targetCompany = profile.map(UserProfile::getTargetCompany)
                .filter(company -> company != null && !company.isBlank())
                .orElse(null);
        List<String> resumeSkillNames = resumeService.getExtractedSkills(userId).stream()
                .map(ResumeSkill::getSkillName)
                .collect(Collectors.toList());
        RecommendedFocus focus = recommendFocusTopics(userId);
        return new MockInterviewDefaults(targetRole, targetCompany, resumeSkillNames, focus.topicIds(), focus.topicNames());
    }

    /**
     * Builds the full Personalized Questions context (the AI Mock
     * Interview's {@link AIService.PersonalizationContext}): target role,
     * resume skills, the required skills of the user's most recently
     * analysed job description, resume/JD skill gaps, topics they've
     * previously gotten wrong (still unresolved), and a starting difficulty
     * derived from their overall demonstrated accuracy - reusing
     * ResumeService and MistakeAnalyzerService exactly as
     * {@link #recommendMockInterviewDefaults} already does, rather than
     * duplicating that logic. This is what lets {@code MockInterviewService}
     * generate genuinely personalised questions instead of generic
     * skill-name templates.
     */
    public AIService.PersonalizationContext buildInterviewPersonalizationContext(Long userId) throws SQLException {
        Optional<UserProfile> profile = userProfileDAO.findByUserId(userId);
        String targetRole = profile.map(UserProfile::getTargetRole)
                .filter(role -> role != null && !role.isBlank())
                .orElse(null);

        List<String> resumeSkills = resumeService.getExtractedSkills(userId).stream()
                .map(ResumeSkill::getSkillName)
                .collect(Collectors.toList());
        List<String> skillGaps = resumeService.getMissingSkills(userId);
        List<String> jobRequiredSkills = resumeService.getLatestJobDescription(userId)
                .map(JobDescription::getRequiredSkills)
                .orElse(List.of());

        List<String> previousMistakeTopics = mistakeAnalyzerService.getUnresolvedMistakes(userId).stream()
                .map(MistakeEntry::getTopicName)
                .filter(name -> name != null && !name.isBlank())
                .distinct()
                .limit(5)
                .collect(Collectors.toList());

        DifficultyLevel difficulty = deriveCurrentDifficulty(userId);

        return new AIService.PersonalizationContext(targetRole, resumeSkills, jobRequiredSkills, skillGaps,
                previousMistakeTopics, difficulty);
    }

    /**
     * Derives the AI Mock Interview's starting difficulty from the user's
     * overall demonstrated accuracy across every topic they've attempted -
     * the same cumulative-accuracy rule {@link AdaptiveDifficultyCalculator}
     * already uses for the Adaptive Assessment engine - so a mock interview
     * opens at a difficulty consistent with the candidate's actual level
     * instead of always defaulting to MEDIUM.
     */
    private DifficultyLevel deriveCurrentDifficulty(Long userId) throws SQLException {
        List<TopicPerformance> attempted = mistakeAnalyzerService.getAllAttemptedTopics(userId);
        if (attempted.isEmpty()) {
            return DifficultyLevel.MEDIUM;
        }
        double avgAccuracy = attempted.stream()
                .mapToDouble(tp -> tp.getAccuracyPercent().doubleValue())
                .average().orElse(0.0);
        int totalAttempts = attempted.stream().mapToInt(TopicPerformance::getAttemptsCount).sum();
        return AdaptiveDifficultyCalculator.baselineDifficulty(avgAccuracy, totalAttempts);
    }

    /**
     * Closes the loop: called once an Assessment, Mistake Retry, Mock
     * Interview, or Job Description analysis finishes, this recomputes the
     * Interview Readiness Score and regenerates the Learning Roadmap off
     * the user's now-updated topic performance / mistake log / mock
     * interview / job-match history (exactly what
     * {@link RoadmapService#generate} already does - reused here rather
     * than duplicated), and records an audit entry so the "Roadmap Update"
     * step of the pipeline is observable. So every practice activity
     * immediately updates skill gaps, recommendations, the roadmap and the
     * readiness score - nothing goes stale until the user looks at it
     * again.
     *
     * <p>Kept as the original, backward-compatible entry point (same
     * signature every existing caller already uses) - it now also captures
     * and persists a "what changed and why" explanation under the hood via
     * {@link #refreshAfterActivityDetailed}, without changing what callers
     * get back.
     */
    public LearningRoadmap refreshAfterActivity(Long userId, CareerRefreshTrigger trigger) throws SQLException {
        return refreshAfterActivityDetailed(userId, trigger).roadmap();
    }

    /** {@link #refreshAfterActivity}'s regenerated roadmap, plus a human-readable explanation of what changed since the previous refresh and why - the Career Intelligence Updates feature. */
    public record RefreshOutcome(LearningRoadmap roadmap, String explanation) {
    }

    /**
     * Same pipeline refresh as {@link #refreshAfterActivity}, but also
     * builds and returns the Career Intelligence Updates explanation: what
     * changed in the user's readiness/roadmap since the last refresh, and
     * why, using their actual before/after data (never a canned string).
     * The explanation is persisted alongside the audit log entry so it can
     * be surfaced again later (e.g. a "what changed" history view) without
     * recomputing anything.
     */
    public RefreshOutcome refreshAfterActivityDetailed(Long userId, CareerRefreshTrigger trigger) throws SQLException {
        // Snapshot "before" state first - roadmapService.generate() below recomputes and immediately
        // persists a NEW readiness snapshot, so this is the last chance to see the prior one.
        List<ReadinessScoreDAO.Snapshot> previousReadinessHistory = readinessScoreService.getHistory(userId, 1);
        Optional<ReadinessScoreDAO.Snapshot> previousReadiness = previousReadinessHistory.isEmpty()
                ? Optional.empty() : Optional.of(previousReadinessHistory.get(0));
        Optional<LearningRoadmap> previousRoadmap = roadmapService.getLatest(userId);

        LearningRoadmap roadmap = roadmapService.generate(userId);

        String explanation = buildChangeExplanation(trigger, previousReadiness, roadmap.getReadinessScore(),
                dimensionMapFromLatest(userId), previousRoadmap, roadmap);

        try {
            careerRefreshLogDAO.log(userId, trigger, roadmap.getReadinessScore(), roadmap.getRoadmapId(), explanation);
        } catch (SQLException e) {
            // Best-effort audit trail only - never let a logging failure block the refresh itself.
            System.err.println("[CareerIntelligence] Could not write refresh log: " + e.getMessage());
        }
        return new RefreshOutcome(roadmap, explanation);
    }

    /** The dimension breakdown of the readiness snapshot {@link RoadmapService#generate} just persisted, keyed the same way {@link ReadinessScore#getDimensions()} is. Best-effort: an empty map just means the explanation skips dimension-level detail. */
    private Map<String, Double> dimensionMapFromLatest(Long userId) throws SQLException {
        List<ReadinessScoreDAO.Snapshot> latest = readinessScoreService.getHistory(userId, 1);
        if (latest.isEmpty()) {
            return Map.of();
        }
        ReadinessScoreDAO.Snapshot s = latest.get(0);
        Map<String, Double> map = new LinkedHashMap<>();
        map.put("technical", s.technicalDimension());
        map.put("problem_solving", s.problemSolvingDimension());
        map.put("communication", s.communicationDimension());
        map.put("behavioral", s.behavioralDimension());
        map.put("role_alignment", s.roleAlignmentDimension());
        return map;
    }

    /**
     * Pure explanation-building core of {@link #refreshAfterActivityDetailed}
     * (unit-testable without a database): compares the previous readiness
     * snapshot to the newly computed score, and the previous roadmap's top
     * item to the new one's, and narrates the delta using the user's actual
     * numbers - never a templated, content-free message.
     */
    static String buildChangeExplanation(CareerRefreshTrigger trigger, Optional<ReadinessScoreDAO.Snapshot> previous,
                                          double newOverall, Map<String, Double> newDimensions,
                                          Optional<LearningRoadmap> previousRoadmap, LearningRoadmap newRoadmap) {
        StringBuilder sb = new StringBuilder();
        sb.append(triggerNarration(trigger)).append(' ');

        if (previous.isEmpty()) {
            sb.append(String.format(Locale.ROOT,
                    "Your Interview Readiness Score has been computed for the first time: %.1f/100.", newOverall));
        } else {
            double delta = Math.round((newOverall - previous.get().overallScore()) * 100.0) / 100.0;
            if (Math.abs(delta) < 0.05) {
                sb.append(String.format(Locale.ROOT,
                        "Your Interview Readiness Score is unchanged at %.1f/100.", newOverall));
            } else {
                sb.append(String.format(Locale.ROOT, "Your Interview Readiness Score %s from %.1f to %.1f (%s%.1f).",
                        delta > 0 ? "improved" : "dropped", previous.get().overallScore(), newOverall,
                        delta > 0 ? "+" : "", delta));
            }

            String movedDimension = biggestDimensionMover(previous.get(), newDimensions);
            if (movedDimension != null) {
                sb.append(" This was driven mainly by your ").append(movedDimension).append(" dimension.");
            }
        }

        int newCount = newRoadmap.getItems() == null ? 0 : newRoadmap.getItems().size();
        String newTop = topItemTitle(newRoadmap);
        String previousTop = previousRoadmap.map(CareerIntelligenceService::topItemTitle).orElse(null);
        if (newTop != null && !newTop.equals(previousTop)) {
            sb.append(" Your top roadmap priority is now \"").append(newTop).append("\".");
        }
        sb.append(" The roadmap now has ").append(newCount).append(" item(s).");

        return sb.toString();
    }

    private static String triggerNarration(CareerRefreshTrigger trigger) {
        return switch (trigger) {
            case ASSESSMENT_COMPLETED -> "You just completed an assessment.";
            case MISTAKE_RETRY_COMPLETED -> "You just retried some previously missed questions.";
            case MOCK_INTERVIEW_COMPLETED -> "You just completed a mock interview.";
            case RESUME_UPDATED -> "You just updated your resume.";
            case JOB_DESCRIPTION_ANALYZED -> "You just analysed a new job description.";
            case MANUAL_ROADMAP_REQUEST -> "You requested a roadmap refresh.";
        };
    }

    private static String topItemTitle(LearningRoadmap roadmap) {
        if (roadmap == null || roadmap.getItems() == null || roadmap.getItems().isEmpty()) {
            return null;
        }
        return roadmap.getItems().stream()
                .min((a, b) -> Integer.compare(a.getItemOrder(), b.getItemOrder()))
                .map(RoadmapItem::getTitle)
                .orElse(null);
    }

    private static final Map<String, String> DIMENSION_LABELS = Map.of(
            "technical", "Technical", "problem_solving", "Problem Solving", "communication", "Communication",
            "behavioral", "Behavioral", "role_alignment", "Role Alignment");

    /** Finds which dimension moved the most (by absolute value) between the previous snapshot and the newly computed dimensions, or null if neither snapshot has dimension data. */
    static String biggestDimensionMover(ReadinessScoreDAO.Snapshot previous, Map<String, Double> newDimensions) {
        if (newDimensions == null || newDimensions.isEmpty()) {
            return null;
        }
        Map<String, Double> previousDims = Map.of(
                "technical", previous.technicalDimension(),
                "problem_solving", previous.problemSolvingDimension(),
                "communication", previous.communicationDimension(),
                "behavioral", previous.behavioralDimension(),
                "role_alignment", previous.roleAlignmentDimension());
        String best = null;
        double bestDelta = 0.0;
        for (Map.Entry<String, Double> e : newDimensions.entrySet()) {
            Double prevVal = previousDims.get(e.getKey());
            if (prevVal == null) continue;
            double delta = Math.abs(e.getValue() - prevVal);
            if (delta > bestDelta) {
                bestDelta = delta;
                best = e.getKey();
            }
        }
        if (best == null || bestDelta < 0.5) {
            return null; // nothing moved meaningfully
        }
        return DIMENSION_LABELS.get(best);
    }

    // -----------------------------------------------------------------
    // Adaptive Career Intelligence: Job Description Analysis entry point
    // -----------------------------------------------------------------

    /** {@link ResumeService#analyzeAndMatch}'s result, plus the freshly-refreshed roadmap/readiness pipeline outcome, so a single call takes a pasted JD all the way to "Resume + JD -> Skill Gaps -> Roadmap". */
    public record JobDescriptionOutcome(JobDescription jobDescription, ResumeService.JobMatchResult match,
                                         RefreshOutcome refresh) {
    }

    /**
     * The Job Description Analysis + Resume/JD Matching + Adaptive Career
     * Intelligence entry point: analyses the pasted JD, computes the
     * explainable Job Match Score against the user's resume + assessment
     * history, then immediately refreshes the roadmap/readiness pipeline
     * so the JD's skill gaps show up as roadmap priorities right away -
     * completing "Resume + JD -&gt; Skill Gaps -&gt; Roadmap -&gt; ... -&gt;
     * Next Best Action" in one call.
     */
    public JobDescriptionOutcome analyzeJobDescriptionAndMatch(Long userId, String jobDescriptionText) throws SQLException {
        JobDescription jd = resumeService.analyzeJobDescription(userId, jobDescriptionText);
        ResumeService.JobMatchResult match = resumeService.matchResumeToJobDescription(userId, jd);
        RefreshOutcome refresh = refreshAfterActivityDetailed(userId, CareerRefreshTrigger.JOB_DESCRIPTION_ANALYZED);
        return new JobDescriptionOutcome(jd, match, refresh);
    }

    /** Order-preserving union of two topic-id lists with no duplicates. Package-private + pure so it's unit-testable without a database. */
    static List<Integer> mergeUnique(List<Integer> primary, List<Integer> secondary) {
        Set<Integer> seen = new LinkedHashSet<>();
        if (primary != null) {
            seen.addAll(primary);
        }
        if (secondary != null) {
            seen.addAll(secondary);
        }
        return new ArrayList<>(seen);
    }
}
