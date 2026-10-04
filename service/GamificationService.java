package com.careerintelligence.service;

import com.careerintelligence.dao.GamificationDAO;
import com.careerintelligence.model.Badge;
import com.careerintelligence.model.PointsLogEntry;
import com.careerintelligence.model.UserBadge;
import com.careerintelligence.model.UserPoints;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Business logic for Gamification (final implementation): points, daily-activity
 * streaks, badges/achievements. Designed to be called additively from the
 * UI layer right after an existing flow completes (assessment finished,
 * mistake resolved, mock interview completed, resume analysed, roadmap
 * generated, successful login) so none of the current implementation service code
 * needs to change - this only ever adds rows, never blocks a feature if
 * it fails (every public method swallows SQLException into a no-op so a
 * gamification hiccup can never break the underlying feature).
 */
public class GamificationService {

    // Point values for each rewarded action - centralised here so the whole
    // reward schedule is visible and easy to tune in one place.
    public static final int POINTS_ASSESSMENT_COMPLETED = 10;
    public static final int POINTS_PERFECT_SCORE_BONUS = 15;
    public static final int POINTS_HIGH_SCORE_BONUS = 5;
    public static final int POINTS_MISTAKE_RESOLVED = 3;
    public static final int POINTS_RESUME_ANALYZED = 10;
    public static final int POINTS_MOCK_INTERVIEW_COMPLETED = 15;
    public static final int POINTS_ROADMAP_GENERATED = 5;
    public static final int POINTS_ROLE_PREP_VIEWED = 2;
    public static final int POINTS_DAILY_LOGIN = 2;

    private final GamificationDAO gamificationDAO = new GamificationDAO();

    /** Everything the UI needs after an award: the resulting balance + any badges newly unlocked. */
    public record AwardResult(UserPoints points, List<Badge> newlyEarnedBadges) {
    }

    public UserPoints getPoints(long userId) {
        try {
            return gamificationDAO.findOrCreate(userId);
        } catch (SQLException e) {
            System.err.println("[Gamification] Could not load points: " + e.getMessage());
            UserPoints empty = new UserPoints();
            empty.setUserId(userId);
            return empty;
        }
    }

    public List<UserBadge> getBadges(long userId) {
        try {
            return gamificationDAO.findBadgesByUser(userId);
        } catch (SQLException e) {
            System.err.println("[Gamification] Could not load badges: " + e.getMessage());
            return List.of();
        }
    }

    public List<PointsLogEntry> getRecentActivity(long userId, int limit) {
        try {
            return gamificationDAO.findRecentPointsLog(userId, limit);
        } catch (SQLException e) {
            System.err.println("[Gamification] Could not load activity log: " + e.getMessage());
            return List.of();
        }
    }

    public List<Object[]> getLeaderboard(int limit) {
        try {
            return gamificationDAO.findTopUsersByPoints(limit);
        } catch (SQLException e) {
            System.err.println("[Gamification] Could not load leaderboard: " + e.getMessage());
            return List.of();
        }
    }

    /** Call once per successful login: updates the daily streak and awards a small login bonus. */
    public AwardResult recordDailyLogin(long userId) {
        List<Badge> earned = new ArrayList<>();
        try {
            int streak = gamificationDAO.touchStreak(userId);
            gamificationDAO.addPoints(userId, POINTS_DAILY_LOGIN, "Daily login");
            earned.addAll(awardIfEligible(userId, "STREAK_3", streak >= 3));
            earned.addAll(awardIfEligible(userId, "STREAK_7", streak >= 7));
            earned.addAll(awardIfEligible(userId, "STREAK_30", streak >= 30));
        } catch (SQLException e) {
            System.err.println("[Gamification] Could not record login streak: " + e.getMessage());
        }
        return new AwardResult(getPoints(userId), earned);
    }

    /** Call after an assessment is finalized (any mode: STANDARD/ADAPTIVE/RETRY/RESUME). */
    public AwardResult awardAssessmentCompleted(long userId, double scorePercent, int totalCompletedAssessments) {
        List<Badge> earned = new ArrayList<>();
        try {
            int points = POINTS_ASSESSMENT_COMPLETED;
            String reason = "Assessment completed";
            if (scorePercent >= 100.0) {
                points += POINTS_PERFECT_SCORE_BONUS;
                reason += " (perfect score bonus)";
            } else if (scorePercent >= 90.0) {
                points += POINTS_HIGH_SCORE_BONUS;
                reason += " (high score bonus)";
            }
            gamificationDAO.addPoints(userId, points, reason);

            earned.addAll(awardIfEligible(userId, "FIRST_STEPS", totalCompletedAssessments >= 1));
            earned.addAll(awardIfEligible(userId, "ASSESSMENT_5", totalCompletedAssessments >= 5));
            earned.addAll(awardIfEligible(userId, "ASSESSMENT_25", totalCompletedAssessments >= 25));
            earned.addAll(awardIfEligible(userId, "PERFECT_SCORE", scorePercent >= 100.0));
            earned.addAll(awardIfEligible(userId, "HIGH_ACHIEVER", scorePercent >= 90.0));
        } catch (SQLException e) {
            System.err.println("[Gamification] Could not award assessment points: " + e.getMessage());
        }
        return new AwardResult(getPoints(userId), earned);
    }

    /** Call after a batch of mistakes is resolved (a correct answer during a RETRY assessment). */
    public AwardResult awardMistakesResolved(long userId, int countResolvedThisSession, int totalResolvedAllTime) {
        List<Badge> earned = new ArrayList<>();
        if (countResolvedThisSession <= 0) {
            return new AwardResult(getPoints(userId), earned);
        }
        try {
            gamificationDAO.addPoints(userId, POINTS_MISTAKE_RESOLVED * countResolvedThisSession,
                    countResolvedThisSession + " mistake(s) resolved");
            earned.addAll(awardIfEligible(userId, "MISTAKE_SLAYER", totalResolvedAllTime >= 10));
        } catch (SQLException e) {
            System.err.println("[Gamification] Could not award mistake-resolution points: " + e.getMessage());
        }
        return new AwardResult(getPoints(userId), earned);
    }

    /** Call after a resume upload + AI analysis completes. */
    public AwardResult awardResumeAnalyzed(long userId) {
        List<Badge> earned = new ArrayList<>();
        try {
            gamificationDAO.addPoints(userId, POINTS_RESUME_ANALYZED, "Resume analyzed");
            earned.addAll(awardIfEligible(userId, "RESUME_UPLOADED", true));
        } catch (SQLException e) {
            System.err.println("[Gamification] Could not award resume points: " + e.getMessage());
        }
        return new AwardResult(getPoints(userId), earned);
    }

    /** Call after a mock interview session is finalized. */
    public AwardResult awardMockInterviewCompleted(long userId, int totalCompletedMocks) {
        List<Badge> earned = new ArrayList<>();
        try {
            gamificationDAO.addPoints(userId, POINTS_MOCK_INTERVIEW_COMPLETED, "Mock interview completed");
            earned.addAll(awardIfEligible(userId, "MOCK_INTERVIEWER", totalCompletedMocks >= 1));
            earned.addAll(awardIfEligible(userId, "MOCK_VETERAN", totalCompletedMocks >= 10));
        } catch (SQLException e) {
            System.err.println("[Gamification] Could not award mock interview points: " + e.getMessage());
        }
        return new AwardResult(getPoints(userId), earned);
    }

    /** Call whenever a fresh Interview Readiness Score is computed. */
    public AwardResult checkReadinessBadge(long userId, double readinessScore) {
        List<Badge> earned = new ArrayList<>();
        try {
            earned.addAll(awardIfEligible(userId, "READY_FOR_HIRE", readinessScore >= 80.0));
        } catch (SQLException e) {
            System.err.println("[Gamification] Could not check readiness badge: " + e.getMessage());
        }
        return new AwardResult(getPoints(userId), earned);
    }

    /** Call after a learning roadmap is generated. */
    public AwardResult awardRoadmapGenerated(long userId) {
        List<Badge> earned = new ArrayList<>();
        try {
            gamificationDAO.addPoints(userId, POINTS_ROADMAP_GENERATED, "Learning roadmap generated");
            earned.addAll(awardIfEligible(userId, "ROADMAP_STARTER", true));
        } catch (SQLException e) {
            System.err.println("[Gamification] Could not award roadmap points: " + e.getMessage());
        }
        return new AwardResult(getPoints(userId), earned);
    }

    /** Call after viewing role/company preparation material for a target role. */
    public AwardResult awardRolePrepViewed(long userId) {
        List<Badge> earned = new ArrayList<>();
        try {
            gamificationDAO.addPoints(userId, POINTS_ROLE_PREP_VIEWED, "Role & company prep viewed");
            earned.addAll(awardIfEligible(userId, "ROLE_READY", true));
        } catch (SQLException e) {
            System.err.println("[Gamification] Could not award role-prep points: " + e.getMessage());
        }
        return new AwardResult(getPoints(userId), earned);
    }

    private List<Badge> awardIfEligible(long userId, String badgeCode, boolean eligible) throws SQLException {
        if (!eligible) {
            return List.of();
        }
        List<Badge> catalog = gamificationDAO.findAllBadgeDefinitions();
        for (Badge badge : catalog) {
            if (badge.getBadgeCode().equals(badgeCode)) {
                boolean newlyAwarded = gamificationDAO.awardBadge(userId, badge);
                return newlyAwarded ? List.of(badge) : List.of();
            }
        }
        return List.of();
    }
}
