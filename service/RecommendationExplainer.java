package com.careerintelligence.service;

import com.careerintelligence.dao.MistakeLogDAO;
import com.careerintelligence.model.ReadinessScore;
import com.careerintelligence.model.ResumeSkill;
import com.careerintelligence.model.TopicPerformance;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Turns the system's recommendation into an <em>explained</em>
 * recommendation: "Why this recommendation?" and "What should I do next?",
 * both grounded in the user's own data rather than a canned string.
 *
 * <p>The evidence this class cites is always traceable to something the
 * rest of the system actually recorded:
 * <ul>
 *   <li><b>Resume</b> - extracted skills, and skills claimed on the resume
 *       that the user has since underperformed on ("claimed but weak").</li>
 *   <li><b>Job description</b> - required skills missing from the resume,
 *       and the JD match score.</li>
 *   <li><b>Mistakes</b> - unresolved entries in the mistake log.</li>
 *   <li><b>Performance</b> - per-topic accuracy, assessment averages and
 *       the weakest readiness dimension.</li>
 * </ul>
 *
 * <p>Every method is pure and dependency-free (it takes plain values, not
 * DAOs), so the explanation logic is fully unit-testable and cannot drift
 * from what the dashboard displays.
 */
public final class RecommendationExplainer {

    /** How far below this per-topic accuracy counts as a weakness worth citing. */
    private static final double WEAK_TOPIC_THRESHOLD = 60.0;

    private RecommendationExplainer() {
    }

    /**
     * One explained recommendation: the action itself, the evidence lines
     * behind it ("why"), and the concrete steps to take ("what next").
     */
    public record Explanation(String action, List<String> why, List<String> whatNext) {

        public boolean hasEvidence() {
            return why != null && !why.isEmpty();
        }
    }

    /**
     * The input bundle the explainer reasons over. Deliberately made of
     * plain types so callers can build it from a live
     * {@code CareerSnapshot} or from demo data with equal ease.
     */
    public record Evidence(String targetRole,
                            ReadinessScore readiness,
                            List<ResumeSkill> resumeSkills,
                            List<String> missingSkills,
                            List<String> weakSkills,
                            List<TopicPerformance> weakTopics,
                            MistakeLogDAO.MistakeCounts mistakeCounts,
                            Double jobMatchScore,
                            String jobTitle,
                            List<String> jobHighPrioritySkills,
                            Double averageAssessmentScore,
                            int completedAssessments,
                            int completedMockInterviews,
                            int roadmapTotalItems,
                            int roadmapCompletedItems) {
    }

    /**
     * Builds the full explained "Recommended Next Action" for a user.
     *
     * <p>The action itself is the readiness engine's own
     * {@code nextRecommendedAction} when it has produced one - this class
     * never overrides the system's recommendation, it explains it. When no
     * recommendation exists yet (a brand-new account), it falls back to the
     * highest-value onboarding step implied by the missing data.
     */
    public static Explanation explainNextAction(Evidence e) {
        String action = resolveAction(e);
        List<String> why = buildWhy(e);
        List<String> next = buildWhatNext(e);
        return new Explanation(action, why, next);
    }

    private static String resolveAction(Evidence e) {
        if (e.readiness() != null && e.readiness().getNextRecommendedAction() != null
                && !e.readiness().getNextRecommendedAction().isBlank()) {
            return e.readiness().getNextRecommendedAction();
        }
        // Onboarding fallbacks, ordered by how much signal each unlocks.
        if (e.resumeSkills() == null || e.resumeSkills().isEmpty()) {
            return "Upload your resume so the system can extract your skills and find your role gaps.";
        }
        if (e.jobMatchScore() == null) {
            return "Paste a target job description to get an explainable Job Match Score and JD-driven roadmap.";
        }
        if (e.completedAssessments() == 0) {
            return "Take your first adaptive assessment to establish a performance baseline.";
        }
        if (e.completedMockInterviews() == 0) {
            return "Run an AI mock interview to score your communication and behavioral readiness.";
        }
        return "Work through the highest-priority item on your learning roadmap.";
    }

    /**
     * Assembles the "why" evidence lines, strongest signal first. Each line
     * names the data source in brackets so a demo audience can see the
     * recommendation is derived, not generated.
     */
    static List<String> buildWhy(Evidence e) {
        List<String> why = new ArrayList<>();

        // 1. The weakest readiness dimension - the single biggest driver of the score.
        if (e.readiness() != null) {
            ReadinessScore.Component weakest = weakestDimension(e.readiness());
            if (weakest != null) {
                why.add(String.format(Locale.ROOT,
                        "[Performance] %s is your lowest readiness dimension at %.1f/100, so it is capping your overall score of %.1f.",
                        weakest.getLabel(), weakest.getValue(), e.readiness().getOverallScore()));
            }
        }

        // 2. Job-description gaps - the most role-specific, highest-stakes evidence.
        if (e.jobMatchScore() != null && e.missingSkills() != null && !e.missingSkills().isEmpty()) {
            String role = e.jobTitle() != null ? e.jobTitle()
                    : (e.targetRole() != null ? e.targetRole() : "your target role");
            why.add(String.format(Locale.ROOT,
                    "[Job Description] Your resume matches %s at %.1f%%; %s %s required by the JD but absent from your resume.",
                    role, e.jobMatchScore(), joinTop(e.missingSkills(), 3),
                    e.missingSkills().size() == 1 ? "is" : "are"));
        } else if (e.missingSkills() != null && !e.missingSkills().isEmpty() && e.targetRole() != null) {
            why.add(String.format(Locale.ROOT,
                    "[Resume] %s %s listed as required for %s but not found on your resume.",
                    joinTop(e.missingSkills(), 3), e.missingSkills().size() == 1 ? "is" : "are", e.targetRole()));
        }

        // 3. Claimed-but-weak skills - the credibility risk an interviewer will probe.
        if (e.weakSkills() != null && !e.weakSkills().isEmpty()) {
            why.add(String.format(Locale.ROOT,
                    "[Resume vs Performance] You list %s on your resume but score below the pass mark on %s in practice - an interviewer is likely to probe there.",
                    joinTop(e.weakSkills(), 3), e.weakSkills().size() == 1 ? "it" : "them"));
        }

        // 4. Measured weak topics, cited with their actual accuracy.
        if (e.weakTopics() != null && !e.weakTopics().isEmpty()) {
            TopicPerformance worst = e.weakTopics().stream()
                    .min(Comparator.comparingDouble(tp -> tp.getAccuracyPercent() == null
                            ? 0.0 : tp.getAccuracyPercent().doubleValue()))
                    .orElse(null);
            if (worst != null && worst.getAccuracyPercent() != null) {
                why.add(String.format(Locale.ROOT,
                        "[Assessments] Your weakest measured topic is %s at %.1f%% accuracy over %d attempt(s), below the %.0f%% competency line.",
                        worst.getTopicName(), worst.getAccuracyPercent().doubleValue(),
                        worst.getAttemptsCount(), WEAK_TOPIC_THRESHOLD));
            }
        }

        // 5. Unresolved mistakes - the cheapest available score improvement.
        if (e.mistakeCounts() != null && e.mistakeCounts().unresolved() > 0) {
            why.add(String.format(Locale.ROOT,
                    "[Mistake Log] %d of your %d logged mistake(s) are still unresolved and keep re-appearing in your weak-topic profile.",
                    e.mistakeCounts().unresolved(), e.mistakeCounts().total()));
        }

        // 6. Coverage gaps - what the system cannot yet score.
        if (e.completedMockInterviews() == 0 && e.completedAssessments() > 0) {
            why.add("[Coverage] You have practised technically but not yet completed a mock interview, "
                    + "so your communication and behavioral dimensions are still unscored.");
        }
        if (e.completedAssessments() == 0 && e.resumeSkills() != null && !e.resumeSkills().isEmpty()) {
            why.add("[Coverage] No completed assessments yet, so every skill on your resume is currently "
                    + "unverified by measured performance.");
        }

        return why;
    }

    /** Concrete, ordered next steps - each one a screen the user can actually open. */
    static List<String> buildWhatNext(Evidence e) {
        List<String> steps = new ArrayList<>();

        if (e.resumeSkills() == null || e.resumeSkills().isEmpty()) {
            steps.add("Open \"Resume & Skills (AI Analysis)\" and upload a PDF/DOCX/TXT resume.");
        }
        if (e.jobMatchScore() == null) {
            steps.add("Paste a target job description under \"Resume & Skills\" to unlock the Job Match breakdown.");
        }

        if (e.weakTopics() != null && !e.weakTopics().isEmpty()) {
            String topics = joinTop(e.weakTopics().stream().map(TopicPerformance::getTopicName).toList(), 2);
            steps.add("Start an adaptive assessment focused on " + topics + " - the system will pre-select these topics.");
        }
        if (e.mistakeCounts() != null && e.mistakeCounts().unresolved() > 0) {
            steps.add("Run \"Mistake Analyzer & Retry\" to clear your " + e.mistakeCounts().unresolved()
                    + " unresolved mistake(s); each resolved mistake feeds straight back into your readiness score.");
        }
        if (e.missingSkills() != null && !e.missingSkills().isEmpty()) {
            steps.add("Work the roadmap items covering " + joinTop(e.missingSkills(), 2)
                    + ", then re-run the Job Match to see the score move.");
        }
        if (e.completedMockInterviews() == 0) {
            steps.add("Complete one AI mock interview to score your Communication and Behavioral dimensions.");
        }
        if (e.roadmapTotalItems() > 0 && e.roadmapCompletedItems() < e.roadmapTotalItems()) {
            steps.add(String.format(Locale.ROOT,
                    "Mark roadmap items complete as you finish them (%d of %d done) to keep recommendations current.",
                    e.roadmapCompletedItems(), e.roadmapTotalItems()));
        }

        if (steps.isEmpty()) {
            steps.add("You have cleared every open gap the system can currently see - "
                    + "re-run a mock interview at a higher difficulty to keep pushing your readiness band up.");
        }
        return steps.size() > 5 ? new ArrayList<>(steps.subList(0, 5)) : steps;
    }

    /**
     * Explains a single roadmap item: why this specific item is on the
     * list, using the gap that produced it.
     */
    public static String explainRoadmapItem(String itemTitle, String relatedTopic, Double topicAccuracy,
                                             boolean requiredByJob, boolean onResume) {
        StringBuilder sb = new StringBuilder();
        sb.append('"').append(itemTitle).append("\" is on your roadmap because ");
        List<String> reasons = new ArrayList<>();
        if (requiredByJob) {
            reasons.add("it is a required skill in your target job description");
        }
        if (topicAccuracy != null && topicAccuracy < WEAK_TOPIC_THRESHOLD && relatedTopic != null) {
            reasons.add(String.format(Locale.ROOT, "your measured accuracy in %s is %.1f%%", relatedTopic, topicAccuracy));
        }
        if (onResume && topicAccuracy != null && topicAccuracy < WEAK_TOPIC_THRESHOLD) {
            reasons.add("you claim it on your resume, so weak performance there is a credibility risk");
        } else if (!onResume && requiredByJob) {
            reasons.add("it does not appear anywhere on your resume");
        }
        if (reasons.isEmpty()) {
            reasons.add("it strengthens a dimension the readiness engine currently scores lowest");
        }
        sb.append(String.join("; ", reasons)).append('.');
        return sb.toString();
    }

    /** The lowest-scoring readiness dimension, or {@code null} when no dimensions are populated. */
    static ReadinessScore.Component weakestDimension(ReadinessScore readiness) {
        if (readiness == null || readiness.getDimensions() == null || readiness.getDimensions().isEmpty()) {
            return null;
        }
        return readiness.getDimensions().values().stream()
                .min(Comparator.comparingDouble(ReadinessScore.Component::getValue))
                .orElse(null);
    }

    /** The highest-scoring readiness dimension, or {@code null} when no dimensions are populated. */
    static ReadinessScore.Component strongestDimension(ReadinessScore readiness) {
        if (readiness == null || readiness.getDimensions() == null || readiness.getDimensions().isEmpty()) {
            return null;
        }
        return readiness.getDimensions().values().stream()
                .max(Comparator.comparingDouble(ReadinessScore.Component::getValue))
                .orElse(null);
    }

    /** Joins the first {@code limit} entries, appending "and N more" when the list is longer. */
    static String joinTop(List<String> values, int limit) {
        if (values == null || values.isEmpty()) {
            return "";
        }
        List<String> head = values.size() <= limit ? values : values.subList(0, limit);
        String joined = String.join(", ", head);
        int remaining = values.size() - head.size();
        return remaining > 0 ? joined + " and " + remaining + " more" : joined;
    }

    /** Maps a readiness dimension key to a human label, matching the dashboard's wording. */
    public static String dimensionLabel(Map.Entry<String, ReadinessScore.Component> entry) {
        return entry.getValue().getLabel() != null ? entry.getValue().getLabel() : entry.getKey();
    }
}
