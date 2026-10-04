package com.careerintelligence.ui;

import com.careerintelligence.model.CompanyProfile;
import com.careerintelligence.model.RolePrepQuestion;
import com.careerintelligence.model.User;
import com.careerintelligence.model.UserProfile;
import com.careerintelligence.service.AuthService;
import com.careerintelligence.service.GamificationService;
import com.careerintelligence.service.RolePrepService;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.Scanner;

/**
 * UI for Role & Company Preparation (complete implementation): shows
 * role/company-specific interview questions, a skill-gap analysis against
 * the user's target role, and reference info about the target company.
 */
public class RolePrepMenu {

    private final Scanner scanner;
    private final RolePrepService rolePrepService;
    private final GamificationService gamificationService;
    private final AuthService authService;

    public RolePrepMenu(Scanner scanner, RolePrepService rolePrepService, GamificationService gamificationService) {
        this.scanner = scanner;
        this.rolePrepService = rolePrepService;
        this.gamificationService = gamificationService;
        this.authService = new AuthService();
    }

    public void show(User user) {
        ConsoleIO.printHeader("ROLE & COMPANY PREPARATION");
        try {
            List<String> roles = rolePrepService.getRoleNamesWithPrepQuestions();
            if (roles.isEmpty()) {
                ConsoleIO.printInfo("No role/company preparation content has been added yet. Check back later.");
                ConsoleIO.pause(scanner);
                return;
            }
            Optional<UserProfile> profile = authService.getProfile(user.getUserId());
            String roleName = profile.map(UserProfile::getTargetRole)
                    .filter(r -> r != null && !r.isBlank()).orElse(null);
            String companyName = profile.map(UserProfile::getTargetCompany)
                    .filter(c -> c != null && !c.isBlank()).orElse(null);

            // Reuse the profile when it already contains the candidate's target. Only ask when
            // the app genuinely does not have enough information to personalise the preparation.
            if (roleName == null) {
                System.out.println("Available roles with prep material:");
                for (int i = 0; i < roles.size(); i++) {
                    System.out.println("  " + (i + 1) + ". " + roles.get(i));
                }
                int roleChoice = ConsoleIO.promptInt(scanner, "Choose a role (0 to type your own)", 0, roles.size());
                roleName = roleChoice == 0 ? ConsoleIO.prompt(scanner, "Enter target role") : roles.get(roleChoice - 1);
            } else {
                System.out.println("Preparing for your target role: " + roleName
                        + (companyName != null ? " at " + companyName : ""));
            }
            if (roleName == null || roleName.isBlank()) {
                ConsoleIO.printError("A target role is needed for interview preparation.");
                ConsoleIO.pause(scanner);
                return;
            }

            if (companyName == null) {
                // Company is optional; keep the preparation useful without forcing a setup question.
                System.out.println("\nPreparing general company-neutral interview material.");
            }

            RolePrepService.PrepBundle bundle = rolePrepService.buildPrepBundle(user.getUserId(), roleName, companyName);
            printBundle(bundle);

            GamificationService.AwardResult award = gamificationService.awardRolePrepViewed(user.getUserId());
            GamificationUi.printAward(award);
        } catch (SQLException e) {
            ConsoleIO.printError("Database error: " + e.getMessage());
        }
        ConsoleIO.pause(scanner);
    }

    private void printBundle(RolePrepService.PrepBundle bundle) {
        ConsoleIO.printDivider();
        System.out.println("Target role: " + bundle.roleName()
                + (bundle.companyName() != null ? "  |  Target company: " + bundle.companyName() : ""));

        Optional<CompanyProfile> company = bundle.companyProfile();
        if (company.isPresent()) {
            CompanyProfile c = company.get();
            System.out.println("\n-- Company snapshot --");
            System.out.println("Industry: " + (c.getIndustry() == null ? "N/A" : c.getIndustry()));
            System.out.println("Typical interview process: "
                    + (c.getInterviewProcess() == null ? "N/A" : c.getInterviewProcess()));
            if (c.getNotes() != null && !c.getNotes().isBlank()) {
                System.out.println("Notes: " + c.getNotes());
            }
        }

        if (!bundle.missingSkills().isEmpty()) {
            System.out.println("\n-- Skill gap analysis (vs. your resume) --");
            System.out.println("Skills expected for this role that weren't found on your resume:");
            for (String skill : bundle.missingSkills()) {
                System.out.println("  - " + skill);
            }
        } else {
            System.out.println("\n-- Skill gap analysis --");
            System.out.println("No missing skills detected (either your resume covers this role well, "
                    + "or set a target role + upload a resume under Resume & Skills for a full analysis).");
        }

        System.out.println("\n-- Practice questions --");
        List<RolePrepQuestion> questions = bundle.questions();
        if (questions.isEmpty()) {
            System.out.println("No role/company-specific questions found for this combination yet.");
        } else {
            int i = 1;
            for (RolePrepQuestion q : questions) {
                String scope = q.getCompanyName() == null ? "General" : q.getCompanyName();
                System.out.printf("%d. [%s | %s | %s] %s%n", i++, q.getCategory(), q.getDifficulty(), scope, q.getQuestionText());
            }
        }
    }
}
