package com.careerintelligence.ui;

import com.careerintelligence.dao.TopicDAO;
import com.careerintelligence.model.Assessment;
import com.careerintelligence.model.JobDescription;
import com.careerintelligence.model.ReadinessScore;
import com.careerintelligence.model.ResumeSkill;
import com.careerintelligence.model.Topic;
import com.careerintelligence.model.User;
import com.careerintelligence.service.AssessmentService;
import com.careerintelligence.service.CareerIntelligenceService;
import com.careerintelligence.service.GamificationService;
import com.careerintelligence.service.ResumeService;
import com.careerintelligence.util.ResumeParser;

import java.io.IOException;
import java.io.File;
import java.sql.SQLException;
import java.util.*;
import javax.swing.JFileChooser;
import javax.swing.filechooser.FileNameExtensionFilter;

/**
 * UI for resume intelligence and resume-based interview preparation: upload/parse a resume, show AI-extracted & categorised
 * skills, show missing skills (vs. the target role set in My Profile) and
 * weak skills (vs. topic_performance from the existing assessment engine),
 * start a resume-based assessment built from the topics those skills
 * matched onto in the question bank, and (Job Description Analysis +
 * Resume/JD Matching) upload or paste a job description to see an explainable
 * Job Match Score against it.
 */
public class ResumeMenu {

    private final Scanner scanner;
    private final ResumeService resumeService;
    private final AssessmentService assessmentService;
    private final AssessmentMenu assessmentMenu;
    private final GamificationService gamificationService;
    private final CareerIntelligenceService careerIntelligenceService;
    private final TopicDAO topicDAO = new TopicDAO();

    public ResumeMenu(Scanner scanner, ResumeService resumeService, AssessmentService assessmentService,
                       AssessmentMenu assessmentMenu, GamificationService gamificationService,
                       CareerIntelligenceService careerIntelligenceService) {
        this.scanner = scanner;
        this.resumeService = resumeService;
        this.assessmentService = assessmentService;
        this.assessmentMenu = assessmentMenu;
        this.gamificationService = gamificationService;
        this.careerIntelligenceService = careerIntelligenceService;
    }

    public void show(User user) {
        boolean back = false;
        while (!back) {
            ConsoleIO.printHeader("RESUME & SKILLS");
            System.out.println("1. Upload & Analyze Resume (.pdf / .docx / .txt)");
            System.out.println("2. View Extracted Skills");
            System.out.println("3. View Missing Skills (vs your target role)");
            System.out.println("4. View Weak Skills (vs your assessment performance)");
            System.out.println("5. Start Resume-Based Assessment");
            System.out.println("6. View Resume-Role Match % & Priority Gaps");
            System.out.println("7. Analyze a Job Description & View Job Match Score");
            System.out.println("8. Back to main menu");
            int choice = ConsoleIO.promptInt(scanner, "Choose an option", 1, 8);

            switch (choice) {
                case 1 -> uploadAndAnalyze(user);
                case 2 -> viewSkills(user);
                case 3 -> viewMissingSkills(user);
                case 4 -> viewWeakSkills(user);
                case 5 -> startResumeAssessment(user);
                case 6 -> viewRoleMatch(user);
                case 7 -> analyzeJobDescription(user);
                case 8 -> back = true;
            }
        }
    }

    private void uploadAndAnalyze(User user) {
        ConsoleIO.printHeader("UPLOAD & ANALYZE RESUME");
        System.out.println("Supported formats: .pdf, .docx, .txt");
        System.out.println("A file picker will open. Select your resume to continue.");

        File selectedFile = chooseResumeFile();
        if (selectedFile == null) {
            ConsoleIO.printInfo("Resume upload cancelled.");
            ConsoleIO.pause(scanner);
            return;
        }

        try {
            ResumeService.AnalysisResult result = resumeService.uploadAndAnalyze(user.getUserId(), selectedFile.getAbsolutePath());
            ConsoleIO.printSuccess("Resume analyzed successfully.");
            printSkills(result.skills());

            GamificationService.AwardResult award = gamificationService.awardResumeAnalyzed(user.getUserId());
            GamificationUi.printAward(award);

            List<String> unmatched = resumeService.getUnmatchedSkills(user.getUserId());
            if (!unmatched.isEmpty()) {
                System.out.println();
                System.out.println("These skills don't have matching questions in our question bank yet, "
                        + "so here are some AI-suggested practice questions for self-study:");
                List<String> suggested = resumeService.suggestQuestionsForUnmatchedSkills(user.getUserId(), Math.min(5, unmatched.size() * 2));
                for (String q : suggested) {
                    System.out.println("  - " + q);
                }
            }
        } catch (IOException e) {
            ConsoleIO.printError("Could not read the resume file: " + e.getMessage());
        } catch (SQLException e) {
            ConsoleIO.printError("Database error while saving resume analysis: " + e.getMessage());
        }
        ConsoleIO.pause(scanner);
    }

    /** Opens a native file picker so users can select a resume instead of typing a path. */
    private File chooseResumeFile() {
        return chooseDocumentFile("Select your resume", "Resume files (*.pdf, *.docx, *.txt)");
    }

    /** Opens the same simple file picker for job descriptions, so users never need to type a file path. */
    private File chooseJobDescriptionFile() {
        return chooseDocumentFile("Select a job description", "Job description files (*.pdf, *.docx, *.txt)");
    }

    private File chooseDocumentFile(String title, String filterDescription) {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle(title);
        chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        chooser.setAcceptAllFileFilterUsed(false);
        chooser.setFileFilter(new FileNameExtensionFilter(
                filterDescription, "pdf", "docx", "txt"));
        int result = chooser.showOpenDialog(null);
        return result == JFileChooser.APPROVE_OPTION ? chooser.getSelectedFile() : null;
    }

    private void viewSkills(User user) {
        ConsoleIO.printHeader("EXTRACTED SKILLS");
        try {
            List<ResumeSkill> skills = resumeService.getExtractedSkills(user.getUserId());
            if (skills.isEmpty()) {
                System.out.println("No resume analyzed yet. Choose option 1 to upload one.");
            } else {
                printSkills(skills);
            }
        } catch (SQLException e) {
            ConsoleIO.printError("Database error: " + e.getMessage());
        }
        ConsoleIO.pause(scanner);
    }

    private void printSkills(List<ResumeSkill> skills) {
        if (skills.isEmpty()) {
            System.out.println("No skills were detected in this resume.");
            return;
        }
        Map<String, List<ResumeSkill>> byCategory = new LinkedHashMap<>();
        for (ResumeSkill s : skills) {
            byCategory.computeIfAbsent(s.getCategory(), k -> new ArrayList<>()).add(s);
        }
        for (Map.Entry<String, List<ResumeSkill>> entry : byCategory.entrySet()) {
            System.out.println();
            System.out.println(entry.getKey() + ":");
            for (ResumeSkill s : entry.getValue()) {
                String topicNote = s.getMatchedTopicName() != null
                        ? " (practice topic available: " + s.getMatchedTopicName() + ")"
                        : "";
                System.out.println("  - " + s.getSkillName() + topicNote);
            }
        }
    }

    private void viewMissingSkills(User user) {
        ConsoleIO.printHeader("MISSING SKILLS (vs target role)");
        try {
            List<String> missing = resumeService.getMissingSkills(user.getUserId());
            List<String> availableRoles = resumeService.getAvailableTargetRoles();
            if (missing.isEmpty()) {
                System.out.println("No missing skills found. This can mean either:");
                System.out.println("  - your resume already covers every skill required for your target role, or");
                System.out.println("  - your target role isn't set yet, or has no requirements in our taxonomy.");
                System.out.println();
                System.out.println("Set/update your target role from My Profile -> Edit career profile.");
                System.out.println("Roles currently in our taxonomy: " + String.join(", ", availableRoles));
            } else {
                System.out.println("Skills expected for your target role but not found on your resume:");
                for (String s : missing) {
                    System.out.println("  - " + s);
                }
            }
        } catch (SQLException e) {
            ConsoleIO.printError("Database error: " + e.getMessage());
        }
        ConsoleIO.pause(scanner);
    }

    private void viewRoleMatch(User user) {
        ConsoleIO.printHeader("RESUME-ROLE MATCH %");
        try {
            ResumeService.RoleMatch match = resumeService.computeRoleMatch(user.getUserId());
            if (match.targetRole() == null) {
                System.out.println("No target role set yet. Set one from My Profile -> Edit career profile.");
            } else if (match.matchedSkills().isEmpty() && match.missingSkills().isEmpty()) {
                System.out.println("No skill requirements found in our taxonomy for \"" + match.targetRole() + "\".");
            } else {
                System.out.printf("Target role: %s%n", match.targetRole());
                System.out.printf("Match: %.1f%% (%d matched / %d required)%n", match.matchPercentage(),
                        match.matchedSkills().size(), match.matchedSkills().size() + match.missingSkills().size());
                System.out.println("\nMatched skills: "
                        + (match.matchedSkills().isEmpty() ? "(none)" : String.join(", ", match.matchedSkills())));
                System.out.println("Missing skills: "
                        + (match.missingSkills().isEmpty() ? "(none)" : String.join(", ", match.missingSkills())));

                if (!match.priorityGaps().isEmpty()) {
                    System.out.println("\nTop priority gaps to close first:");
                    int rank = 1;
                    for (ResumeService.PriorityGap gap : match.priorityGaps()) {
                        if (rank > 5) break;
                        System.out.printf("  %d. %s - %s%n", rank++, gap.skillName(), gap.reason());
                    }
                }
            }
        } catch (SQLException e) {
            ConsoleIO.printError("Database error: " + e.getMessage());
        }
        ConsoleIO.pause(scanner);
    }

    private void viewWeakSkills(User user) {
        ConsoleIO.printHeader("WEAK SKILLS (vs assessment performance)");
        try {
            List<String> weak = resumeService.getWeakSkills(user.getUserId());
            if (weak.isEmpty()) {
                System.out.println("No weak skills detected yet. This can mean either you're doing well on "
                        + "the topics matching your resume skills, or you haven't attempted assessments on "
                        + "those topics yet - take a Resume-Based Assessment (option 5) to generate data.");
            } else {
                System.out.println("Skills on your resume where your assessment accuracy is below 60%:");
                for (String s : weak) {
                    System.out.println("  - " + s);
                }
            }
        } catch (SQLException e) {
            ConsoleIO.printError("Database error: " + e.getMessage());
        }
        ConsoleIO.pause(scanner);
    }

    private void analyzeJobDescription(User user) {
        ConsoleIO.printHeader("ANALYZE JOB DESCRIPTION & JOB MATCH SCORE");
        System.out.println("Choose how you want to provide the job description:");
        System.out.println("1. Upload a file (.pdf / .docx / .txt)");
        System.out.println("2. Paste the job description text");
        System.out.println("3. Cancel");
        int inputChoice = ConsoleIO.promptInt(scanner, "Choose an option", 1, 3);

        String jdText;
        if (inputChoice == 3) {
            ConsoleIO.printInfo("Job description analysis cancelled.");
            ConsoleIO.pause(scanner);
            return;
        }

        try {
            if (inputChoice == 1) {
                File selectedFile = chooseJobDescriptionFile();
                if (selectedFile == null) {
                    ConsoleIO.printInfo("Job description upload cancelled.");
                    ConsoleIO.pause(scanner);
                    return;
                }
                jdText = ResumeParser.extractText(selectedFile.toPath()).trim();
                if (jdText.isBlank()) {
                    ConsoleIO.printError("The selected file was read but no text content was found in it.");
                    ConsoleIO.pause(scanner);
                    return;
                }
                System.out.println("Selected: " + selectedFile.getName());
            } else {
                System.out.println("Paste the job description below. Enter a single blank line when done.");
                StringBuilder sb = new StringBuilder();
                String line;
                while (!(line = scanner.nextLine()).isBlank()) {
                    sb.append(line).append('\n');
                }
                jdText = sb.toString().trim();
                if (jdText.isBlank()) {
                    ConsoleIO.printError("No job description entered.");
                    ConsoleIO.pause(scanner);
                    return;
                }
            }

            CareerIntelligenceService.JobDescriptionOutcome outcome =
                    careerIntelligenceService.analyzeJobDescriptionAndMatch(user.getUserId(), jdText);
            JobDescription jd = outcome.jobDescription();
            ResumeService.JobMatchResult match = outcome.match();

            ConsoleIO.printSuccess("Job description analyzed" + (jd.getTitle() != null ? ": " + jd.getTitle() : "") + ".");
            System.out.println("Required skills: " + (jd.getRequiredSkills().isEmpty() ? "(none detected)" : String.join(", ", jd.getRequiredSkills())));
            System.out.println("Preferred skills: " + (jd.getPreferredSkills().isEmpty() ? "(none detected)" : String.join(", ", jd.getPreferredSkills())));
            System.out.println("Technologies: " + (jd.getTechnologies().isEmpty() ? "(none detected)" : String.join(", ", jd.getTechnologies())));
            System.out.println("Experience required: " + (jd.getExperienceRequired() == null ? "(not specified)" : jd.getExperienceRequired()));
            System.out.println("Soft skills: " + (jd.getSoftSkills().isEmpty() ? "(none detected)" : String.join(", ", jd.getSoftSkills())));
            if (!jd.getResponsibilities().isEmpty()) {
                System.out.println("Responsibilities:");
                jd.getResponsibilities().forEach(r -> System.out.println("  - " + r));
            }

            System.out.println();
            System.out.printf("Job Match Score: %.1f%%\n", match.matchScore());
            if (!match.breakdown().isEmpty()) {
                System.out.println("Score breakdown:");
                for (ReadinessScore.Component c : match.breakdown()) {
                    System.out.printf("  - %s: %.1f%% (weight %d%%) - %s\n",
                            c.getLabel(), c.getValue(), c.getWeight(), c.getNote());
                }
            }
            printEvidence("Strong Match", match.strongMatches());
            printEvidence("Partial Match", match.partialMatches());
            printEvidence("Weak Evidence", match.weakEvidence());
            printEvidence("Missing", match.missing());
            if (!match.highPrioritySkills().isEmpty()) {
                System.out.println("\nHigh-priority skills to work on first: " + String.join(", ", match.highPrioritySkills()));
            }

            System.out.println();
            System.out.println("InterviewMentor update: " + outcome.refresh().explanation());
        } catch (IOException e) {
            ConsoleIO.printError("Could not read the job description file: " + e.getMessage());
        } catch (SQLException e) {
            ConsoleIO.printError("Database error: " + e.getMessage());
        }
        ConsoleIO.pause(scanner);
    }

    private void printEvidence(String label, List<ResumeService.JdSkillEvidence> evidence) {
        if (evidence.isEmpty()) {
            return;
        }
        System.out.println("\n" + label + ":");
        for (ResumeService.JdSkillEvidence e : evidence) {
            System.out.println("  - " + e.skillName() + (e.requiredByJd() ? " (required)" : " (preferred)") + ": " + e.reason());
        }
    }

    private void startResumeAssessment(User user) {
        ConsoleIO.printHeader("RESUME-BASED ASSESSMENT");
        try {
            List<Integer> topicIds = resumeService.getMatchedTopicIds(user.getUserId());
            if (topicIds.isEmpty()) {
                ConsoleIO.printError("None of your extracted resume skills matched a topic in our question bank yet. "
                        + "Upload/re-analyze your resume (option 1), or take a Standard/Adaptive assessment instead.");
                ConsoleIO.pause(scanner);
                return;
            }

            List<String> topicNames = new ArrayList<>();
            int totalAvailable = 0;
            for (Integer id : topicIds) {
                Optional<Topic> topic = topicDAO.findById(id);
                topic.ifPresent(t -> topicNames.add(t.getTopicName()));
                totalAvailable += topicDAO.countQuestionsForTopic(id);
            }
            System.out.println("Topics matched from your resume skills: " + String.join(", ", topicNames));
            if (totalAvailable == 0) {
                ConsoleIO.printError("The matched topics have no active questions yet.");
                ConsoleIO.pause(scanner);
                return;
            }

            int questionCount = Math.min(totalAvailable, 10);
            int durationMinutes = Math.max(1, questionCount);

            String title = "Resume-Based Assessment - " + String.join(", ", topicNames);
            Assessment assessment = assessmentService.createResumeAssessment(
                    user.getUserId(), topicIds, topicNames, questionCount, durationMinutes, title);
            assessmentMenu.runAssessment(assessment);
        } catch (SQLException e) {
            ConsoleIO.printError("Database error: " + e.getMessage());
            ConsoleIO.pause(scanner);
        } catch (IllegalStateException e) {
            ConsoleIO.printError(e.getMessage());
            ConsoleIO.pause(scanner);
        }
    }
}
