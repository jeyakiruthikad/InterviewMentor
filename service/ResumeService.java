package com.careerintelligence.service;

import com.careerintelligence.ai.AIService;
import com.careerintelligence.ai.AIServiceImpl;
import com.careerintelligence.ai.SkillTaxonomy;
import com.careerintelligence.dao.AssessmentDAO;
import com.careerintelligence.dao.JobDescriptionDAO;
import com.careerintelligence.dao.JobMatchResultDAO;
import com.careerintelligence.dao.MockInterviewDAO;
import com.careerintelligence.dao.ResumeSkillDAO;
import com.careerintelligence.dao.RoleSkillRequirementDAO;
import com.careerintelligence.dao.TopicDAO;
import com.careerintelligence.dao.TopicPerformanceDAO;
import com.careerintelligence.dao.UserProfileDAO;
import com.careerintelligence.model.JobDescription;
import com.careerintelligence.model.JobMatchStrength;
import com.careerintelligence.model.MockInterviewSession;
import com.careerintelligence.model.OverallStats;
import com.careerintelligence.model.ReadinessScore;
import com.careerintelligence.model.ResumeSkill;
import com.careerintelligence.model.Topic;
import com.careerintelligence.model.TopicPerformance;
import com.careerintelligence.model.UserProfile;
import com.careerintelligence.util.ResumeParser;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Business logic for the Resume AI Analysis and Resume-Based Questions
 * features (current implementation): uploading and parsing a resume, extracting and
 * categorising skills via {@link AIServiceImpl}, persisting them, and
 * identifying missing skills (vs. a target role) and weak skills (vs. the
 * existing topic_performance data from the assessment engine).
 */
public class ResumeService {

    /** Where uploaded resumes are copied to, relative to the project's working directory. */
    private static final String RESUME_STORAGE_DIR = "resumes";
    private static final double WEAK_TOPIC_ACCURACY_THRESHOLD = 60.0;

    private final ResumeSkillDAO resumeSkillDAO = new ResumeSkillDAO();
    private final RoleSkillRequirementDAO roleSkillRequirementDAO = new RoleSkillRequirementDAO();
    private final TopicDAO topicDAO = new TopicDAO();
    private final TopicPerformanceDAO topicPerformanceDAO = new TopicPerformanceDAO();
    private final UserProfileDAO userProfileDAO = new UserProfileDAO();
    private final AIServiceImpl aiService = new AIServiceImpl();
    private final JobDescriptionDAO jobDescriptionDAO = new JobDescriptionDAO();
    private final JobMatchResultDAO jobMatchResultDAO = new JobMatchResultDAO();
    private final AssessmentDAO assessmentDAO = new AssessmentDAO();
    private final MockInterviewDAO mockInterviewDAO = new MockInterviewDAO();

    /** Result of a resume upload + analysis, everything the UI needs to display in one shot. */
    public record AnalysisResult(String resumePath, List<ResumeSkill> skills, int charactersExtracted) {
    }

    /**
     * Reads and parses the resume at {@code sourceFilePath}, extracts and
     * categorises skills, stores a copy of the file plus the structured
     * skill set in MySQL, and returns everything needed to display the
     * result. Never leaves partial/stale data: the DB write only happens
     * once parsing and extraction have both succeeded.
     */
    public AnalysisResult uploadAndAnalyze(Long userId, String sourceFilePath) throws SQLException, IOException {
        Path source = Paths.get(sourceFilePath.trim().replaceAll("^\"|\"$", ""));
        if (!Files.exists(source) || !Files.isRegularFile(source)) {
            throw new IOException("File not found: " + source.toAbsolutePath());
        }

        String text = ResumeParser.extractText(source);
        if (text == null || text.isBlank()) {
            throw new IOException("The file was read but no text content was found in it.");
        }

        Path storedPath = copyIntoResumeStorage(userId, source);

        List<String> extractedSkillNames = aiService.extractSkillsFromResume(text);
        List<ResumeSkill> resumeSkills = new ArrayList<>();
        for (String skillName : extractedSkillNames) {
            ResumeSkill skill = new ResumeSkill();
            skill.setUserId(userId);
            skill.setSkillName(skillName);
            Optional<SkillTaxonomy.SkillDef> def = SkillTaxonomy.byCanonicalName(skillName);
            skill.setCategory(def.map(SkillTaxonomy.SkillDef::category).orElse("Other"));
            if (def.isPresent() && def.get().topicNameHint() != null) {
                Optional<Topic> topic = topicDAO.findByName(def.get().topicNameHint());
                topic.ifPresent(t -> skill.setMatchedTopicId(t.getTopicId()));
            }
            resumeSkills.add(skill);
        }

        resumeSkillDAO.replaceForUser(userId, resumeSkills);
        String skillsCsv = extractedSkillNames.stream().sorted(String.CASE_INSENSITIVE_ORDER)
                .collect(Collectors.joining(", "));
        userProfileDAO.updateResumeInfo(userId, storedPath.toString(), skillsCsv);

        return new AnalysisResult(storedPath.toString(), resumeSkillDAO.findByUser(userId), text.length());
    }

    private Path copyIntoResumeStorage(Long userId, Path source) throws IOException {
        Path dir = Paths.get(RESUME_STORAGE_DIR);
        Files.createDirectories(dir);
        String extension = "";
        String fileName = source.getFileName().toString();
        int dot = fileName.lastIndexOf('.');
        if (dot >= 0) {
            extension = fileName.substring(dot);
        }
        String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        Path target = dir.resolve("user" + userId + "_" + stamp + extension);
        Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
        return target;
    }

    public List<ResumeSkill> getExtractedSkills(Long userId) throws SQLException {
        return resumeSkillDAO.findByUser(userId);
    }

    /** Skills required for the user's target role (from user_profiles.target_role) that were NOT found in their resume. */
    public List<String> getMissingSkills(Long userId) throws SQLException {
        Optional<UserProfile> profile = userProfileDAO.findByUserId(userId);
        if (profile.isEmpty() || profile.get().getTargetRole() == null || profile.get().getTargetRole().isBlank()) {
            return List.of();
        }
        List<String> required = roleSkillRequirementDAO.findSkillsForRole(profile.get().getTargetRole());
        if (required.isEmpty()) {
            return List.of();
        }
        Set<String> extracted = resumeSkillDAO.findByUser(userId).stream()
                .map(s -> s.getSkillName().toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());
        return required.stream()
                .filter(skill -> !extracted.contains(skill.toLowerCase(Locale.ROOT)))
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .collect(Collectors.toList());
    }

    /**
     * Extracted resume skills whose matched topic has a recorded accuracy
     * below {@link #WEAK_TOPIC_ACCURACY_THRESHOLD}% in topic_performance
     * (i.e. skills the user claims but has struggled with in assessments).
     * Skills with no matched topic, or no assessment attempts yet on that
     * topic, are not flagged (there isn't enough evidence either way).
     */
    public List<String> getWeakSkills(Long userId) throws SQLException {
        List<ResumeSkill> skills = resumeSkillDAO.findByUser(userId);
        if (skills.isEmpty()) {
            return List.of();
        }
        Map<Integer, TopicPerformance> performanceByTopic = topicPerformanceDAO.findAllByUser(userId).stream()
                .collect(Collectors.toMap(TopicPerformance::getTopicId, tp -> tp, (a, b) -> a));

        List<String> weak = new ArrayList<>();
        for (ResumeSkill skill : skills) {
            if (skill.getMatchedTopicId() == null) {
                continue;
            }
            TopicPerformance tp = performanceByTopic.get(skill.getMatchedTopicId());
            if (tp != null && tp.getAttemptsCount() > 0
                    && tp.getAccuracyPercent().doubleValue() < WEAK_TOPIC_ACCURACY_THRESHOLD) {
                weak.add(skill.getSkillName());
            }
        }
        return weak;
    }

    /** Distinct topic IDs that at least one of the user's extracted skills matched onto (for resume-based assessments). */
    public List<Integer> getMatchedTopicIds(Long userId) throws SQLException {
        return resumeSkillDAO.findByUser(userId).stream()
                .map(ResumeSkill::getMatchedTopicId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
    }

    /** Extracted skills with no matched question-bank topic - candidates for AI-generated (not DB) practice questions. */
    public List<String> getUnmatchedSkills(Long userId) throws SQLException {
        return resumeSkillDAO.findByUser(userId).stream()
                .filter(s -> s.getMatchedTopicId() == null)
                .map(ResumeSkill::getSkillName)
                .collect(Collectors.toList());
    }

    /** Recommended (non-graded) study questions for skills the question bank doesn't cover yet. */
    public List<String> suggestQuestionsForUnmatchedSkills(Long userId, int count) throws SQLException {
        List<String> unmatched = getUnmatchedSkills(userId);
        if (unmatched.isEmpty()) {
            return List.of();
        }
        return aiService.generateQuestionsForSkills(unmatched, count);
    }

    public List<String> getAvailableTargetRoles() throws SQLException {
        return roleSkillRequirementDAO.findAllRoleNames();
    }

    // -----------------------------------------------------------------
    // Resume-Role Match % (Career Intelligence deep-linking)
    // -----------------------------------------------------------------

    /** One prioritised gap the roadmap/dashboard should surface first - either a missing or a weak skill. */
    public record PriorityGap(String skillName, String reason, int priorityScore) {
    }

    /** Full Resume vs. Target-Role match result: match %, matched/missing skills, and ranked priority gaps. */
    public record RoleMatch(String targetRole, double matchPercentage, List<String> matchedSkills,
                             List<String> missingSkills, List<PriorityGap> priorityGaps) {

        public static RoleMatch empty(String targetRole) {
            return new RoleMatch(targetRole, 0.0, List.of(), List.of(), List.of());
        }
    }

    /**
     * Computes how well the user's resume matches their target role: the
     * match percentage (required skills actually found on the resume),
     * the matched and missing skill lists, and a ranked list of "priority
     * gaps" - missing skills (always most urgent) and weak claimed skills
     * (present but assessed under 60% accuracy), ordered by a priority
     * score that combines the role taxonomy's per-skill importance
     * (role_skill_requirements.priority_weight) with how weak the
     * evidence actually is. This is what powers the Learning Roadmap's
     * and Dashboard's "what should I fix first" recommendation - not a
     * new data source, just a scored, ranked view of resume + role +
     * assessment-accuracy data every other feature already collects.
     */
    public RoleMatch computeRoleMatch(Long userId) throws SQLException {
        Optional<UserProfile> profile = userProfileDAO.findByUserId(userId);
        String targetRole = profile.map(UserProfile::getTargetRole)
                .filter(r -> r != null && !r.isBlank())
                .orElse(null);
        if (targetRole == null) {
            return RoleMatch.empty(null);
        }

        List<RoleSkillRequirementDAO.Requirement> requirements;
        try {
            requirements = roleSkillRequirementDAO.findRequirementsForRole(targetRole);
        } catch (SQLException e) {
            // degrade gracefully to the pre-existing flat skill list with a default weight, rather
            // than breaking the feature on an un-migrated database.
            requirements = roleSkillRequirementDAO.findSkillsForRole(targetRole).stream()
                    .map(name -> new RoleSkillRequirementDAO.Requirement(name, "General", 5))
                    .collect(Collectors.toList());
        }
        if (requirements.isEmpty()) {
            return RoleMatch.empty(targetRole);
        }

        List<ResumeSkill> resumeSkills = resumeSkillDAO.findByUser(userId);
        Set<String> extracted = resumeSkills.stream()
                .map(s -> s.getSkillName().toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());
        Map<Integer, TopicPerformance> performanceByTopic = topicPerformanceDAO.findAllByUser(userId).stream()
                .collect(Collectors.toMap(TopicPerformance::getTopicId, tp -> tp, (a, b) -> a));
        Map<String, Integer> matchedTopicBySkill = resumeSkills.stream()
                .filter(s -> s.getMatchedTopicId() != null)
                .collect(Collectors.toMap(s -> s.getSkillName().toLowerCase(Locale.ROOT),
                        ResumeSkill::getMatchedTopicId, (a, b) -> a));

        return computeRoleMatchPure(targetRole, requirements, extracted, matchedTopicBySkill, performanceByTopic);
    }

    /**
     * Pure computation core of {@link #computeRoleMatch}, factored out so the
     * match %/ranking logic itself is unit-testable without a database (see
     * {@code ResumeServiceRoleMatchTest}). Package-private + static, mirroring
     * how {@code CareerIntelligenceService.mergeUnique} is tested.
     */
    static RoleMatch computeRoleMatchPure(String targetRole, List<RoleSkillRequirementDAO.Requirement> requirements,
                                           Set<String> extractedSkillsLower,
                                           Map<String, Integer> matchedTopicBySkillLower,
                                           Map<Integer, TopicPerformance> performanceByTopic) {
        List<String> matched = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        List<PriorityGap> gaps = new ArrayList<>();

        for (RoleSkillRequirementDAO.Requirement req : requirements) {
            String lower = req.skillName().toLowerCase(Locale.ROOT);
            if (!extractedSkillsLower.contains(lower)) {
                missing.add(req.skillName());
                int score = 100 + req.priorityWeight() * 5; // missing always outranks a merely-weak skill
                gaps.add(new PriorityGap(req.skillName(),
                        "Required for " + targetRole + " but not found on your resume.", score));
                continue;
            }
            matched.add(req.skillName());

            Integer topicId = matchedTopicBySkillLower.get(lower);
            TopicPerformance tp = topicId == null ? null : performanceByTopic.get(topicId);
            if (tp != null && tp.getAttemptsCount() > 0 && tp.getAccuracyPercent().doubleValue() < WEAK_TOPIC_ACCURACY_THRESHOLD) {
                double accuracy = tp.getAccuracyPercent().doubleValue();
                int score = (int) Math.round(50 + (WEAK_TOPIC_ACCURACY_THRESHOLD - accuracy) + req.priorityWeight());
                gaps.add(new PriorityGap(req.skillName(),
                        String.format(Locale.ROOT, "On your resume, but assessment accuracy is only %.1f%%.", accuracy),
                        score));
            }
        }

        gaps.sort(Comparator.comparingInt(PriorityGap::priorityScore).reversed());
        double matchPercentage = requirements.isEmpty() ? 0.0
                : Math.round(matched.size() * 10000.0 / requirements.size()) / 100.0;

        return new RoleMatch(targetRole, matchPercentage, matched, missing, gaps);
    }

    // -----------------------------------------------------------------
    // Job Description Analysis (free-text JD, as opposed to the fixed
    // role_skill_requirements taxonomy used by computeRoleMatch above)
    // -----------------------------------------------------------------

    /**
     * Analyses a pasted job description via {@link AIService#analyzeJobDescription}
     * (live LLM with local-heuristic fallback, same pattern as resume skill
     * extraction) and persists the structured result to `job_descriptions`.
     */
    public JobDescription analyzeJobDescription(Long userId, String jobDescriptionText) throws SQLException {
        AIService.JobDescriptionExtraction extraction = aiService.analyzeJobDescription(jobDescriptionText);

        JobDescription jd = new JobDescription();
        jd.setUserId(userId);
        jd.setTitle(extraction.title());
        jd.setRawText(jobDescriptionText);
        jd.setRequiredSkills(extraction.requiredSkills());
        jd.setPreferredSkills(extraction.preferredSkills());
        jd.setTechnologies(extraction.technologies());
        jd.setResponsibilities(extraction.responsibilities());
        jd.setSoftSkills(extraction.softSkills());
        jd.setExperienceRequired(extraction.experienceRequired());
        jd.setAnalyzedAt(LocalDateTime.now());

        return jobDescriptionDAO.save(jd);
    }

    public Optional<JobDescription> getLatestJobDescription(Long userId) throws SQLException {
        return jobDescriptionDAO.findLatestByUser(userId);
    }

    // -----------------------------------------------------------------
    // Resume + JD Matching
    // -----------------------------------------------------------------

    /** One JD skill's explainable match evidence: which skill, how strong the evidence is, and why. */
    public record JdSkillEvidence(String skillName, JobMatchStrength strength, boolean requiredByJd, String reason) {
    }

    /**
     * Full Resume + JD match result: an explainable 0-100 score, the
     * per-skill breakdown (Strong Match / Partial Match / Weak Evidence /
     * Missing), the ranked list of skills to prioritise first (missing
     * required skills, then weak-evidence required skills, then missing
     * preferred skills), and the weighted component breakdown behind the
     * score (see {@link #matchResumeToJobDescriptionPure}) - the Job Match
     * Score feature. {@code breakdown} reuses {@link ReadinessScore.Component}
     * (label/value/weight/note) - the same explainable-scoring shape
     * {@code ReadinessScoreService} already uses - so every point in
     * {@code matchScore} traces back to a named, human-readable signal
     * instead of being an opaque number.
     */
    public record JobMatchResult(Long jobDescriptionId, double matchScore, List<JdSkillEvidence> strongMatches,
                                  List<JdSkillEvidence> partialMatches, List<JdSkillEvidence> weakEvidence,
                                  List<JdSkillEvidence> missing, List<String> highPrioritySkills, String summary,
                                  List<ReadinessScore.Component> breakdown) {

        public JobMatchResult(Long jobDescriptionId, double matchScore, List<JdSkillEvidence> strongMatches,
                               List<JdSkillEvidence> partialMatches, List<JdSkillEvidence> weakEvidence,
                               List<JdSkillEvidence> missing, List<String> highPrioritySkills, String summary) {
            this(jobDescriptionId, matchScore, strongMatches, partialMatches, weakEvidence, missing,
                    highPrioritySkills, summary, List.of());
        }

        public static JobMatchResult empty() {
            return new JobMatchResult(null, 0.0, List.of(), List.of(), List.of(), List.of(), List.of(),
                    "No job description has been analysed yet.", List.of());
        }
    }

    /**
     * Computes the explainable Job Match Score between the user's resume
     * (extracted skills + assessment performance, exactly the same signals
     * {@link #computeRoleMatch} already uses for a fixed role) and a
     * previously-analysed free-text job description, and persists the
     * result as an audit snapshot in `job_match_results`.
     */
    public JobMatchResult matchResumeToJobDescription(Long userId, JobDescription jd) throws SQLException {
        List<ResumeSkill> resumeSkills = resumeSkillDAO.findByUser(userId);
        Set<String> extracted = resumeSkills.stream()
                .map(s -> s.getSkillName().toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());
        Map<String, Integer> matchedTopicBySkill = resumeSkills.stream()
                .filter(s -> s.getMatchedTopicId() != null)
                .collect(Collectors.toMap(s -> s.getSkillName().toLowerCase(Locale.ROOT),
                        ResumeSkill::getMatchedTopicId, (a, b) -> a));
        Map<Integer, TopicPerformance> performanceByTopic = topicPerformanceDAO.findAllByUser(userId).stream()
                .collect(Collectors.toMap(TopicPerformance::getTopicId, tp -> tp, (a, b) -> a));

        // Additional signals the pure scorer can optionally blend in when real data exists for
        // them (candidate experience level, overall assessment performance, mock interview
        // performance) - each is left null/empty when the user hasn't produced that data yet,
        // exactly the "only include a component once there's real evidence for it" pattern
        // ReadinessScoreService already uses, so a signal the user hasn't touched yet never
        // silently drags the score down (or up) on invented evidence.
        String candidateExperienceLevel = userProfileDAO.findByUserId(userId)
                .map(UserProfile::getExperienceLevel).orElse(null);

        OverallStats stats = assessmentDAO.getOverallStats(userId);
        Double overallAssessmentAveragePercent = stats.getTotalAssessments() > 0
                ? stats.getAverageScorePercent() : null;

        List<MockInterviewSession> recentMocks = mockInterviewDAO.findRecentCompleted(userId, 5);
        OptionalDouble avgMock = recentMocks.stream()
                .filter(s -> s.getAverageScore() != null)
                .mapToDouble(s -> s.getAverageScore().doubleValue())
                .average();
        Double averageInterviewScorePercent = avgMock.isPresent() ? avgMock.getAsDouble() : null;

        JobMatchResult result = matchResumeToJobDescriptionPure(jd.getRequiredSkills(), jd.getPreferredSkills(),
                extracted, matchedTopicBySkill, performanceByTopic, jd.getTechnologies(),
                jd.getExperienceRequired(), candidateExperienceLevel, overallAssessmentAveragePercent,
                averageInterviewScorePercent);
        result = new JobMatchResult(jd.getId(), result.matchScore(), result.strongMatches(), result.partialMatches(),
                result.weakEvidence(), result.missing(), result.highPrioritySkills(), result.summary(),
                result.breakdown());

        jobMatchResultDAO.save(jd.getId(), userId, result.matchScore(),
                names(result.strongMatches()), names(result.partialMatches()), names(result.weakEvidence()),
                names(result.missing()), result.highPrioritySkills(), result.summary());

        return result;
    }

    private static List<String> names(List<JdSkillEvidence> evidence) {
        return evidence.stream().map(JdSkillEvidence::skillName).collect(Collectors.toList());
    }

    /** Convenience: analyses + matches in one call for a fresh JD paste, without the caller needing two round-trips. */
    public JobMatchResult analyzeAndMatch(Long userId, String jobDescriptionText) throws SQLException {
        JobDescription jd = analyzeJobDescription(userId, jobDescriptionText);
        return matchResumeToJobDescription(userId, jd);
    }

    public Optional<JobMatchResultDAO.Row> getLatestJobMatch(Long userId) throws SQLException {
        return jobMatchResultDAO.findLatestByUser(userId);
    }

    /** Accuracy threshold (assessment evidence on a JD skill's matched topic) above which a resume claim becomes a Strong, not merely Partial, Match. */
    private static final double STRONG_MATCH_ACCURACY_THRESHOLD = 75.0;

    /** Component weights used when their underlying signal is actually available (see the richer {@link #matchResumeToJobDescriptionPure} overload below). */
    private static final int WEIGHT_SKILL_COVERAGE = 40;
    private static final int WEIGHT_RESUME_EVIDENCE_STRENGTH = 10;
    private static final int WEIGHT_RELEVANT_EXPERIENCE = 15;
    private static final int WEIGHT_PROJECT_RELEVANCE = 15;
    private static final int WEIGHT_ASSESSMENT_PERFORMANCE = 10;
    private static final int WEIGHT_INTERVIEW_PERFORMANCE = 10;

    /** Approximate years of experience each profile band represents, for comparison against a JD's "N+ years" requirement. */
    private static final Map<String, Integer> EXPERIENCE_LEVEL_YEARS = Map.of(
            "FRESHER", 0, "JUNIOR", 2, "MID", 4, "SENIOR", 7);

    private static final Pattern YEARS_PATTERN = Pattern.compile("(\\d+)\\s*\\+?\\s*(?:-|to)?\\s*(\\d*)");

    /**
     * Pure computation core of {@link #matchResumeToJobDescription}, factored
     * out so the classification/scoring logic itself is unit-testable
     * without a database - mirrors {@link #computeRoleMatchPure}.
     *
     * <p>Per-JD-skill classification (required or preferred) is unchanged:
     *   - not on the resume                                  -> MISSING
     *   - on resume, matched topic accuracy &gt;= 75%              -> STRONG_MATCH
     *   - on resume, matched topic accuracy in (0, 75%)            -> PARTIAL_MATCH, unless
     *     accuracy is also &lt; 40% (i.e. clearly struggling)          -> WEAK_EVIDENCE
     *   - on resume, no assessment evidence either way             -> PARTIAL_MATCH
     *     (present, but nothing to confirm depth yet)
     *
     * <p>The overall {@code matchScore} is now an explainable, weighted blend of up to six
     * named components (each surfaced in {@link JobMatchResult#breakdown()} as a
     * {@link ReadinessScore.Component}), following the same "only include a component once
     * there's real evidence for it, then renormalise the remaining weights" rule
     * {@code ReadinessScoreService} uses for the Interview Readiness Score:
     * <ol>
     *   <li><b>Required &amp; Preferred Skill Coverage</b> (always included) - required skills
     *       carry 70% of this component's internal weight and preferred 30%, with
     *       STRONG=1.0/PARTIAL=0.6/WEAK=0.3/MISSING=0.0 coverage credit per skill - this is the
     *       original scoring formula, unchanged, and is also where "resume evidence" (whether a
     *       skill is actually on the resume at all) enters the score.</li>
     *   <li><b>Resume Evidence Strength</b> (included whenever at least one required/preferred
     *       skill is present on the resume) - of the JD skills that ARE on the resume, what
     *       fraction are backed by real assessment evidence (STRONG_MATCH or WEAK_EVIDENCE)
     *       rather than merely being listed with nothing to confirm depth yet (PARTIAL_MATCH
     *       with no assessment attempts) - i.e. how much of the resume's claimed overlap with
     *       this JD is actually corroborated, not just self-reported.</li>
     *   <li><b>Relevant Experience</b> (included only when both the JD states an experience
     *       requirement, e.g. "3+ years", and the candidate has a profile experience level) -
     *       candidate years (approximated from FRESHER/JUNIOR/MID/SENIOR) vs. required years.</li>
     *   <li><b>Project/Technology Relevance</b> (included only when the JD lists concrete
     *       technologies) - the fraction of those technologies that are also on the resume,
     *       i.e. how much of the resume's real, extracted skill set overlaps with the tools
     *       this specific role's projects would actually use.</li>
     *   <li><b>Assessment Performance</b> (included only once the candidate has completed at
     *       least one assessment) - their overall average assessment score, independent of
     *       which specific skills it was on.</li>
     *   <li><b>Interview Performance</b> (included only once the candidate has completed at
     *       least one AI mock interview) - their average mock-interview score.</li>
     * </ol>
     * Every component score is derived only from data the caller actually supplied (resume
     * skills, assessment history, JD text) - nothing about the candidate is fabricated when a
     * signal is unavailable; that signal is simply left out of the blend, exactly like
     * {@code ReadinessScoreService}.
     */
    static JobMatchResult matchResumeToJobDescriptionPure(List<String> requiredSkills, List<String> preferredSkills,
                                                           Set<String> extractedSkillsLower,
                                                           Map<String, Integer> matchedTopicBySkillLower,
                                                           Map<Integer, TopicPerformance> performanceByTopic) {
        List<JdSkillEvidence> strong = new ArrayList<>();
        List<JdSkillEvidence> partial = new ArrayList<>();
        List<JdSkillEvidence> weak = new ArrayList<>();
        List<JdSkillEvidence> missing = new ArrayList<>();

        double requiredCoverage = classifyGroup(requiredSkills, true, extractedSkillsLower,
                matchedTopicBySkillLower, performanceByTopic, strong, partial, weak, missing);
        double preferredCoverage = classifyGroup(preferredSkills, false, extractedSkillsLower,
                matchedTopicBySkillLower, performanceByTopic, strong, partial, weak, missing);

        int requiredCount = requiredSkills == null ? 0 : requiredSkills.size();
        int preferredCount = preferredSkills == null ? 0 : preferredSkills.size();
        double matchScore;
        if (requiredCount == 0 && preferredCount == 0) {
            matchScore = 0.0;
        } else if (requiredCount == 0) {
            matchScore = preferredCoverage * 100.0;
        } else if (preferredCount == 0) {
            matchScore = requiredCoverage * 100.0;
        } else {
            matchScore = (requiredCoverage * 0.7 + preferredCoverage * 0.3) * 100.0;
        }
        matchScore = Math.round(matchScore * 100.0) / 100.0;

        // High-priority: missing required skills first, then weak-evidence required skills,
        // then missing preferred skills - i.e. exactly what should be studied/added first.
        List<String> highPriority = new ArrayList<>();
        missing.stream().filter(JdSkillEvidence::requiredByJd).map(JdSkillEvidence::skillName).forEach(highPriority::add);
        weak.stream().filter(JdSkillEvidence::requiredByJd).map(JdSkillEvidence::skillName).forEach(highPriority::add);
        missing.stream().filter(e -> !e.requiredByJd()).map(JdSkillEvidence::skillName).forEach(highPriority::add);

        String summary = String.format(Locale.ROOT,
                "Job Match Score: %.1f%%. %d strong match(es), %d partial match(es), %d weak-evidence skill(s), "
                        + "%d missing skill(s) out of %d required + %d preferred JD skill(s).",
                matchScore, strong.size(), partial.size(), weak.size(), missing.size(), requiredCount, preferredCount);

        List<ReadinessScore.Component> breakdown = List.of(new ReadinessScore.Component(
                "Required & Preferred Skill Coverage", matchScore, 100, String.format(Locale.ROOT,
                        "%d strong, %d partial, %d weak-evidence, %d missing out of %d required + %d preferred skill(s). "
                                + "Required skills carry 70%% of this component's weight, preferred 30%%.",
                        strong.size(), partial.size(), weak.size(), missing.size(), requiredCount, preferredCount)));

        return new JobMatchResult(null, matchScore, strong, partial, weak, missing, highPriority, summary, breakdown);
    }

    /**
     * Richer overload of {@link #matchResumeToJobDescriptionPure}: reuses the skill
     * classification and Skill Coverage score from the five-argument overload above
     * unchanged, then blends in the remaining signals the Job Match Score should consider -
     * Resume Evidence Strength, Relevant Experience, Project/Technology Relevance, Assessment
     * Performance and Interview Performance - each only when the caller actually supplied real
     * data for it (see the class-level breakdown documented on this overload's sibling). This
     * is the pathway {@link #matchResumeToJobDescription} uses in production; the plain
     * five-argument overload remains available (and numerically unchanged) for callers that
     * only have the original signals.
     */
    static JobMatchResult matchResumeToJobDescriptionPure(List<String> requiredSkills, List<String> preferredSkills,
                                                           Set<String> extractedSkillsLower,
                                                           Map<String, Integer> matchedTopicBySkillLower,
                                                           Map<Integer, TopicPerformance> performanceByTopic,
                                                           List<String> jdTechnologies, String jdExperienceRequired,
                                                           String candidateExperienceLevel,
                                                           Double overallAssessmentAveragePercent,
                                                           Double averageInterviewScorePercent) {
        JobMatchResult base = matchResumeToJobDescriptionPure(requiredSkills, preferredSkills, extractedSkillsLower,
                matchedTopicBySkillLower, performanceByTopic);
        List<JdSkillEvidence> strong = base.strongMatches();
        List<JdSkillEvidence> partial = base.partialMatches();
        List<JdSkillEvidence> weak = base.weakEvidence();
        ReadinessScore.Component skillCoverage = base.breakdown().get(0);

        List<ReadinessScore.Component> breakdown = new ArrayList<>();
        breakdown.add(new ReadinessScore.Component(skillCoverage.getLabel(), skillCoverage.getValue(),
                WEIGHT_SKILL_COVERAGE, skillCoverage.getNote()));

        int presentOnResume = strong.size() + partial.size() + weak.size();
        if (presentOnResume > 0) {
            int corroborated = strong.size() + weak.size(); // has real assessment evidence, good or bad
            double evidenceStrength = corroborated * 100.0 / presentOnResume;
            breakdown.add(new ReadinessScore.Component("Resume Evidence Strength", evidenceStrength,
                    WEIGHT_RESUME_EVIDENCE_STRENGTH, String.format(Locale.ROOT,
                            "%d of %d resume-listed JD skill(s) are backed by real assessment evidence, not just self-reported.",
                            corroborated, presentOnResume)));
        }

        Integer requiredYears = parseRequiredYears(jdExperienceRequired);
        Integer candidateYears = candidateExperienceLevel == null ? null
                : EXPERIENCE_LEVEL_YEARS.get(candidateExperienceLevel.trim().toUpperCase(Locale.ROOT));
        if (requiredYears != null && candidateYears != null) {
            double experienceScore = requiredYears <= 0 ? 100.0
                    : Math.max(0.0, Math.min(100.0, candidateYears * 100.0 / requiredYears));
            breakdown.add(new ReadinessScore.Component("Relevant Experience", experienceScore,
                    WEIGHT_RELEVANT_EXPERIENCE, String.format(Locale.ROOT,
                            "Role expects ~%d year(s); profile experience level implies ~%d year(s).",
                            requiredYears, candidateYears)));
        }

        if (jdTechnologies != null && !jdTechnologies.isEmpty()) {
            long techMatched = jdTechnologies.stream()
                    .filter(t -> extractedSkillsLower.contains(t.toLowerCase(Locale.ROOT)))
                    .count();
            double projectRelevance = techMatched * 100.0 / jdTechnologies.size();
            breakdown.add(new ReadinessScore.Component("Project/Technology Relevance", projectRelevance,
                    WEIGHT_PROJECT_RELEVANCE, String.format(Locale.ROOT,
                            "%d of %d role technolog(ies) also appear on the resume's extracted skills.",
                            techMatched, jdTechnologies.size())));
        }

        if (overallAssessmentAveragePercent != null) {
            double assessmentScore = Math.max(0.0, Math.min(100.0, overallAssessmentAveragePercent));
            breakdown.add(new ReadinessScore.Component("Assessment Performance", assessmentScore,
                    WEIGHT_ASSESSMENT_PERFORMANCE,
                    String.format(Locale.ROOT, "Overall average assessment score: %.1f%%.", assessmentScore)));
        }

        if (averageInterviewScorePercent != null) {
            double interviewScore = Math.max(0.0, Math.min(100.0, averageInterviewScorePercent));
            breakdown.add(new ReadinessScore.Component("Interview Performance", interviewScore,
                    WEIGHT_INTERVIEW_PERFORMANCE,
                    String.format(Locale.ROOT, "Average AI mock-interview score: %.1f%%.", interviewScore)));
        }

        double weightedSum = 0.0;
        int totalWeight = 0;
        for (ReadinessScore.Component c : breakdown) {
            weightedSum += c.getValue() * c.getWeight();
            totalWeight += c.getWeight();
        }
        double matchScore = totalWeight == 0 ? 0.0 : weightedSum / totalWeight;
        matchScore = Math.round(matchScore * 100.0) / 100.0;

        int requiredCount = requiredSkills == null ? 0 : requiredSkills.size();
        int preferredCount = preferredSkills == null ? 0 : preferredSkills.size();
        StringBuilder summary = new StringBuilder(String.format(Locale.ROOT,
                "Job Match Score: %.1f%%. %d strong match(es), %d partial match(es), %d weak-evidence skill(s), "
                        + "%d missing skill(s) out of %d required + %d preferred JD skill(s).",
                matchScore, strong.size(), partial.size(), weak.size(), base.missing().size(), requiredCount, preferredCount));
        if (breakdown.size() > 1) {
            summary.append(" Breakdown - ");
            summary.append(breakdown.stream()
                    .map(c -> String.format(Locale.ROOT, "%s: %.1f%% (weight %d%%)", c.getLabel(), c.getValue(), c.getWeight()))
                    .collect(Collectors.joining(", ")));
            summary.append('.');
        }

        return new JobMatchResult(null, matchScore, strong, partial, weak, base.missing(), base.highPrioritySkills(),
                summary.toString(), breakdown);
    }

    /** Extracts the leading "N (+) years" figure from free text like "3+ years" or "5-7 years", or null if none is found - never invents a requirement the JD text didn't actually state. */
    private static Integer parseRequiredYears(String experienceRequiredText) {
        if (experienceRequiredText == null || experienceRequiredText.isBlank()) {
            return null;
        }
        Matcher m = YEARS_PATTERN.matcher(experienceRequiredText);
        if (m.find()) {
            try {
                return Integer.parseInt(m.group(1));
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    /** Classifies one group (required or preferred) of JD skills, appending each skill's evidence into the shared result buckets, and returns that group's 0.0-1.0 coverage score. */
    private static double classifyGroup(List<String> skills, boolean required, Set<String> extractedSkillsLower,
                                         Map<String, Integer> matchedTopicBySkillLower,
                                         Map<Integer, TopicPerformance> performanceByTopic,
                                         List<JdSkillEvidence> strong, List<JdSkillEvidence> partial,
                                         List<JdSkillEvidence> weak, List<JdSkillEvidence> missing) {
        if (skills == null || skills.isEmpty()) {
            return 0.0;
        }
        double totalCredit = 0.0;
        for (String skillName : skills) {
            String lower = skillName.toLowerCase(Locale.ROOT);
            if (!extractedSkillsLower.contains(lower)) {
                missing.add(new JdSkillEvidence(skillName, JobMatchStrength.MISSING, required,
                        "Not found on your resume."));
                continue;
            }

            Integer topicId = matchedTopicBySkillLower.get(lower);
            TopicPerformance tp = topicId == null ? null : performanceByTopic.get(topicId);
            if (tp == null || tp.getAttemptsCount() == 0) {
                partial.add(new JdSkillEvidence(skillName, JobMatchStrength.PARTIAL_MATCH, required,
                        "On your resume, but no assessment evidence yet to confirm depth."));
                totalCredit += 0.6;
                continue;
            }

            double accuracy = tp.getAccuracyPercent().doubleValue();
            if (accuracy >= STRONG_MATCH_ACCURACY_THRESHOLD) {
                strong.add(new JdSkillEvidence(skillName, JobMatchStrength.STRONG_MATCH, required,
                        String.format(Locale.ROOT, "On your resume with %.1f%% assessment accuracy.", accuracy)));
                totalCredit += 1.0;
            } else if (accuracy < WEAK_TOPIC_ACCURACY_THRESHOLD) {
                weak.add(new JdSkillEvidence(skillName, JobMatchStrength.WEAK_EVIDENCE, required,
                        String.format(Locale.ROOT, "On your resume, but assessment accuracy is only %.1f%%.", accuracy)));
                totalCredit += 0.3;
            } else {
                partial.add(new JdSkillEvidence(skillName, JobMatchStrength.PARTIAL_MATCH, required,
                        String.format(Locale.ROOT, "On your resume with moderate (%.1f%%) assessment accuracy.", accuracy)));
                totalCredit += 0.6;
            }
        }
        return totalCredit / skills.size();
    }
}
