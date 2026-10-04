package com.careerintelligence.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Represents a row in `topic_performance`: one user's running accuracy and
 * recommended next difficulty for one topic. Drives both the adaptive quiz
 * engine (service.AdaptiveDifficultyEngine via AssessmentService) and the
 * performance dashboard's topic-wise breakdown.
 */
public class TopicPerformance {

    private Long id;
    private Long userId;
    private Integer topicId;
    private String topicName; // convenience field populated by joined queries
    private int attemptsCount;
    private int correctCount;
    private int wrongCount;
    private BigDecimal accuracyPercent = BigDecimal.ZERO;
    private DifficultyLevel currentDifficulty = DifficultyLevel.EASY;
    /** Last-N outcomes ('1'=correct,'0'=wrong), most recent last - drives adaptive momentum, see util.AdaptiveDifficultyCalculator. */
    private String recentResults = "";
    private LocalDateTime lastAttemptAt;
    private LocalDateTime updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Integer getTopicId() {
        return topicId;
    }

    public void setTopicId(Integer topicId) {
        this.topicId = topicId;
    }

    public String getTopicName() {
        return topicName;
    }

    public void setTopicName(String topicName) {
        this.topicName = topicName;
    }

    public int getAttemptsCount() {
        return attemptsCount;
    }

    public void setAttemptsCount(int attemptsCount) {
        this.attemptsCount = attemptsCount;
    }

    public int getCorrectCount() {
        return correctCount;
    }

    public void setCorrectCount(int correctCount) {
        this.correctCount = correctCount;
    }

    public int getWrongCount() {
        return wrongCount;
    }

    public void setWrongCount(int wrongCount) {
        this.wrongCount = wrongCount;
    }

    public BigDecimal getAccuracyPercent() {
        return accuracyPercent;
    }

    public void setAccuracyPercent(BigDecimal accuracyPercent) {
        this.accuracyPercent = accuracyPercent;
    }

    public DifficultyLevel getCurrentDifficulty() {
        return currentDifficulty;
    }

    public void setCurrentDifficulty(DifficultyLevel currentDifficulty) {
        this.currentDifficulty = currentDifficulty;
    }

    public String getRecentResults() {
        return recentResults;
    }

    public void setRecentResults(String recentResults) {
        this.recentResults = recentResults == null ? "" : recentResults;
    }

    public LocalDateTime getLastAttemptAt() {
        return lastAttemptAt;
    }

    public void setLastAttemptAt(LocalDateTime lastAttemptAt) {
        this.lastAttemptAt = lastAttemptAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
