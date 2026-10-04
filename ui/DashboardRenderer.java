package com.careerintelligence.ui;

import com.careerintelligence.model.ReadinessScore;
import com.careerintelligence.model.ResumeSkill;
import com.careerintelligence.model.UserBadge;
import com.careerintelligence.service.DashboardView;
import com.careerintelligence.service.RecommendationExplainer;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Draws the Complete Dashboard from a {@link DashboardView}.
 *
 * <p>The renderer is deliberately side-effect free apart from the final
 * print: {@link #render(DashboardView)} returns the dashboard as a list of
 * lines, so the layout can be asserted in unit tests, captured for the
 * README, or replayed by the guided demo. {@link #print(DashboardView)} is
 * the thin wrapper that actually writes to stdout.
 *
 * <p>Every section degrades to a helpful empty state rather than an empty
 * heading, so a brand-new account sees "what to do to unlock this" instead
 * of a blank panel.
 */
public final class DashboardRenderer {

    private static final int BAR = 22;
    private static final int LABEL = 20;

    private DashboardRenderer() {
    }

    public static void print(DashboardView view) {
        Layout.print(render(view));
    }

    /** Renders the entire dashboard as displayable lines. */
    public static List<String> render(DashboardView view) {
        List<String> out = new ArrayList<>();
        out.add("");
        out.addAll(Layout.banner(
                "INTERVIEWMENTOR DASHBOARD  \u2022  " + safe(view.candidateName(), "Candidate"),
                "Target role: " + safe(view.targetRole(), "not set (see My Profile)")
                        + "   \u2022   AI engine: " + safe(view.aiMode(), "local semantic engine")));

        if (view.isEmptyProfile()) {
            out.addAll(onboardingPanel(view));
            return out;
        }

        out.add("");
        out.addAll(readinessPanel(view));
        out.add("");
        out.addAll(jobMatchPanel(view));
        out.add("");
        out.addAll(strengthsAndGapsPanel(view));
        out.add("");
        out.addAll(topicMasteryPanel(view));
        out.add("");
        out.addAll(performancePanel(view));
        out.add("");
        out.addAll(roadmapPanel(view));
        out.add("");
        out.addAll(careerUpdatesPanel(view));
        out.add("");
        out.addAll(nextActionPanel(view));
        out.add("");
        out.addAll(achievementsPanel(view));
        return out;
    }

    // -----------------------------------------------------------------
    // Sections
    // -----------------------------------------------------------------

    /** Shown when the account has no resume, assessments or mock interviews yet. */
    public static List<String> onboardingPanel(DashboardView view) {
        List<String> body = new ArrayList<>();
        body.add(Ansi.yellow("Your dashboard is waiting for its first signal."));
        body.add("");
        body.add("The system builds every score below from data you generate, so nothing is");
        body.add("shown until there is something real to measure. Start here:");
        body.add("");
        body.add(Layout.numbered(1, "Resume & Skills \u2192 upload a PDF/DOCX/TXT resume (extracts your skills)"));
        body.add(Layout.numbered(2, "Resume & Skills \u2192 paste a target job description (unlocks Job Match)"));
        body.add(Layout.numbered(3, "Start New Assessment \u2192 establishes your performance baseline"));
        body.add(Layout.numbered(4, "AI Mock Interview \u2192 scores communication and behavioral readiness"));
        body.add("");
        body.add(Ansi.grey("Tip: run the Guided Demo from the main menu to see a fully populated"));
        body.add(Ansi.grey("dashboard with realistic data before entering your own."));
        return Layout.panel("GETTING STARTED", body);
    }

    public static List<String> readinessPanel(DashboardView view) {
        List<String> body = new ArrayList<>();
        ReadinessScore r = view.readiness();
        if (r == null) {
            body.add(emptyState("No readiness score yet - complete an assessment or mock interview to generate one."));
            return Layout.panel("INTERVIEW READINESS", body);
        }

        body.add(Ansi.bold("Overall  ") + Charts.gauge(r.getOverallScore(), 30)
                + "   " + bandBadge(r.getBand()));
        body.add(Layout.kv("Trend", Charts.trendLine(view.readinessSeries(), view.readinessTrendCaption()), 10));
        body.add("");
        body.add(Ansi.grey("Readiness dimensions"));

        Map<String, ReadinessScore.Component> dims = r.getDimensions();
        if (dims == null || dims.isEmpty()) {
            body.add(emptyState("Dimension breakdown appears once you have both assessment and interview data."));
        } else {
            for (Map.Entry<String, ReadinessScore.Component> e : dims.entrySet()) {
                ReadinessScore.Component c = e.getValue();
                body.add("  " + Charts.labelledGauge(c.getLabel(), c.getValue(), LABEL, BAR));
            }
        }
        if (r.getBiggestStrength() != null || r.getBiggestRisk() != null) {
            body.add("");
            if (r.getBiggestStrength() != null) {
                body.add(Layout.kv("Biggest strength", Ansi.green(r.getBiggestStrength())));
            }
            if (r.getBiggestRisk() != null) {
                body.add(Layout.kv("Biggest risk", Ansi.red(r.getBiggestRisk())));
            }
        }
        return Layout.panel("INTERVIEW READINESS", body);
    }

    public static List<String> jobMatchPanel(DashboardView view) {
        List<String> body = new ArrayList<>();
        DashboardView.JobMatchView jm = view.jobMatch();
        if (jm == null || !jm.isPresent()) {
            body.add(emptyState("No job description analysed yet."));
            body.add(Ansi.grey("  Upload or paste a JD under \"Resume & Skills\" to get an explainable match score,"));
            body.add(Ansi.grey("  a skill-by-skill breakdown, and JD-driven roadmap priorities."));
            return Layout.panel("JOB MATCH", body);
        }

        body.add(Ansi.bold("Match    ") + Charts.gauge(jm.matchScore(), 30)
                + "   " + Ansi.grey(safe(jm.jobTitle(), "target role")));
        if (jm.breakdown() != null && !jm.breakdown().isEmpty()) {
            body.add("");
            body.add(Ansi.grey("How the match score was computed"));
            for (ReadinessScore.Component c : jm.breakdown()) {
                body.add("  " + Charts.labelledGauge(c.getLabel(), c.getValue(), LABEL, BAR)
                        + Ansi.grey("  w=" + c.getWeight()));
            }
        }
        body.add("");
        body.add(skillLine("Strong match", jm.strongMatches(), Ansi.green("\u2714")));
        body.add(skillLine("Partial match", jm.partialMatches(), Ansi.yellow("\u25D1")));
        body.add(skillLine("Missing", jm.missing(), Ansi.red("\u2716")));
        if (jm.highPrioritySkills() != null && !jm.highPrioritySkills().isEmpty()) {
            body.add(Layout.kv("Close these first", Ansi.bold(String.join(", ", jm.highPrioritySkills()))));
        }
        if (jm.summary() != null && !jm.summary().isBlank()) {
            body.add("");
            body.addAll(Layout.wrap(Ansi.grey(jm.summary()), Layout.WIDTH - 6, "  "));
        }
        return Layout.panel("JOB MATCH", body);
    }

    public static List<String> strengthsAndGapsPanel(DashboardView view) {
        List<String> body = new ArrayList<>();

        List<DashboardView.TopicMastery> strengths = view.strengths();
        body.add(Ansi.green(Ansi.bold("Strengths")));
        if (strengths.isEmpty()) {
            body.add(emptyState("No topic is above " + (int) DashboardView.STRENGTH_THRESHOLD
                    + "% accuracy yet - keep practising."));
        } else {
            for (DashboardView.TopicMastery t : strengths.stream().limit(4).toList()) {
                body.add("  " + Charts.barRow(t.topicName(), t.accuracyPercent(), 100.0, LABEL, BAR,
                        String.format(Locale.ROOT, "%5.1f%%  %s", t.accuracyPercent(), trendTag(t.trend()))));
            }
        }

        body.add("");
        body.add(Ansi.red(Ansi.bold("Skill gaps")));
        List<DashboardView.TopicMastery> gaps = view.gaps();
        if (gaps.isEmpty() && (view.missingSkills() == null || view.missingSkills().isEmpty())) {
            body.add(emptyState("No gaps detected against your current target role."));
        } else {
            for (DashboardView.TopicMastery t : gaps.stream().limit(4).toList()) {
                body.add("  " + Charts.barRow(t.topicName(), t.accuracyPercent(), 100.0, LABEL, BAR,
                        String.format(Locale.ROOT, "%5.1f%%  %s", t.accuracyPercent(), trendTag(t.trend()))));
            }
            if (view.missingSkills() != null && !view.missingSkills().isEmpty()) {
                body.add(Layout.kv("Missing from resume", Ansi.red(String.join(", ", view.missingSkills()))));
            }
            if (view.weakSkills() != null && !view.weakSkills().isEmpty()) {
                body.add(Layout.kv("Claimed but weak", Ansi.yellow(String.join(", ", view.weakSkills()))));
            }
        }

        if (view.resumeSkills() != null && !view.resumeSkills().isEmpty()) {
            body.add("");
            body.add(Layout.kv("Resume skills (" + view.resumeSkills().size() + ")",
                    Ansi.grey(view.resumeSkills().stream().map(ResumeSkill::getSkillName)
                            .limit(10).collect(Collectors.joining(", ")))));
        }
        return Layout.panel("STRENGTHS & SKILL GAPS", body);
    }

    public static List<String> topicMasteryPanel(DashboardView view) {
        List<String> body = new ArrayList<>();
        List<DashboardView.TopicMastery> rows = view.topicMastery();
        if (rows == null || rows.isEmpty()) {
            body.add(emptyState("No topic has been attempted yet - take an assessment to build your mastery map."));
            return Layout.panel("TOPIC MASTERY", body);
        }
        for (DashboardView.TopicMastery t : rows.stream().limit(8).toList()) {
            body.add(Charts.barRow(t.topicName(), t.accuracyPercent(), 100.0, LABEL, BAR,
                    String.format(Locale.ROOT, "%5.1f%%  %s  %s",
                            t.accuracyPercent(), Ansi.grey(t.attempts() + " att"), trendTag(t.trend()))));
        }
        body.add("");
        body.add(Ansi.grey("  Legend: " + Ansi.green("\u25B2 improving") + "  "
                + Ansi.grey("\u25CF steady") + "  " + Ansi.red("\u25BC declining")
                + "   \u2022  gap < " + (int) DashboardView.MASTERY_THRESHOLD
                + "%  \u2022  strength \u2265 " + (int) DashboardView.STRENGTH_THRESHOLD + "%"));
        return Layout.panel("TOPIC MASTERY", body);
    }

    public static List<String> performancePanel(DashboardView view) {
        List<String> body = new ArrayList<>();

        body.add(Ansi.bold("Assessments"));
        if (view.assessmentStats() == null || view.assessmentStats().getTotalAssessments() == 0) {
            body.add(emptyState("No assessments completed yet."));
        } else {
            var s = view.assessmentStats();
            body.add("  " + Charts.labelledGauge("Average score", s.getAverageScorePercent(), LABEL, BAR));
            body.add("  " + Charts.labelledGauge("Best score", s.getBestScorePercent(), LABEL, BAR));
            body.add("  " + Charts.labelledGauge("Accuracy", s.getOverallAccuracyPercent(), LABEL, BAR));
            body.add(Layout.kv("Completed", s.getTotalAssessments() + " assessment(s)   "
                    + Ansi.green(s.getTotalCorrect() + " correct") + "  "
                    + Ansi.red(s.getTotalWrong() + " wrong") + "  "
                    + Ansi.grey(s.getTotalUnanswered() + " unanswered")));
        }

        body.add("");
        body.add(Ansi.bold("Mock interviews"));
        DashboardView.MockView m = view.mockInterviews();
        if (m == null || m.completedSessions() == 0) {
            body.add(emptyState("No mock interview completed yet - this is what scores Communication "
                    + "and Behavioral readiness."));
        } else {
            body.add(Layout.kv("Sessions", m.completedSessions() + " completed of " + m.totalSessions() + " started"));
            if (m.lastScore() != null) {
                body.add("  " + Charts.labelledGauge("Latest score", m.lastScore(), LABEL, BAR));
            }
        }

        if (view.mistakeCounts() != null && view.mistakeCounts().total() > 0) {
            body.add("");
            body.add(Ansi.bold("Mistake resolution"));
            body.add("  " + Charts.padRight("Resolved", LABEL) + "  "
                    + Charts.fraction(view.mistakeCounts().resolved(), view.mistakeCounts().total(), BAR));
            if (view.mistakeCounts().unresolved() > 0) {
                body.add(Ansi.grey("  " + view.mistakeCounts().unresolved()
                        + " unresolved mistake(s) still feeding your weak-topic profile."));
            }
        }
        return Layout.panel("ASSESSMENTS & MOCK INTERVIEWS", body);
    }

    public static List<String> roadmapPanel(DashboardView view) {
        List<String> body = new ArrayList<>();
        DashboardView.RoadmapView r = view.roadmap();
        if (r == null || r.totalItems() == 0) {
            body.add(emptyState("No roadmap generated yet - open \"Learning Roadmap\" to build one "
                    + "from your current gaps."));
            return Layout.panel("ROADMAP PROGRESS", body);
        }
        body.add("  " + Charts.padRight("Completion", LABEL) + "  "
                + Charts.fraction(r.completedItems(), r.totalItems(), BAR));
        if (r.topPriorities() != null && !r.topPriorities().isEmpty()) {
            body.add("");
            body.add(Ansi.grey("Top outstanding priorities"));
            int i = 1;
            for (String p : r.topPriorities()) {
                body.add(Layout.numbered(i++, p));
            }
        }
        if (r.summary() != null && !r.summary().isBlank()) {
            body.add("");
            body.addAll(Layout.wrap(Ansi.grey(r.summary()), Layout.WIDTH - 6, "  "));
        }
        return Layout.panel("ROADMAP PROGRESS", body);
    }

    public static List<String> careerUpdatesPanel(DashboardView view) {
        List<String> body = new ArrayList<>();
        List<String> updates = view.careerUpdates();
        if (updates == null || updates.isEmpty()) {
            body.add(emptyState("No pipeline updates recorded yet."));
            body.add(Ansi.grey("  Every completed assessment, retry, mock interview or JD analysis writes"));
            body.add(Ansi.grey("  a \"what changed and why\" entry here."));
            return Layout.panel("INTERVIEWMENTOR UPDATES", body);
        }
        int i = 1;
        for (String u : updates.stream().limit(4).toList()) {
            body.addAll(Layout.wrap(Ansi.cyan(i + ". ") + u, Layout.WIDTH - 6, "  "));
            i++;
        }
        return Layout.panel("INTERVIEWMENTOR UPDATES", body);
    }

    /** The explainable "Recommended Next Action" panel - the headline feature of the dashboard. */
    public static List<String> nextActionPanel(DashboardView view) {
        List<String> body = new ArrayList<>();
        RecommendationExplainer.Explanation ex = view.nextAction();
        if (ex == null) {
            body.add(emptyState("No recommendation available yet."));
            return Layout.panel("RECOMMENDED NEXT ACTION", body);
        }

        body.addAll(Layout.wrap(Ansi.bold(Ansi.white("\u27A4 " + ex.action())), Layout.WIDTH - 6, "  "));
        body.add("");
        body.add(Ansi.cyan(Ansi.bold("Why this recommendation?")));
        if (!ex.hasEvidence()) {
            body.add(emptyState("Not enough data yet to explain this - it is a default onboarding step."));
        } else {
            for (String w : ex.why()) {
                body.addAll(Layout.wrap(Layout.bullet(w), Layout.WIDTH - 10, "    "));
            }
        }
        body.add("");
        body.add(Ansi.cyan(Ansi.bold("What should I do next?")));
        int i = 1;
        for (String s : ex.whatNext()) {
            body.addAll(Layout.wrap(Layout.numbered(i++, s), Layout.WIDTH - 10, "    "));
        }
        return Layout.panel("RECOMMENDED NEXT ACTION", body);
    }

    public static List<String> achievementsPanel(DashboardView view) {
        List<String> body = new ArrayList<>();
        if (view.points() == null) {
            body.add(emptyState("No achievements yet."));
            return Layout.panel("ACHIEVEMENTS", body);
        }
        var p = view.points();
        body.add(Layout.kv("Points", Ansi.bold(String.valueOf(p.getTotalPoints()))
                + Ansi.grey("  (Level " + p.getLevel() + ")")));
        body.add(Layout.kv("Practice streak", p.getCurrentStreakDays() + " day(s)"
                + Ansi.grey("  longest " + p.getLongestStreakDays())));
        if (view.badges() == null || view.badges().isEmpty()) {
            body.add(Layout.kv("Badges", Ansi.grey("none earned yet")));
        } else {
            body.add(Layout.kv("Badges (" + view.badges().size() + ")",
                    view.badges().stream().map(UserBadge::getBadgeName).collect(Collectors.joining(", "))));
        }
        return Layout.panel("ACHIEVEMENTS", body);
    }

    // -----------------------------------------------------------------
    // Small helpers
    // -----------------------------------------------------------------

    private static String skillLine(String label, List<String> skills, String marker) {
        if (skills == null || skills.isEmpty()) {
            return Layout.kv(label, Ansi.grey("none"));
        }
        String shown = skills.stream().limit(6).collect(Collectors.joining(", "));
        if (skills.size() > 6) {
            shown += " (+" + (skills.size() - 6) + " more)";
        }
        return Layout.kv(marker + " " + label, shown);
    }

    private static String trendTag(String trend) {
        if (trend == null) {
            return "";
        }
        return switch (trend.toUpperCase(Locale.ROOT)) {
            case "IMPROVING", "UP" -> Ansi.green("\u25B2");
            case "DECLINING", "DOWN" -> Ansi.red("\u25BC");
            default -> Ansi.grey("\u25CF");
        };
    }

    private static String bandBadge(String band) {
        if (band == null) {
            return "";
        }
        String text = " " + band.toUpperCase(Locale.ROOT) + " ";
        String upper = band.toUpperCase(Locale.ROOT);
        if (upper.contains("READY") && !upper.contains("NOT")) {
            return Ansi.green(Ansi.bold(text));
        }
        if (upper.contains("ALMOST") || upper.contains("DEVELOP") || upper.contains("PROGRESS")) {
            return Ansi.yellow(Ansi.bold(text));
        }
        return Ansi.red(Ansi.bold(text));
    }

    private static String emptyState(String message) {
        return "  " + Ansi.grey("\u2014 " + message);
    }

    private static String safe(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    /** Exposed for the demo runner so it can reuse the dashboard's dimension chart. */
    public static List<String> dimensionChart(Map<String, ReadinessScore.Component> dimensions) {
        List<String> out = new ArrayList<>();
        if (dimensions == null || dimensions.isEmpty()) {
            return out;
        }
        Map<String, ReadinessScore.Component> ordered = new LinkedHashMap<>(dimensions);
        for (ReadinessScore.Component c : ordered.values()) {
            out.add("  " + Charts.labelledGauge(c.getLabel(), c.getValue(), LABEL, BAR));
        }
        return out;
    }
}
