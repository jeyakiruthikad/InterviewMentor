package com.careerintelligence.model;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Represents a row in `user_points`: one user's gamification balance -
 * total points earned across the whole app, and their current/longest
 * daily-activity streak. Maintained by service.GamificationService.
 */
public class UserPoints {

    private Long userId;
    private int totalPoints;
    private int currentStreakDays;
    private int longestStreakDays;
    private LocalDate lastActivityDate;
    private LocalDateTime updatedAt;

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public int getTotalPoints() {
        return totalPoints;
    }

    public void setTotalPoints(int totalPoints) {
        this.totalPoints = totalPoints;
    }

    public int getCurrentStreakDays() {
        return currentStreakDays;
    }

    public void setCurrentStreakDays(int currentStreakDays) {
        this.currentStreakDays = currentStreakDays;
    }

    public int getLongestStreakDays() {
        return longestStreakDays;
    }

    public void setLongestStreakDays(int longestStreakDays) {
        this.longestStreakDays = longestStreakDays;
    }

    public LocalDate getLastActivityDate() {
        return lastActivityDate;
    }

    public void setLastActivityDate(LocalDate lastActivityDate) {
        this.lastActivityDate = lastActivityDate;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    /** Simple level derived from total points, purely for a friendly display label (100 points per level). */
    public int getLevel() {
        return 1 + (totalPoints / 100);
    }

    public int getPointsIntoCurrentLevel() {
        return totalPoints % 100;
    }
}
