package com.careerintelligence.service;

import com.careerintelligence.dao.JobMatchResultDAO;
import com.careerintelligence.dao.MistakeLogDAO;
import com.careerintelligence.dao.RoadmapDAO;
import com.careerintelligence.dao.TopicPerformanceDAO;
import com.careerintelligence.model.LearningRoadmap;
import com.careerintelligence.model.MistakeEntry;
import com.careerintelligence.model.ReadinessScore;
import com.careerintelligence.model.RoadmapCategory;
import com.careerintelligence.model.RoadmapItem;
import com.careerintelligence.model.RoadmapItemStatus;
import com.careerintelligence.model.RoadmapPriority;
import com.careerintelligence.model.TopicPerformance;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Business logic for the Personalised Learning Roadmap. Generates a
 * concrete, ordered, actionable preparation plan
 * for a user by combining signals every other feature already tracks -
 * resume skills / missing skills (service.ResumeService), weak topics and
 * repeated mistakes (service.MistakeAnalyzerService, dao.MistakeLogDAO),
 * raw assessment/topic performance (dao.TopicPerformanceDAO), mock
 * interview usage, and the blended Interview Readiness Score
 * (service.ReadinessScoreService). No new AI calls are required - the
 * roadmap is a deterministic, explainable synthesis of data the app
 * already has, which also means it never fails/hangs on an unavailable
 * live AI backend.
 */
public class RoadmapService {

    private static final int MAX_ITEMS = 10;

    private final ResumeService resumeService = new ResumeService();
    private final MistakeAnalyzerService mistakeAnalyzerService = new MistakeAnalyzerService();
    private final MistakeLogDAO mistakeLogDAO = new MistakeLogDAO();
    private final TopicPerformanceDAO topicPerformanceDAO = new TopicPerformanceDAO();
    private final ReadinessScoreService readinessScoreService = new ReadinessScoreService();
    private final RoadmapDAO roadmapDAO = new RoadmapDAO();
    private final JobMatchResultDAO jobMatchResultDAO = new JobMatchResultDAO();

    /**
     * Generates a brand-new roadmap for the user (replacing, not merging
     * with, any earlier one - {@link #getLatest} always returns the most
     * recently generated plan) and persists it.
     */
    public LearningRoadmap generate(Long userId) throws SQLException {
        ReadinessScore readiness = readinessScoreService.compute(userId);

        List<RoadmapItem> items = new ArrayList<>();
        int order = 0;

        // 1. Skill gaps: required-for-role skills missing from the resume.
        List<String> missingSkills = resumeService.getMissingSkills(userId);
        for (String skill : missingSkills) {
            if (items.size() >= MAX_ITEMS) break;
            RoadmapItem item = new RoadmapItem();
            item.setItemOrder(++order);
            item.setCategory(RoadmapCategory.SKILL_GAP);
            item.setPriority(RoadmapPriority.HIGH);
            item.setTitle("Close skill gap: " + skill);
            item.setDescription("Your target role expects " + skill + ", but it wasn't found on your resume. "
                    + "Add relevant project/work experience with " + skill + ", or start practicing it.");
            items.add(item);
        }

        // 2. Weak skills: resume skills the user claims but performs poorly on in assessments.
        List<String> weakSkills = resumeService.getWeakSkills(userId);
        for (String skill : weakSkills) {
            if (items.size() >= MAX_ITEMS) break;
            RoadmapItem item = new RoadmapItem();
            item.setItemOrder(++order);
            item.setCategory(RoadmapCategory.SKILL_GAP);
            item.setPriority(RoadmapPriority.MEDIUM);
            item.setTitle("Strengthen claimed skill: " + skill);
            item.setDescription("Your resume lists " + skill + ", but your assessment accuracy on it is below 60%. "
                    + "Revisit the fundamentals and retake a topic assessment.");
            items.add(item);
        }

        // 2b. Adaptive Career Intelligence: fold in the latest Resume + JD match's highest-priority
        // gaps too, if the user has analysed a job description (service.ResumeService#analyzeAndMatch).
        // This is what actually connects "Resume + JD -> Skill Gaps -> Roadmap": whatever that match
        // flagged as missing/weak-but-required feeds straight into the same roadmap the rest of the
        // pipeline (Assessment -> Mistakes -> Readiness -> Next Best Action) already consumes.
        int jdGapsAdded = 0;
        Optional<JobMatchResultDAO.Row> latestMatch = jobMatchResultDAO.findLatestByUser(userId);
        if (latestMatch.isPresent()) {
            JobMatchResultDAO.Row match = latestMatch.get();
            Set<String> alreadyCovered = new HashSet<>();
            for (String s : missingSkills) alreadyCovered.add(s.toLowerCase());
            for (String s : weakSkills) alreadyCovered.add(s.toLowerCase());
            List<JdGapItem> jdGapItems = buildJdGapItems(match.highPriority(), match.missing(), alreadyCovered,
                    MAX_ITEMS - items.size());
            for (JdGapItem gap : jdGapItems) {
                RoadmapItem item = new RoadmapItem();
                item.setItemOrder(++order);
                item.setCategory(RoadmapCategory.SKILL_GAP);
                item.setPriority(RoadmapPriority.HIGH);
                item.setTitle("Job description needs: " + gap.skill());
                item.setDescription(gap.missingFromResume()
                        ? "The job description you analysed requires " + gap.skill()
                            + ", but it wasn't found on your resume. Add it or start practicing it now."
                        : "The job description you analysed requires " + gap.skill()
                            + ", and it's on your resume, but your assessment accuracy on it is weak. Reinforce it before you apply.");
                items.add(item);
                jdGapsAdded++;
            }
        }

        // 3. Weak topics from topic_performance (accuracy-based, independent of resume).
        List<TopicPerformance> weakTopics = mistakeAnalyzerService.getWeakTopics(userId);
        for (TopicPerformance tp : weakTopics) {
            if (items.size() >= MAX_ITEMS) break;
            RoadmapItem item = new RoadmapItem();
            item.setItemOrder(++order);
            item.setCategory(RoadmapCategory.WEAK_TOPIC);
            item.setPriority(tp.getAccuracyPercent().doubleValue() < 40 ? RoadmapPriority.HIGH : RoadmapPriority.MEDIUM);
            item.setTitle("Practice weak topic: " + tp.getTopicName());
            item.setDescription(String.format(
                    "Your accuracy on %s is %.1f%% across %d attempt(s). Take a focused assessment on this topic.",
                    tp.getTopicName(), tp.getAccuracyPercent().doubleValue(), tp.getAttemptsCount()));
            item.setRelatedTopicId(tp.getTopicId());
            item.setRelatedTopicName(tp.getTopicName());
            items.add(item);
        }

        // 4. Repeated mistakes: questions missed 2+ times, grouped by topic.
        List<MistakeEntry> repeated = mistakeAnalyzerService.getRepeatedMistakes(userId);
        MistakeLogDAO.MistakeCounts mistakeCounts = mistakeLogDAO.countsByUser(userId);
        if (!repeated.isEmpty() && items.size() < MAX_ITEMS) {
            RoadmapItem item = new RoadmapItem();
            item.setItemOrder(++order);
            item.setCategory(RoadmapCategory.MISTAKE_REVIEW);
            item.setPriority(RoadmapPriority.HIGH);
            item.setTitle("Resolve " + repeated.size() + " repeated mistake(s)");
            item.setDescription("You have " + repeated.size() + " question(s) missed 2 or more times, out of "
                    + mistakeCounts.unresolved() + " unresolved mistake(s) overall. Use Mistake Analyzer & Retry "
                    + "to work through them.");
            items.add(item);
        }

        // 5. Mock interview practice, if the readiness score didn't include this component (i.e. never taken).
        if (items.size() < MAX_ITEMS && !readiness.getComponents().containsKey("mock")) {
            RoadmapItem item = new RoadmapItem();
            item.setItemOrder(++order);
            item.setCategory(RoadmapCategory.MOCK_PRACTICE);
            item.setPriority(RoadmapPriority.MEDIUM);
            item.setTitle("Take your first AI Mock Interview");
            item.setDescription("You haven't completed a mock interview yet. It's the best way to practice "
                    + "verbalising answers under light pressure and get AI feedback on open-ended questions.");
            items.add(item);
        }

        // 6. Overall readiness nudge.
        if (items.size() < MAX_ITEMS) {
            RoadmapItem item = new RoadmapItem();
            item.setItemOrder(++order);
            item.setCategory(RoadmapCategory.ROLE_READINESS);
            item.setPriority(readiness.getOverallScore() < 60 ? RoadmapPriority.HIGH : RoadmapPriority.LOW);
            item.setTitle("Reach an Interview Ready score");
            item.setDescription(String.format(
                    "Your current Interview Readiness Score is %.1f/100 (%s). Work through the items above and "
                            + "recompute your score to track progress.",
                    readiness.getOverallScore(), readiness.getBand()));
            items.add(item);
        }

        if (items.isEmpty()) {
            RoadmapItem item = new RoadmapItem();
            item.setItemOrder(1);
            item.setCategory(RoadmapCategory.GENERAL);
            item.setPriority(RoadmapPriority.LOW);
            item.setTitle("Keep up the momentum");
            item.setDescription("No specific gaps detected right now - keep taking assessments and mock "
                    + "interviews regularly to maintain your readiness.");
            items.add(item);
        }

        LearningRoadmap roadmap = new LearningRoadmap();
        roadmap.setUserId(userId);
        roadmap.setReadinessScore(readiness.getOverallScore());
        roadmap.setReadinessBand(readiness.getBand());
        roadmap.setSummary(buildSummary(readiness, missingSkills.size(), weakSkills.size(), weakTopics.size(),
                repeated.size(), jdGapsAdded));
        roadmap.setItems(items);

        return roadmapDAO.save(roadmap);
    }

    private String buildSummary(ReadinessScore readiness, int missingSkillCount, int weakSkillCount,
                                 int weakTopicCount, int repeatedMistakeCount, int jdGapCount) {
        String jdClause = jdGapCount > 0
                ? String.format(" %d gap(s) prioritised from your latest job description match.", jdGapCount)
                : "";
        return String.format(
                "Readiness: %.1f/100 (%s). %d missing skill(s) for your target role, %d claimed-but-weak skill(s), "
                        + "%d weak topic(s), and %d repeated mistake(s) identified.%s Focus on the HIGH priority items first.",
                readiness.getOverallScore(), readiness.getBand(), missingSkillCount, weakSkillCount,
                weakTopicCount, repeatedMistakeCount, jdClause);
    }

    public Optional<LearningRoadmap> getLatest(Long userId) throws SQLException {
        return roadmapDAO.findLatestByUser(userId);
    }

    public void markItemComplete(Long itemId, boolean complete) throws SQLException {
        roadmapDAO.markItemStatus(itemId, complete ? RoadmapItemStatus.COMPLETED : RoadmapItemStatus.PENDING);
    }

    /** One prioritised JD-driven skill gap to surface on the roadmap: which skill, and whether it's absent from the resume entirely vs. present-but-weak. */
    record JdGapItem(String skill, boolean missingFromResume) {
    }

    /**
     * Pure helper (no DB) behind step 2b of {@link #generate}: turns a Resume
     * + JD match's already-ranked {@code highPriority} skill list into
     * roadmap-ready gap items, skipping anything already covered by a
     * role-based gap/weak-skill item so the roadmap doesn't repeat itself,
     * and respecting the remaining item budget.
     */
    static List<JdGapItem> buildJdGapItems(List<String> highPriority, List<String> missing,
                                            Set<String> alreadyCoveredLower, int maxItems) {
        List<JdGapItem> result = new ArrayList<>();
        if (highPriority == null || maxItems <= 0) {
            return result;
        }
        Set<String> covered = new HashSet<>(alreadyCoveredLower);
        Set<String> missingLower = new HashSet<>();
        if (missing != null) {
            for (String s : missing) missingLower.add(s.toLowerCase());
        }
        for (String skill : highPriority) {
            if (result.size() >= maxItems) break;
            String lower = skill.toLowerCase();
            if (!covered.add(lower)) continue;
            result.add(new JdGapItem(skill, missingLower.contains(lower)));
        }
        return result;
    }
}
