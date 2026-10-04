package com.careerintelligence.ui;

import com.careerintelligence.demo.DemoFlow;
import com.careerintelligence.model.User;
import com.careerintelligence.service.AssessmentService;
import com.careerintelligence.service.AuthService;
import com.careerintelligence.service.CareerIntelligenceService;
import com.careerintelligence.service.DashboardService;
import com.careerintelligence.service.GamificationService;
import com.careerintelligence.service.IntegratedDashboardService;
import com.careerintelligence.service.MistakeAnalyzerService;
import com.careerintelligence.service.MockInterviewService;
import com.careerintelligence.service.ProgressAnalyticsService;
import com.careerintelligence.service.ReadinessScoreService;
import com.careerintelligence.service.ResumeService;
import com.careerintelligence.service.RoadmapService;
import com.careerintelligence.service.RolePrepService;

import java.util.Scanner;

public class MainMenu {

    private final Scanner scanner;
    private final AuthService authService;
    private final AssessmentService assessmentService;
    private final ProfileMenu profileMenu;
    private final AssessmentMenu assessmentMenu;
    private final MistakeMenu mistakeMenu;
    private final DashboardMenu dashboardMenu;
    private final ResumeMenu resumeMenu;
    private final MockInterviewMenu mockInterviewMenu;
    private final ReadinessMenu readinessMenu;
    private final RoadmapMenu roadmapMenu;
    private final RolePrepMenu rolePrepMenu;
    private final GamificationMenu gamificationMenu;
    private final CompleteDashboardMenu completeDashboardMenu;
    private final ProgressAnalyticsMenu progressAnalyticsMenu;

    public MainMenu(Scanner scanner, AuthService authService, AssessmentService assessmentService) {
        this.scanner = scanner;
        this.authService = authService;
        this.assessmentService = assessmentService;
        GamificationService gamificationService = new GamificationService();
        // Single shared CareerIntelligenceService: the central hub that connects Profile -> Resume ->
        // Skills -> Skill Gap -> Roadmap -> Recommendations -> Adaptive Assessment -> AI Evaluation ->
        // Mistakes -> Practice -> Mock Interview -> Readiness -> Dashboard -> Roadmap Update into one
        // loop. Every menu below that personalises a screen or needs to refresh the roadmap/readiness
        // after an activity shares this exact instance.
        CareerIntelligenceService careerIntelligenceService = new CareerIntelligenceService();
        this.profileMenu = new ProfileMenu(scanner, authService);
        this.assessmentMenu = new AssessmentMenu(scanner, assessmentService, gamificationService, careerIntelligenceService);
        this.mistakeMenu = new MistakeMenu(scanner, new MistakeAnalyzerService(), assessmentService, assessmentMenu);
        this.dashboardMenu = new DashboardMenu(scanner, new DashboardService());
        ResumeService resumeService = new ResumeService();
        this.resumeMenu = new ResumeMenu(scanner, resumeService, assessmentService, assessmentMenu, gamificationService, careerIntelligenceService);
        this.mockInterviewMenu = new MockInterviewMenu(
                scanner, new MockInterviewService(), resumeService, gamificationService, careerIntelligenceService);
        this.readinessMenu = new ReadinessMenu(scanner, new ReadinessScoreService(), gamificationService);
        this.roadmapMenu = new RoadmapMenu(scanner, new RoadmapService(), gamificationService, careerIntelligenceService);
        this.rolePrepMenu = new RolePrepMenu(scanner, new RolePrepService(), gamificationService);
        this.gamificationMenu = new GamificationMenu(scanner, gamificationService);
        this.completeDashboardMenu = new CompleteDashboardMenu(scanner, new IntegratedDashboardService());
        this.progressAnalyticsMenu = new ProgressAnalyticsMenu(scanner, new ProgressAnalyticsService());
    }

    /** Returns when the user logs out. */
    public void show(User user) {
        boolean loggedOut = false;
        while (!loggedOut) {
            printMenu(user);
            int choice = ConsoleIO.promptInt(scanner, "Choose an option", 1, 15);

            switch (choice) {
                case 1 -> resumeMenu.show(user);
                case 2 -> assessmentMenu.startNewAssessment(user);
                case 3 -> mistakeMenu.show(user);
                case 4 -> mockInterviewMenu.show(user);
                case 5 -> completeDashboardMenu.show(user);
                case 6 -> readinessMenu.show(user);
                case 7 -> roadmapMenu.show(user);
                case 8 -> progressAnalyticsMenu.show(user);
                case 9 -> dashboardMenu.show(user);
                case 10 -> assessmentMenu.showHistory(user);
                case 11 -> rolePrepMenu.show(user);
                case 12 -> gamificationMenu.show(user);
                case 13 -> profileMenu.show(user);
                case 14 -> new DemoFlow(scanner, true).run();
                case 15 -> {
                    ConsoleIO.printInfo("Logged out. See you next time, " + user.getFullName() + "!");
                    loggedOut = true;
                }
            }
        }
    }

    /**
     * Renders the main menu grouped by what the user is trying to do,
     * rather than as one flat list of fifteen options. The ordering
     * mirrors the product's own pipeline - build your profile, practise,
     * then review - so the next sensible step is always near the top.
     */
    private void printMenu(User user) {
        ConsoleIO.printHeader("MAIN MENU - " + user.getFullName() + " (" + user.getRole() + ")");

        System.out.println(Ansi.cyan("  BUILD YOUR PROFILE"));
        System.out.println("   1. Resume & Job Description (AI Analysis)");
        System.out.println();
        System.out.println(Ansi.cyan("  PRACTISE"));
        System.out.println("   2. Start New Assessment");
        System.out.println("   3. Mistake Analyzer & Retry");
        System.out.println("   4. AI Mock Interview");
        System.out.println();
        System.out.println(Ansi.cyan("  REVIEW & PLAN"));
        System.out.println("   5. " + Ansi.bold("InterviewMentor Dashboard"));
        System.out.println("   6. Interview Readiness Score");
        System.out.println("   7. Learning Roadmap");
        System.out.println("   8. Progress Analytics");
        System.out.println("   9. Performance Dashboard");
        System.out.println("  10. Assessment History");
        System.out.println();
        System.out.println(Ansi.cyan("  MORE"));
        System.out.println("  11. Role & Company Preparation");
        System.out.println("  12. My Achievements (Points, Badges, Streaks)");
        System.out.println("  13. My Profile");
        System.out.println("  14. " + Ansi.yellow("Guided Demo (no data required)"));
        System.out.println("  15. Logout");
    }
}
