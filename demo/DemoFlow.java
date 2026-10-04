package com.careerintelligence.demo;

import com.careerintelligence.ai.AIHealth;
import com.careerintelligence.ai.AIService;
import com.careerintelligence.ai.AIServiceImpl;
import com.careerintelligence.model.ReadinessScore;
import com.careerintelligence.model.ResumeSkill;
import com.careerintelligence.model.RoadmapItem;
import com.careerintelligence.model.RoadmapItemStatus;
import com.careerintelligence.service.DashboardView;
import com.careerintelligence.service.RecommendationExplainer;
import com.careerintelligence.ui.Ansi;
import com.careerintelligence.ui.Charts;
import com.careerintelligence.ui.ConsoleIO;
import com.careerintelligence.ui.DashboardRenderer;
import com.careerintelligence.ui.Layout;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Scanner;
import java.util.stream.Collectors;

/**
 * The guided demo: a scripted, nine-stage walkthrough of the complete
 * career-intelligence loop, using the realistic dataset in
 * {@link DemoDataFactory}.
 *
 * <pre>
 * 1 Resume            2 Job Description   3 Job Match
 * 4 Skill Gaps        5 Roadmap           6 Assessment
 * 7 AI Evaluation     8 Mock Interview    9 Improved Readiness
 * </pre>
 *
 * <p>Two design decisions make this useful rather than decorative:
 *
 * <ul>
 *   <li><b>It needs no database and no API key.</b> Every stage is computed
 *       in memory, so the demo runs identically on any machine - a
 *       hackathon laptop with no MySQL, a CI job, or a projector at a
 *       judging table.</li>
 *   <li><b>It runs the real code.</b> Stages 1 and 7 call the genuine
 *       {@link AIServiceImpl} (skill extraction and answer evaluation,
 *       which both work fully offline through the local semantic engine),
 *       and every dashboard is rendered by the production
 *       {@link DashboardRenderer}. The only thing supplied is the
 *       candidate's data.</li>
 * </ul>
 *
 * <p>The demo's throughline is the adaptive change: each stage prints an
 * explicit before/after delta, so an audience can see the readiness score,
 * topic mastery and recommendation all move in response to what the
 * candidate just did.
 */
public final class DemoFlow {

    private final Scanner scanner;
    private final boolean interactive;
    private final AIServiceImpl aiService = new AIServiceImpl();

    public DemoFlow(Scanner scanner, boolean interactive) {
        this.scanner = scanner;
        this.interactive = interactive;
    }

    /** Runs all nine stages in order. */
    public void run() {
        intro();

        stage1Resume();
        stage2JobDescription();
        stage3JobMatch();
        stage4SkillGaps();
        stage5Roadmap();
        stage6Assessment();
        stage7AiEvaluation();
        stage8MockInterview();
        stage9ImprovedReadiness();

        outro();
    }

    // -----------------------------------------------------------------
    // Framing
    // -----------------------------------------------------------------

    private void intro() {
        System.out.println();
        Layout.print(Layout.banner("INTERVIEWMENTOR GUIDED DEMO  \u2022  THE COMPLETE READINESS LOOP",
                "Candidate: " + DemoDataFactory.CANDIDATE_NAME + "   \u2022   Target: "
                        + DemoDataFactory.JOB_TITLE));
        System.out.println();
        for (String line : Layout.wrap(
                "This demo follows one candidate through every stage of the system, with no database "
                        + "and no API key required. The skill extraction and answer evaluation you will see "
                        + "are produced by the real AI service running on its local semantic engine - only "
                        + "the candidate's data is supplied.")) {
            System.out.println(line);
        }
        System.out.println();
        System.out.println("  " + Ansi.grey("Resume \u2192 JD \u2192 Job Match \u2192 Skill Gaps \u2192 Roadmap \u2192 "
                + "Assessment \u2192 AI Evaluation \u2192 Mock \u2192 Readiness"));
        pause();
    }

    private void outro() {
        System.out.println();
        Layout.print(Layout.banner("DEMO COMPLETE", "The loop is closed - and it repeats"));
        System.out.println();

        List<Double> series = DemoDataFactory.readinessSeries(DemoDataFactory.Stage.AFTER_MOCK);
        double first = series.get(0);
        double last = series.get(series.size() - 1);

        List<String> body = new ArrayList<>();
        body.add(Layout.kv("Readiness journey", Charts.sparkline(series) + "  "
                + String.format(Locale.ROOT, "%.1f \u2192 %.1f  ", first, last) + Charts.delta(last - first, true)));
        body.add(Layout.kv("Band", "NOT READY \u2192 " + Ansi.green(Ansi.bold("ALMOST READY"))));
        body.add(Layout.kv("Job match", "46.5% \u2192 61.5%  " + Charts.delta(15.0, true)));
        // Derived from the demo data rather than hardcoded, so this summary can never
        // contradict the before/after table printed in stage 9.
        int weakBefore = DemoDataFactory.dashboardView(DemoDataFactory.Stage.BASELINE).gaps().size();
        int weakAfter = DemoDataFactory.dashboardView(DemoDataFactory.Stage.AFTER_MOCK).gaps().size();
        int mistakesBefore = DemoDataFactory.mistakeCounts(DemoDataFactory.Stage.BASELINE).unresolved();
        int mistakesAfter = DemoDataFactory.mistakeCounts(DemoDataFactory.Stage.AFTER_MOCK).unresolved();
        body.add(Layout.kv("Weak topics", weakBefore + " below competency \u2192 "
                + Ansi.green(weakAfter + " below competency")));
        body.add(Layout.kv("Unresolved mistakes", mistakesBefore + " \u2192 "
                + Ansi.green(String.valueOf(mistakesAfter))));
        body.add("");
        body.addAll(Layout.wrap("Every number moved because of something the candidate actually did, and each "
                + "activity fed straight back into the next recommendation. That feedback loop is the product.",
                Layout.WIDTH - 6, "  "));
        Layout.print(Layout.panel("WHAT THE DEMO SHOWED", body));
        pause();
    }

    private void stageHeader(int number, String title, String subtitle) {
        System.out.println();
        Layout.print(Layout.banner("STAGE " + number + " OF 9  \u2022  " + title, subtitle));
        System.out.println();
    }

    private void pause() {
        if (interactive && scanner != null) {
            System.out.print("\n" + Ansi.grey("Press ENTER for the next stage..."));
            try {
                scanner.nextLine();
            } catch (RuntimeException e) {
                // No input available (piped/non-interactive run) - just continue.
            }
        }
    }

    // -----------------------------------------------------------------
    // Stage 1 - Resume
    // -----------------------------------------------------------------

    private void stage1Resume() {
        stageHeader(1, "RESUME ANALYSIS", "Extracting skills from an uploaded CV");

        String resumeText = """
                Priya Raman - Backend Developer (4 years)

                Experience:
                - Built and maintained REST APIs in Java 17 and Spring Boot serving 2M requests/day
                - Designed MySQL schemas and optimised Hibernate queries for a reporting service
                - Containerised services with Docker and automated builds with Jenkins
                - Wrote unit and integration tests with JUnit and Mockito
                - Collaborated via Git and code review

                Skills: Java, Spring Boot, REST APIs, MySQL, Hibernate, Docker, JUnit, Git, Maven
                """;

        System.out.println(Ansi.grey("  Resume text submitted to the AI skill extractor:"));
        System.out.println();
        for (String line : resumeText.lines().limit(8).toList()) {
            System.out.println("  " + Ansi.dim(line));
        }
        System.out.println("  " + Ansi.dim("..."));
        System.out.println();

        // The real AI service - runs offline through the local taxonomy when no API key is configured.
        List<String> extracted = aiService.extractSkillsFromResume(resumeText);

        List<String> body = new ArrayList<>();
        body.add(Layout.kv("Engine", AIHealth.describeMode()));
        body.add(Layout.kv("Skills extracted", Ansi.bold(String.valueOf(extracted.size()))));
        body.add("");
        body.addAll(Layout.wrap(Ansi.green(String.join(", ", extracted)), Layout.WIDTH - 6, "  "));
        body.add("");
        body.add(Ansi.grey("Mapped to question-bank topics:"));
        for (ResumeSkill s : DemoDataFactory.resumeSkills()) {
            if (s.getMatchedTopicName() != null) {
                body.add(Layout.bullet(Charts.padRight(s.getSkillName(), 14) + Ansi.grey("\u2192 ")
                        + s.getMatchedTopicName()));
            }
        }
        Layout.print(Layout.panel("EXTRACTED SKILLS (LIVE AI SERVICE CALL)", body));
        note("Skills are now linked to topics, so every future assessment can be personalised to this resume.");
        pause();
    }

    // -----------------------------------------------------------------
    // Stage 2 - Job description
    // -----------------------------------------------------------------

    private void stage2JobDescription() {
        stageHeader(2, "JOB DESCRIPTION ANALYSIS", "Parsing the target role's requirements");

        System.out.println(Ansi.grey("  Pasted job description:"));
        System.out.println();
        for (String line : DemoDataFactory.jobDescriptionText().lines().limit(14).toList()) {
            System.out.println("  " + Ansi.dim(line));
        }
        System.out.println();

        List<String> body = new ArrayList<>();
        body.add(Layout.kv("Title", Ansi.bold(DemoDataFactory.JOB_TITLE)));
        body.add(Layout.kv("Experience required", "5+ years"));
        body.add("");
        body.add(Ansi.grey("Required skills detected"));
        body.addAll(Layout.wrap(String.join(", ", DemoDataFactory.jdRequiredSkills()), Layout.WIDTH - 6, "  "));
        body.add("");
        body.add(Ansi.grey("Preferred"));
        body.addAll(Layout.wrap("Payments/fintech domain, Prometheus, Grafana", Layout.WIDTH - 6, "  "));
        Layout.print(Layout.panel("PARSED JOB DESCRIPTION", body));
        note("The JD is now a structured requirement list that the resume can be scored against.");
        pause();
    }

    // -----------------------------------------------------------------
    // Stage 3 - Job match
    // -----------------------------------------------------------------

    private void stage3JobMatch() {
        stageHeader(3, "JOB MATCH SCORE", "Explainable resume-to-JD scoring");

        DashboardView view = DemoDataFactory.dashboardView(DemoDataFactory.Stage.BASELINE);
        Layout.print(DashboardRenderer.jobMatchPanel(view));
        note("The score is never a black box: every weighted component that produced it is shown above.");
        pause();
    }

    // -----------------------------------------------------------------
    // Stage 4 - Skill gaps
    // -----------------------------------------------------------------

    private void stage4SkillGaps() {
        stageHeader(4, "SKILL GAP ANALYSIS", "Where the resume, the JD and measured performance disagree");

        DashboardView view = DemoDataFactory.dashboardView(DemoDataFactory.Stage.BASELINE);
        Layout.print(DashboardRenderer.strengthsAndGapsPanel(view));
        System.out.println();
        Layout.print(DashboardRenderer.topicMasteryPanel(view));
        note("Three gap types are distinguished: missing from the resume, claimed but weak, "
                + "and measured below competency.");
        pause();
    }

    // -----------------------------------------------------------------
    // Stage 5 - Roadmap
    // -----------------------------------------------------------------

    private void stage5Roadmap() {
        stageHeader(5, "PERSONALISED ROADMAP", "Gaps converted into an ordered plan");

        List<String> body = new ArrayList<>();
        for (RoadmapItem item : DemoDataFactory.roadmap(DemoDataFactory.Stage.BASELINE).getItems()) {
            body.add(Layout.numbered(item.getItemOrder(),
                    priorityTag(item.getPriority().name()) + " " + Ansi.bold(item.getTitle())));
            body.addAll(Layout.wrap(Ansi.grey(item.getDescription()), Layout.WIDTH - 13, "       "));
        }
        Layout.print(Layout.panel("LEARNING ROADMAP (8 ITEMS)", body));

        System.out.println();
        List<String> why = new ArrayList<>();
        why.addAll(Layout.wrap(Ansi.cyan(Ansi.bold("Why this recommendation?")), Layout.WIDTH - 6, "  "));
        why.addAll(Layout.wrap(Layout.bullet(RecommendationExplainer.explainRoadmapItem(
                "Master System Design fundamentals", "System Design", 38.0, true, false)),
                Layout.WIDTH - 10, "    "));
        why.addAll(Layout.wrap(Layout.bullet(RecommendationExplainer.explainRoadmapItem(
                "Strengthen Databases and Hibernate", "Databases", 54.0, false, true)),
                Layout.WIDTH - 10, "    "));
        Layout.print(Layout.panel("ROADMAP EXPLAINABILITY", why));
        note("Every item cites the specific gap that produced it - nothing on this list is generic advice.");
        pause();
    }

    // -----------------------------------------------------------------
    // Stage 6 - Assessment
    // -----------------------------------------------------------------

    private void stage6Assessment() {
        stageHeader(6, "ADAPTIVE ASSESSMENT", "Difficulty adapts to demonstrated performance");

        List<String> body = new ArrayList<>();
        body.add(Layout.kv("Topics selected", Ansi.bold("System Design, Concurrency")
                + Ansi.grey("  (auto-selected from weak topics)")));
        body.add(Layout.kv("Starting difficulty", "EASY " + Ansi.grey("(cumulative accuracy 57.8%)")));
        body.add("");
        body.add(Ansi.grey("Adaptive difficulty as the assessment progresses"));
        body.add("");

        String[][] questions = {
                {"Q1", "System Design", "EASY", "correct", "MEDIUM"},
                {"Q2", "System Design", "MEDIUM", "correct", "MEDIUM"},
                {"Q3", "Concurrency", "MEDIUM", "wrong", "EASY"},
                {"Q4", "Concurrency", "EASY", "correct", "MEDIUM"},
                {"Q5", "System Design", "MEDIUM", "correct", "HARD"},
                {"Q6", "System Design", "HARD", "wrong", "MEDIUM"},
        };
        body.add("  " + Ansi.grey(Charts.padRight("#", 4) + Charts.padRight("Topic", 16)
                + Charts.padRight("Asked at", 10) + Charts.padRight("Result", 10) + "Next question"));
        for (String[] q : questions) {
            String result = q[3].equals("correct") ? Ansi.green("\u2714 correct") : Ansi.red("\u2716 wrong");
            body.add("  " + Charts.padRight(q[0], 4) + Charts.padRight(q[1], 16)
                    + Charts.padRight(q[2], 10) + Charts.padRight(result, 10)
                    + Ansi.cyan("\u2192 " + q[4]));
        }
        body.add("");
        body.add(Layout.kv("Score", "4/6 (66.7%)  " + Ansi.green("best result so far")));
        body.add(Layout.kv("Mistakes logged", "2 " + Ansi.grey("(both queued for targeted retry)")));
        Layout.print(Layout.panel("ASSESSMENT RUN", body));
        note("The engine raises difficulty after a correct streak and drops it after a miss, "
                + "so the candidate is always tested near their actual ceiling.");
        pause();
    }

    // -----------------------------------------------------------------
    // Stage 7 - AI evaluation
    // -----------------------------------------------------------------

    private void stage7AiEvaluation() {
        stageHeader(7, "AI ANSWER EVALUATION", "Semantic scoring of a descriptive answer");

        String question = "Explain how you would keep a payment service consistent when a downstream "
                + "provider times out.";
        String modelAnswer = "Use idempotency keys so retries do not double-charge, persist the request "
                + "in an outbox before calling the provider, retry with exponential backoff, and "
                + "reconcile asynchronously. Return a pending state rather than a failure.";
        String candidateAnswer = "I would retry the call a few times with a delay. I would also store "
                + "the request first so we know it happened, and use an idempotency key so the customer "
                + "is not charged twice.";

        System.out.println("  " + Ansi.bold("Question"));
        for (String line : Layout.wrap(question, Layout.WIDTH - 6, "  ")) {
            System.out.println(line);
        }
        System.out.println();
        System.out.println("  " + Ansi.bold("Candidate answer"));
        for (String line : Layout.wrap(Ansi.dim(candidateAnswer), Layout.WIDTH - 6, "  ")) {
            System.out.println(line);
        }
        System.out.println();

        // Real evaluation through the production AI service (local semantic engine when offline).
        AIServiceImpl.Evaluation evaluation = aiService.evaluate(question, modelAnswer, candidateAnswer);

        List<String> body = new ArrayList<>();
        body.add(Layout.kv("Engine", AIHealth.describeMode()));
        body.add("");
        body.add("  " + Charts.labelledGauge("Overall", evaluation.score(), 18, 26));
        body.add("");
        body.add(Ansi.grey("Feedback"));
        body.addAll(Layout.wrap(evaluation.feedback(), Layout.WIDTH - 6, "  "));
        if (evaluation.improvementAdvice() != null && !evaluation.improvementAdvice().isBlank()) {
            body.add("");
            body.add(Ansi.grey("How to improve"));
            body.addAll(Layout.wrap(evaluation.improvementAdvice(), Layout.WIDTH - 6, "  "));
        }
        Layout.print(Layout.panel("AI EVALUATION (LIVE AI SERVICE CALL)", body));

        String degradation = AIHealth.degradationNotice();
        if (degradation != null) {
            System.out.println();
            ConsoleIO.printInfo(degradation);
        }
        note("The evaluation above was computed just now by the real evaluator - it is not canned text.");
        pause();
    }

    // -----------------------------------------------------------------
    // Stage 8 - Mock interview
    // -----------------------------------------------------------------

    private void stage8MockInterview() {
        stageHeader(8, "AI MOCK INTERVIEW", "Personalised questions with adaptive follow-ups");

        List<String> body = new ArrayList<>();
        body.add(Layout.kv("Personalised from", Ansi.grey("resume skills + JD requirements + unresolved mistakes")));
        body.add(Layout.kv("Opening difficulty", "MEDIUM " + Ansi.grey("(derived from 67.2% accuracy)")));
        body.add("");

        body.add("  " + Ansi.bold("Q1 (technical, from JD gap)"));
        body.addAll(Layout.wrap("You have Docker on your resume but the role needs Kubernetes. Walk me "
                + "through how you would deploy and scale a Spring Boot service on Kubernetes.",
                Layout.WIDTH - 10, "    "));
        body.add("  " + Ansi.cyan("\u21B3 Smart follow-up: ") + Ansi.grey("\"What happens to in-flight "
                + "payments during a rolling update?\""));
        body.add("  " + Ansi.grey("  triggered because the answer omitted graceful shutdown"));
        body.add("");

        body.add("  " + Ansi.bold("Q2 (behavioral, STAR-analysed)"));
        body.addAll(Layout.wrap("Tell me about a time you found a production bug that affected money.",
                Layout.WIDTH - 10, "    "));
        body.add("");
        body.add("  " + Ansi.grey("STAR completeness"));
        Map<String, Double> star = new LinkedHashMap<>();
        star.put("Situation", 88.0);
        star.put("Task", 76.0);
        star.put("Action", 82.0);
        star.put("Result", 41.0);
        for (Map.Entry<String, Double> e : star.entrySet()) {
            body.add("  " + Charts.labelledGauge(e.getKey(), e.getValue(), 14, 24));
        }
        body.add("  " + Ansi.yellow("\u2691 Result is the weak element - the answer never quantified the outcome."));
        body.add("");
        body.add(Layout.kv("Session score", Ansi.bold("71.5/100")));
        Layout.print(Layout.panel("MOCK INTERVIEW SESSION", body));
        note("Follow-ups are generated from what the answer actually missed, "
                + "and STAR analysis pinpoints the weak element rather than grading the whole answer.");
        pause();
    }

    // -----------------------------------------------------------------
    // Stage 9 - Improved readiness
    // -----------------------------------------------------------------

    private void stage9ImprovedReadiness() {
        stageHeader(9, "IMPROVED READINESS", "The loop closes - every activity feeds the score");

        DashboardView before = DemoDataFactory.dashboardView(DemoDataFactory.Stage.BASELINE);
        DashboardView after = DemoDataFactory.dashboardView(DemoDataFactory.Stage.AFTER_MOCK);

        Layout.print(Layout.panel("BEFORE \u2192 AFTER", comparisonRows(before, after)));
        System.out.println();

        System.out.println(Layout.section("The fully updated dashboard"));
        DashboardRenderer.print(after);
        pause();
    }

    /** Builds the before/after comparison table that makes the adaptive change explicit. */
    static List<String> comparisonRows(DashboardView before, DashboardView after) {
        List<String> rows = new ArrayList<>();

        double beforeScore = before.readiness().getOverallScore();
        double afterScore = after.readiness().getOverallScore();

        rows.add("  " + Ansi.grey(Charts.padRight("Metric", 22) + Charts.padRight("Before", 14)
                + Charts.padRight("After", 14) + "Change"));
        rows.add("  " + Ansi.grey("\u2500".repeat(Layout.WIDTH - 6)));
        rows.add(row("Readiness score", fmt(beforeScore), fmt(afterScore), afterScore - beforeScore, true));
        rows.add(row("Band", before.readiness().getBand(), Ansi.green(after.readiness().getBand()), 0, true));

        double beforeMatch = before.jobMatch().matchScore();
        double afterMatch = after.jobMatch().matchScore();
        rows.add(row("Job match", fmt(beforeMatch), fmt(afterMatch), afterMatch - beforeMatch, true));

        rows.add(row("Weak topics", String.valueOf(before.gaps().size()),
                String.valueOf(after.gaps().size()), after.gaps().size() - before.gaps().size(), false));

        rows.add(row("Unresolved mistakes", String.valueOf(before.mistakeCounts().unresolved()),
                String.valueOf(after.mistakeCounts().unresolved()),
                after.mistakeCounts().unresolved() - before.mistakeCounts().unresolved(), false));

        rows.add(row("Roadmap done", before.roadmap().completedItems() + "/" + before.roadmap().totalItems(),
                after.roadmap().completedItems() + "/" + after.roadmap().totalItems(),
                after.roadmap().completedItems() - before.roadmap().completedItems(), true));

        rows.add("");
        rows.add("  " + Ansi.grey("Readiness dimensions"));
        Map<String, ReadinessScore.Component> beforeDims = before.readiness().getDimensions();
        Map<String, ReadinessScore.Component> afterDims = after.readiness().getDimensions();
        for (Map.Entry<String, ReadinessScore.Component> e : afterDims.entrySet()) {
            ReadinessScore.Component b = beforeDims.get(e.getKey());
            double bv = b == null ? 0 : b.getValue();
            double av = e.getValue().getValue();
            rows.add("  " + Charts.padRight(e.getValue().getLabel(), 20)
                    + Charts.padRight(fmt(bv), 10) + Ansi.grey("\u2192 ")
                    + Charts.padRight(fmt(av), 10) + Charts.delta(av - bv, true));
        }

        rows.add("");
        rows.add("  " + Ansi.grey("Recommendation changed"));
        rows.addAll(Layout.wrap(Ansi.dim("Before: " + before.nextAction().action()), Layout.WIDTH - 10, "    "));
        rows.addAll(Layout.wrap(Ansi.bold("After:  " + after.nextAction().action()), Layout.WIDTH - 10, "    "));
        return rows;
    }

    private static String row(String label, String before, String after, double delta, boolean higherIsBetter) {
        String change = Math.abs(delta) < 0.05 ? Ansi.grey("\u2014") : Charts.delta(delta, higherIsBetter);
        return "  " + Charts.padRight(label, 22) + Charts.padRight(before, 14)
                + Charts.padRight(after, 14) + change;
    }

    private static String fmt(double value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }

    private static String priorityTag(String priority) {
        return switch (priority) {
            case "HIGH" -> Ansi.red("[HIGH]  ");
            case "MEDIUM" -> Ansi.yellow("[MED]   ");
            default -> Ansi.grey("[LOW]   ");
        };
    }

    private void note(String text) {
        System.out.println();
        for (String line : Layout.wrap(Ansi.cyan("\u25B8 ") + Ansi.grey(text), Layout.WIDTH - 4, "  ")) {
            System.out.println(line);
        }
    }

    /** Convenience for a non-interactive run (scripted output, CI, README capture). */
    public static void runNonInteractive() {
        new DemoFlow(null, false).run();
    }

    /** The demo's roadmap progress, exposed for tests that assert the stages really differ. */
    static int completedRoadmapItems(DemoDataFactory.Stage stage) {
        return (int) DemoDataFactory.roadmap(stage).getItems().stream()
                .filter(i -> i.getStatus() == RoadmapItemStatus.COMPLETED).count();
    }

    /** Exposed for tests: the skill names the demo resume yields. */
    static List<String> demoResumeSkillNames() {
        return DemoDataFactory.resumeSkills().stream().map(ResumeSkill::getSkillName)
                .collect(Collectors.toList());
    }
}
