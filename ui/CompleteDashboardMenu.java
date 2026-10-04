package com.careerintelligence.ui;

import com.careerintelligence.model.User;
import com.careerintelligence.service.DashboardView;
import com.careerintelligence.service.IntegratedDashboardService;

import java.sql.SQLException;
import java.util.Scanner;

/**
 * UI for the consolidated InterviewMentor Dashboard: one view of
 * resume, job match, assessments, AI evaluations, mistakes, mock
 * interviews, readiness, roadmap, pipeline updates and achievements.
 *
 * <p>The screen itself is now purely a controller. It loads a
 * {@link DashboardView} from {@link IntegratedDashboardService} and hands
 * it to {@link DashboardRenderer}, which owns all layout and charting.
 * That split keeps the visual layer unit-testable and lets the guided demo
 * render identical dashboards from in-memory data.
 *
 * <p>The per-feature "Performance Dashboard" (main menu option 4) is
 * preserved untouched; this remains the higher-level summary.
 */
public class CompleteDashboardMenu {

    private final Scanner scanner;
    private final IntegratedDashboardService integratedDashboardService;

    public CompleteDashboardMenu(Scanner scanner, IntegratedDashboardService integratedDashboardService) {
        this.scanner = scanner;
        this.integratedDashboardService = integratedDashboardService;
    }

    public void show(User user) {
        try {
            DashboardView view = integratedDashboardService.buildView(user.getUserId(), user.getFullName());
            DashboardRenderer.print(view);
            printFooterHints(view);
        } catch (SQLException e) {
            ConsoleIO.printError("Could not load your dashboard: " + e.getMessage());
            System.out.println();
            System.out.println("  This usually means the database connection dropped or the schema is out of date.");
            System.out.println("  Verify MySQL is running and that database/schema.sql plus");
            System.out.println("  database/migration_v2_job_intelligence.sql have both been applied.");
        } catch (RuntimeException e) {
            // A rendering or data-shape problem should never look like a crash to the user.
            ConsoleIO.printError("Unexpected problem rendering the dashboard: " + e.getMessage());
            ConsoleIO.printInfo("Your data is safe - try again, or use option 4 for the basic performance view.");
        }
        ConsoleIO.pause(scanner);
    }

    /** Context-sensitive hints that point at whichever screen would unlock the most missing data. */
    private void printFooterHints(DashboardView view) {
        System.out.println();
        if (view.isEmptyProfile()) {
            ConsoleIO.printInfo("Tip: run the Guided Demo from the main menu to see a fully populated dashboard.");
            return;
        }
        if (view.jobMatch() == null || !view.jobMatch().isPresent()) {
            ConsoleIO.printInfo("Tip: upload a job description or paste one under \"Resume & Skills\" to unlock the Job Match panel.");
        } else if (view.mockInterviews() != null && view.mockInterviews().completedSessions() == 0) {
            ConsoleIO.printInfo("Tip: an AI Mock Interview is the fastest way to raise your two lowest dimensions.");
        }
    }
}
