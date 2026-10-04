package com.careerintelligence.demo;

import com.careerintelligence.dao.MistakeLogDAO;
import com.careerintelligence.dao.ReadinessScoreDAO;
import com.careerintelligence.model.LearningRoadmap;
import com.careerintelligence.model.OverallStats;
import com.careerintelligence.model.ReadinessScore;
import com.careerintelligence.model.ResumeSkill;
import com.careerintelligence.model.RoadmapCategory;
import com.careerintelligence.model.RoadmapItem;
import com.careerintelligence.model.RoadmapItemStatus;
import com.careerintelligence.model.RoadmapPriority;
import com.careerintelligence.model.TopicPerformance;
import com.careerintelligence.model.UserBadge;
import com.careerintelligence.model.UserPoints;
import com.careerintelligence.service.DashboardView;
import com.careerintelligence.service.IntegratedDashboardService;
import com.careerintelligence.service.ProgressAnalyticsService;
import com.careerintelligence.service.ResumeService;
import com.careerintelligence.util.AdaptiveDifficultyCalculator;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Builds the realistic dataset behind the guided demo.
 *
 * <p>The demo follows one candidate - <b>Priya Raman</b>, a mid-level
 * backend developer targeting a Senior Backend Engineer role at a
 * payments company - through the complete pipeline, and shows the system's
 * numbers moving as she works through it:
 *
 * <pre>
 * Resume -&gt; JD -&gt; Job Match -&gt; Skill Gaps -&gt; Roadmap
 *        -&gt; Assessment -&gt; AI Evaluation -&gt; Mock Interview -&gt; Improved Readiness
 * </pre>
 *
 * <p>Crucially this is <em>data</em>, not a script of pre-rendered
 * screens: the factory produces real {@code CompleteDashboard} objects and
 * feeds them through the exact same {@link DashboardView} and
 * {@code DashboardRenderer} code paths the live application uses. The demo
 * therefore proves the real rendering and explanation pipeline works,
 * rather than faking a screenshot of it - and it runs with no database and
 * no API key, so it always works on a hackathon laptop.
 */
public final class DemoDataFactory {

    public static final String CANDIDATE_NAME = "Priya Raman";
    public static final String TARGET_ROLE = "Senior Backend Engineer";
    public static final String JOB_TITLE = "Senior Backend Engineer - Northwind Payments";

    private DemoDataFactory() {
    }

    /** The three points in the journey the demo contrasts. */
    public enum Stage {
        /** Resume analysed, JD matched, roadmap generated - but nothing practised yet. */
        BASELINE,
        /** After the adaptive assessment, AI evaluation and mistake retry. */
        AFTER_PRACTICE,
        /** After the AI mock interview - the full loop closed. */
        AFTER_MOCK
    }

    // -----------------------------------------------------------------
    // Resume and job description
    // -----------------------------------------------------------------

    /** The skills the resume parser extracts from Priya's CV. */
    public static List<ResumeSkill> resumeSkills() {
        return List.of(
                skill("Java", "Language", 1, "Java Core"),
                skill("Spring Boot", "Framework", 2, "Spring Boot"),
                skill("REST APIs", "Architecture", 3, "REST APIs"),
                skill("MySQL", "Database", 4, "Databases"),
                skill("Hibernate", "Framework", 4, "Databases"),
                skill("Docker", "DevOps", 5, "DevOps"),
                skill("JUnit", "Testing", 6, "Testing"),
                skill("Git", "Tooling", null, null));
    }

    /** Skills the JD requires that never appear on the resume. */
    public static List<String> missingSkills() {
        return List.of("Kubernetes", "Kafka", "System Design");
    }

    /** Skills claimed on the resume that measured performance contradicts. */
    public static List<String> weakSkills() {
        return List.of("Hibernate", "Docker");
    }

    public static List<String> jdRequiredSkills() {
        return List.of("Java", "Spring Boot", "Microservices", "Kubernetes", "Kafka",
                "System Design", "PostgreSQL", "CI/CD");
    }

    /** The raw job description text the demo "pastes" into the analyser. */
    public static String jobDescriptionText() {
        return """
                Senior Backend Engineer - Northwind Payments

                We are hiring a Senior Backend Engineer to own services that move
                real money at scale.

                Required skills:
                - Strong Java (17+) and Spring Boot experience
                - Designing and operating microservices in production
                - Kubernetes and containerised deployment
                - Event-driven architecture with Kafka
                - System design for high-throughput, low-latency systems
                - PostgreSQL and relational data modelling
                - CI/CD pipelines and automated testing

                Preferred:
                - Payments or fintech domain experience
                - Observability tooling (Prometheus, Grafana)

                Responsibilities:
                - Design and ship resilient payment services
                - Lead technical design reviews
                - Mentor mid-level engineers

                Experience required: 5+ years
                """;
    }

    /** The explainable Job Match view, including the weighted breakdown that produced the score. */
    public static DashboardView.JobMatchView jobMatch(Stage stage) {
        double score = switch (stage) {
            case BASELINE -> 46.5;
            case AFTER_PRACTICE -> 54.0;
            case AFTER_MOCK -> 61.5;
        };
        List<ReadinessScore.Component> breakdown = List.of(
                new ReadinessScore.Component("Required skills", stage == Stage.BASELINE ? 50.0 : 62.5, 50,
                        "4 of 8 required skills evidenced on the resume"),
                new ReadinessScore.Component("Demonstrated depth", stage == Stage.BASELINE ? 41.0 : 63.0, 30,
                        "measured assessment accuracy on matched topics"),
                new ReadinessScore.Component("Preferred skills", 45.0, 10,
                        "domain and observability experience partially evidenced"),
                new ReadinessScore.Component("Experience fit", 55.0, 10,
                        "4 years against a 5+ year requirement"));

        return new DashboardView.JobMatchView(
                JOB_TITLE,
                score,
                List.of("Java", "Spring Boot", "REST APIs", "CI/CD"),
                List.of("Microservices", "PostgreSQL"),
                missingSkills(),
                List.of("System Design", "Kubernetes", "Kafka"),
                "Strong core-Java and Spring foundation, but the JD's distributed-systems requirements "
                        + "(System Design, Kubernetes, Kafka) are unevidenced. Closing System Design alone "
                        + "moves the largest single weight in the score.",
                breakdown);
    }

    // -----------------------------------------------------------------
    // Performance data
    // -----------------------------------------------------------------

    /** Per-topic accuracy at each stage - this is where the adaptive movement is most visible. */
    public static List<ProgressAnalyticsService.TopicTrend> topicTrends(Stage stage) {
        return switch (stage) {
            case BASELINE -> List.of(
                    trend("System Design", 38.0, 8, AdaptiveDifficultyCalculator.Trend.DECLINING),
                    trend("Concurrency", 45.0, 11, AdaptiveDifficultyCalculator.Trend.STABLE),
                    trend("Databases", 54.0, 14, AdaptiveDifficultyCalculator.Trend.STABLE),
                    trend("Data Structures", 61.0, 16, AdaptiveDifficultyCalculator.Trend.IMPROVING),
                    trend("Spring Boot", 68.0, 12, AdaptiveDifficultyCalculator.Trend.STABLE),
                    trend("Java Core", 72.0, 20, AdaptiveDifficultyCalculator.Trend.IMPROVING),
                    trend("REST APIs", 76.0, 9, AdaptiveDifficultyCalculator.Trend.IMPROVING));
            case AFTER_PRACTICE -> List.of(
                    trend("System Design", 57.0, 18, AdaptiveDifficultyCalculator.Trend.IMPROVING),
                    trend("Concurrency", 62.0, 19, AdaptiveDifficultyCalculator.Trend.IMPROVING),
                    trend("Databases", 66.0, 21, AdaptiveDifficultyCalculator.Trend.IMPROVING),
                    trend("Data Structures", 69.0, 22, AdaptiveDifficultyCalculator.Trend.IMPROVING),
                    trend("Spring Boot", 74.0, 17, AdaptiveDifficultyCalculator.Trend.IMPROVING),
                    trend("Java Core", 79.0, 26, AdaptiveDifficultyCalculator.Trend.IMPROVING),
                    trend("REST APIs", 81.0, 13, AdaptiveDifficultyCalculator.Trend.STABLE));
            case AFTER_MOCK -> List.of(
                    trend("System Design", 64.0, 24, AdaptiveDifficultyCalculator.Trend.IMPROVING),
                    trend("Concurrency", 66.0, 21, AdaptiveDifficultyCalculator.Trend.IMPROVING),
                    trend("Databases", 70.0, 23, AdaptiveDifficultyCalculator.Trend.IMPROVING),
                    trend("Data Structures", 72.0, 24, AdaptiveDifficultyCalculator.Trend.IMPROVING),
                    trend("Spring Boot", 78.0, 19, AdaptiveDifficultyCalculator.Trend.IMPROVING),
                    trend("Java Core", 82.0, 28, AdaptiveDifficultyCalculator.Trend.IMPROVING),
                    trend("REST APIs", 84.0, 15, AdaptiveDifficultyCalculator.Trend.STABLE));
        };
    }

    /** Topics still below the competency line at a given stage. */
    public static List<TopicPerformance> weakTopics(Stage stage) {
        List<TopicPerformance> out = new ArrayList<>();
        for (ProgressAnalyticsService.TopicTrend t : topicTrends(stage)) {
            if (t.accuracyPercent() < DashboardView.MASTERY_THRESHOLD) {
                out.add(topicPerformance(t.topicName(), t.accuracyPercent(), t.attempts()));
            }
        }
        return out;
    }

    public static OverallStats assessmentStats(Stage stage) {
        OverallStats s = new OverallStats();
        switch (stage) {
            case BASELINE -> {
                s.setTotalAssessments(2);
                s.setAverageScorePercent(58.5);
                s.setBestScorePercent(66.0);
                s.setOverallAccuracyPercent(57.8);
                s.setTotalCorrect(26);
                s.setTotalWrong(19);
                s.setTotalUnanswered(3);
            }
            case AFTER_PRACTICE -> {
                s.setTotalAssessments(4);
                s.setAverageScorePercent(68.4);
                s.setBestScorePercent(79.0);
                s.setOverallAccuracyPercent(67.2);
                s.setTotalCorrect(58);
                s.setTotalWrong(28);
                s.setTotalUnanswered(2);
            }
            case AFTER_MOCK -> {
                s.setTotalAssessments(5);
                s.setAverageScorePercent(72.1);
                s.setBestScorePercent(84.0);
                s.setOverallAccuracyPercent(71.0);
                s.setTotalCorrect(71);
                s.setTotalWrong(29);
                s.setTotalUnanswered(2);
            }
        }
        return s;
    }

    public static MistakeLogDAO.MistakeCounts mistakeCounts(Stage stage) {
        return switch (stage) {
            case BASELINE -> new MistakeLogDAO.MistakeCounts(14, 5, 9);
            case AFTER_PRACTICE -> new MistakeLogDAO.MistakeCounts(19, 16, 3);
            case AFTER_MOCK -> new MistakeLogDAO.MistakeCounts(21, 19, 2);
        };
    }

    // -----------------------------------------------------------------
    // Readiness
    // -----------------------------------------------------------------

    /** The readiness score history, oldest first, that the trend sparkline plots. */
    public static List<Double> readinessSeries(Stage stage) {
        return switch (stage) {
            case BASELINE -> List.of(38.4, 41.2);
            case AFTER_PRACTICE -> List.of(38.4, 41.2, 47.5, 52.3, 58.6);
            case AFTER_MOCK -> List.of(38.4, 41.2, 47.5, 52.3, 58.6, 63.1, 71.8);
        };
    }

    public static ReadinessScore readiness(Stage stage) {
        ReadinessScore r = new ReadinessScore();
        List<Double> series = readinessSeries(stage);
        double overall = series.get(series.size() - 1);
        r.setOverallScore(overall);
        r.setComputedAt(LocalDateTime.now());

        switch (stage) {
            case BASELINE -> {
                r.setBand("NOT READY");
                addDimensions(r, 56.0, 48.0, 30.0, 32.0, 52.0);
                r.setBiggestStrength("Technical (56.0/100)");
                r.setBiggestRisk("Communication (30.0/100)");
                r.setNextRecommendedAction("Start an adaptive assessment on System Design and Concurrency - "
                        + "they are your weakest measured topics and both are required by your target JD.");
            }
            case AFTER_PRACTICE -> {
                r.setBand("DEVELOPING");
                addDimensions(r, 71.0, 64.0, 34.0, 36.0, 62.0);
                r.setBiggestStrength("Technical (71.0/100)");
                r.setBiggestRisk("Communication (34.0/100)");
                r.setNextRecommendedAction("Run an AI mock interview - your technical dimensions have improved "
                        + "but Communication and Behavioral are still unscored, and they are capping your overall score.");
            }
            case AFTER_MOCK -> {
                r.setBand("ALMOST READY");
                addDimensions(r, 76.0, 70.0, 68.0, 71.0, 69.0);
                r.setBiggestStrength("Technical (76.0/100)");
                r.setBiggestRisk("Communication (68.0/100)");
                r.setNextRecommendedAction("Close the Kubernetes and Kafka roadmap items, then re-run the Job "
                        + "Match - they are the last two required JD skills with no evidence behind them.");
            }
        }

        r.addComponent("assessment", new ReadinessScore.Component("Assessment performance",
                assessmentStats(stage).getAverageScorePercent(), 30, "average score across completed assessments"));
        r.addComponent("topics", new ReadinessScore.Component("Topic mastery",
                averageTopicAccuracy(stage), 25, "mean accuracy across attempted topics"));
        r.addComponent("resume", new ReadinessScore.Component("Resume / role alignment",
                jobMatch(stage).matchScore(), 20, "resume-to-JD match score"));
        r.addComponent("mock", new ReadinessScore.Component("Mock interview",
                stage == Stage.AFTER_MOCK ? 71.5 : 0.0, 15,
                stage == Stage.AFTER_MOCK ? "latest mock interview score" : "no mock interview completed yet"));
        r.addComponent("mistakes", new ReadinessScore.Component("Mistake resolution",
                mistakeResolutionPercent(stage), 10, "share of logged mistakes resolved"));
        return r;
    }

    private static void addDimensions(ReadinessScore r, double technical, double problemSolving,
                                       double communication, double behavioral, double roleAlignment) {
        r.addDimension("technical", new ReadinessScore.Component("Technical", technical, 30,
                "core language, framework and database accuracy"));
        r.addDimension("problem_solving", new ReadinessScore.Component("Problem Solving", problemSolving, 25,
                "data structures, algorithms and system design"));
        r.addDimension("communication", new ReadinessScore.Component("Communication", communication, 20,
                "clarity and structure of descriptive and interview answers"));
        r.addDimension("behavioral", new ReadinessScore.Component("Behavioral", behavioral, 15,
                behavioral <= 36.0
                        ? "no real behavioral mock-interview answer yet - placeholder from mistake/streak data"
                        : "blends STAR (Situation/Task/Action/Result) completeness with AI-graded score "
                        + "on your behavioral mock interview answers"));
        r.addDimension("role_alignment", new ReadinessScore.Component("Role Alignment", roleAlignment, 10,
                "resume and JD coverage for the target role"));
    }

    // -----------------------------------------------------------------
    // Roadmap, gamification and pipeline updates
    // -----------------------------------------------------------------

    public static LearningRoadmap roadmap(Stage stage) {
        LearningRoadmap r = new LearningRoadmap();
        r.setRoadmapId(9001L);
        r.setUserId(1L);
        r.setReadinessScore(readiness(stage).getOverallScore());
        r.setReadinessBand(readiness(stage).getBand());
        r.setGeneratedAt(LocalDateTime.now());
        r.setSummary("Generated from 3 unevidenced JD skills, 2 topics below the competency line, and "
                + mistakeCounts(stage).unresolved() + " unresolved mistake(s).");

        int completed = switch (stage) {
            case BASELINE -> 0;
            case AFTER_PRACTICE -> 4;
            case AFTER_MOCK -> 6;
        };

        List<RoadmapItem> items = new ArrayList<>();
        items.add(item(1, "Master System Design fundamentals", RoadmapCategory.SKILL_GAP, RoadmapPriority.HIGH,
                "System Design", "Required by the JD, absent from the resume, and your lowest topic at 38% accuracy."));
        items.add(item(2, "Close the Concurrency gap", RoadmapCategory.WEAK_TOPIC, RoadmapPriority.HIGH,
                "Concurrency", "45% measured accuracy over 11 attempts - below the 60% competency line."));
        items.add(item(3, "Resolve outstanding mistakes", RoadmapCategory.MISTAKE_REVIEW, RoadmapPriority.HIGH,
                null, "9 unresolved mistakes are still feeding your weak-topic profile."));
        items.add(item(4, "Strengthen Databases and Hibernate", RoadmapCategory.WEAK_TOPIC, RoadmapPriority.MEDIUM,
                "Databases", "Claimed on your resume but measured at 54% - a credibility risk in interview."));
        items.add(item(5, "Complete a mock interview", RoadmapCategory.MOCK_PRACTICE, RoadmapPriority.HIGH,
                null, "Communication and Behavioral are unscored until you complete one."));
        items.add(item(6, "Learn Kubernetes deployment basics", RoadmapCategory.SKILL_GAP, RoadmapPriority.MEDIUM,
                null, "Required by the JD; your resume evidences Docker but not orchestration."));
        items.add(item(7, "Learn Kafka and event-driven patterns", RoadmapCategory.SKILL_GAP, RoadmapPriority.MEDIUM,
                null, "Required by the JD with no evidence anywhere on your resume."));
        items.add(item(8, "Prepare payments-domain STAR stories", RoadmapCategory.ROLE_READINESS, RoadmapPriority.LOW,
                null, "The JD is fintech; domain-specific stories raise your Behavioral dimension."));

        for (int i = 0; i < items.size() && i < completed; i++) {
            items.get(i).setStatus(RoadmapItemStatus.COMPLETED);
            items.get(i).setCompletedAt(LocalDateTime.now());
        }
        r.setItems(items);
        return r;
    }

    public static UserPoints points(Stage stage) {
        UserPoints p = new UserPoints();
        p.setUserId(1L);
        p.setTotalPoints(switch (stage) {
            case BASELINE -> 120;
            case AFTER_PRACTICE -> 430;
            case AFTER_MOCK -> 615;
        });
        p.setCurrentStreakDays(switch (stage) {
            case BASELINE -> 1;
            case AFTER_PRACTICE -> 4;
            case AFTER_MOCK -> 6;
        });
        p.setLongestStreakDays(6);
        p.setLastActivityDate(LocalDate.now());
        p.setUpdatedAt(LocalDateTime.now());
        return p;
    }

    public static List<UserBadge> badges(Stage stage) {
        List<UserBadge> badges = new ArrayList<>();
        badges.add(badge("FIRST_RESUME", "Resume Analysed", "Uploaded and analysed a resume"));
        if (stage != Stage.BASELINE) {
            badges.add(badge("ASSESSMENT_3", "Consistent Practitioner", "Completed 3 assessments"));
            badges.add(badge("MISTAKE_CLEARER", "Mistake Clearer", "Resolved 10 logged mistakes"));
        }
        if (stage == Stage.AFTER_MOCK) {
            badges.add(badge("MOCK_COMPLETE", "Interview Tested", "Completed an AI mock interview"));
            badges.add(badge("STREAK_5", "Five-Day Streak", "Practised 5 days in a row"));
        }
        return badges;
    }

    /** The "Career Intelligence Updates" feed - what changed and why, after each activity. */
    public static List<String> careerUpdates(Stage stage) {
        return switch (stage) {
            case BASELINE -> List.of(
                    "You just analysed a new job description. Your Interview Readiness Score improved from 38.4 "
                            + "to 41.2 (+2.8). This was driven mainly by your Role Alignment dimension. Your top "
                            + "roadmap priority is now \"Master System Design fundamentals\". The roadmap now has 8 item(s).",
                    "You just updated your resume. Your Interview Readiness Score has been computed for the first "
                            + "time: 38.4/100. The roadmap now has 6 item(s).");
            case AFTER_PRACTICE -> List.of(
                    "You just retried some previously missed questions. Your Interview Readiness Score improved "
                            + "from 52.3 to 58.6 (+6.3). This was driven mainly by your Problem Solving dimension. "
                            + "The roadmap now has 8 item(s).",
                    "You just completed an assessment. Your Interview Readiness Score improved from 47.5 to 52.3 "
                            + "(+4.8). This was driven mainly by your Technical dimension. Your top roadmap priority "
                            + "is now \"Close the Concurrency gap\". The roadmap now has 8 item(s).",
                    "You just completed an assessment. Your Interview Readiness Score improved from 41.2 to 47.5 "
                            + "(+6.3). This was driven mainly by your Technical dimension. The roadmap now has 8 item(s).");
            case AFTER_MOCK -> List.of(
                    "You just completed a mock interview. Your Interview Readiness Score improved from 63.1 to 71.8 "
                            + "(+8.7). This was driven mainly by your Communication dimension. Your top roadmap "
                            + "priority is now \"Learn Kubernetes deployment basics\". The roadmap now has 8 item(s).",
                    "You just completed a mock interview. Your Interview Readiness Score improved from 58.6 to 63.1 "
                            + "(+4.5). This was driven mainly by your Behavioral dimension. The roadmap now has 8 item(s).",
                    "You just retried some previously missed questions. Your Interview Readiness Score improved "
                            + "from 52.3 to 58.6 (+6.3). This was driven mainly by your Problem Solving dimension. "
                            + "The roadmap now has 8 item(s).");
        };
    }

    // -----------------------------------------------------------------
    // Assembly
    // -----------------------------------------------------------------

    /**
     * Assembles a real {@code CompleteDashboard} for a stage - the same
     * record the live {@code IntegratedDashboardService} produces, so the
     * demo exercises the genuine view-model and renderer rather than a
     * parallel mock implementation.
     */
    public static IntegratedDashboardService.CompleteDashboard completeDashboard(Stage stage) {
        List<ProgressAnalyticsService.TopicTrend> trends = topicTrends(stage);
        LearningRoadmap roadmap = roadmap(stage);
        int roadmapTotal = roadmap.getItems().size();
        int roadmapDone = (int) roadmap.getItems().stream()
                .filter(i -> i.getStatus() == RoadmapItemStatus.COMPLETED).count();

        ProgressAnalyticsService.AnalyticsSummary analytics = new ProgressAnalyticsService.AnalyticsSummary(
                readinessSnapshots(stage),
                trends,
                new ProgressAnalyticsService.TrendMetric("Overall accuracy",
                        assessmentStats(stage).getOverallAccuracyPercent(), 52.0,
                        ProgressAnalyticsService.Direction.UP),
                new ProgressAnalyticsService.TrendMetric("Seconds per question", 48.0, 63.0,
                        ProgressAnalyticsService.Direction.DOWN),
                mistakeCounts(stage),
                roadmapTotal == 0 ? 0.0 : Math.round(roadmapDone * 10000.0 / roadmapTotal) / 100.0,
                roadmapTotal, roadmapDone);

        ResumeService.RoleMatch roleMatch = new ResumeService.RoleMatch(
                TARGET_ROLE, jobMatch(stage).matchScore(),
                List.of("Java", "Spring Boot", "REST APIs", "CI/CD"),
                missingSkills(),
                List.of(new ResumeService.PriorityGap("System Design",
                        "Required by the JD and your lowest measured topic", 95),
                        new ResumeService.PriorityGap("Kubernetes", "Required by the JD, no resume evidence", 80),
                        new ResumeService.PriorityGap("Kafka", "Required by the JD, no resume evidence", 78)));

        int mockTotal = switch (stage) {
            case BASELINE -> 0;
            case AFTER_PRACTICE -> 1;
            case AFTER_MOCK -> 3;
        };
        int mockDone = switch (stage) {
            case BASELINE, AFTER_PRACTICE -> 0;
            case AFTER_MOCK -> 2;
        };

        return new IntegratedDashboardService.CompleteDashboard(
                TARGET_ROLE,
                assessmentStats(stage),
                resumeSkills(),
                missingSkills(),
                weakSkills(),
                weakTopics(stage),
                weakTopics(stage).stream().map(TopicPerformance::getTopicName).toList(),
                mistakeCounts(stage),
                mockTotal, mockDone,
                readiness(stage),
                Optional.of(roadmap),
                points(stage),
                badges(stage),
                roleMatch,
                analytics);
    }

    /**
     * Builds the presentation-ready dashboard for a stage by running the
     * demo data through the production {@link DashboardView#from} factory.
     */
    public static DashboardView dashboardView(Stage stage) {
        return DashboardView.from(
                CANDIDATE_NAME,
                completeDashboard(stage),
                jobMatch(stage),
                refreshLog(stage),
                stage == Stage.AFTER_MOCK ? 71.5 : null,
                "local semantic engine (demo mode)");
    }

    // -----------------------------------------------------------------
    // Builders
    // -----------------------------------------------------------------

    private static List<ReadinessScoreDAO.Snapshot> readinessSnapshots(Stage stage) {
        // The DAO returns newest-first; mirror that so DashboardView's reversal is genuinely exercised.
        List<Double> series = new ArrayList<>(readinessSeries(stage));
        List<ReadinessScoreDAO.Snapshot> snapshots = new ArrayList<>();
        for (int i = series.size() - 1; i >= 0; i--) {
            double v = series.get(i);
            snapshots.add(new ReadinessScoreDAO.Snapshot(v, v, v, v, v, v,
                    v, v, v, v, v, "Technical", "Communication",
                    "See dashboard recommendation.", LocalDateTime.now().minusDays(series.size() - i)));
        }
        return snapshots;
    }

    private static List<com.careerintelligence.dao.CareerRefreshLogDAO.Snapshot> refreshLog(Stage stage) {
        List<com.careerintelligence.dao.CareerRefreshLogDAO.Snapshot> out = new ArrayList<>();
        long id = 1;
        for (String detail : careerUpdates(stage)) {
            out.add(new com.careerintelligence.dao.CareerRefreshLogDAO.Snapshot(
                    id++, "DEMO", readiness(stage).getOverallScore(), 9001L, detail, LocalDateTime.now()));
        }
        return out;
    }

    private static ResumeSkill skill(String name, String category, Integer topicId, String topicName) {
        ResumeSkill s = new ResumeSkill();
        s.setUserId(1L);
        s.setSkillName(name);
        s.setCategory(category);
        s.setMatchedTopicId(topicId);
        s.setMatchedTopicName(topicName);
        s.setExtractedAt(LocalDateTime.now());
        return s;
    }

    private static ProgressAnalyticsService.TopicTrend trend(String name, double accuracy, int attempts,
                                                              AdaptiveDifficultyCalculator.Trend t) {
        return new ProgressAnalyticsService.TopicTrend(name, accuracy, attempts, t);
    }

    private static TopicPerformance topicPerformance(String name, double accuracy, int attempts) {
        TopicPerformance tp = new TopicPerformance();
        tp.setUserId(1L);
        tp.setTopicName(name);
        tp.setTopicId(Math.abs(name.hashCode() % 100));
        tp.setAttemptsCount(attempts);
        tp.setCorrectCount((int) Math.round(attempts * accuracy / 100.0));
        tp.setWrongCount(attempts - (int) Math.round(attempts * accuracy / 100.0));
        tp.setAccuracyPercent(BigDecimal.valueOf(accuracy));
        tp.setLastAttemptAt(LocalDateTime.now());
        return tp;
    }

    private static RoadmapItem item(int order, String title, RoadmapCategory category, RoadmapPriority priority,
                                     String topic, String description) {
        RoadmapItem i = new RoadmapItem();
        i.setRoadmapId(9001L);
        i.setItemOrder(order);
        i.setTitle(title);
        i.setCategory(category);
        i.setPriority(priority);
        i.setRelatedTopicName(topic);
        i.setDescription(description);
        i.setStatus(RoadmapItemStatus.PENDING);
        return i;
    }

    private static UserBadge badge(String code, String name, String description) {
        UserBadge b = new UserBadge();
        b.setUserId(1L);
        b.setBadgeCode(code);
        b.setBadgeName(name);
        b.setDescription(description);
        b.setEarnedAt(LocalDateTime.now());
        return b;
    }

    private static double averageTopicAccuracy(Stage stage) {
        return topicTrends(stage).stream()
                .mapToDouble(ProgressAnalyticsService.TopicTrend::accuracyPercent)
                .average().orElse(0.0);
    }

    private static double mistakeResolutionPercent(Stage stage) {
        MistakeLogDAO.MistakeCounts c = mistakeCounts(stage);
        return c.total() == 0 ? 0.0 : Math.round(c.resolved() * 10000.0 / c.total()) / 100.0;
    }
}
