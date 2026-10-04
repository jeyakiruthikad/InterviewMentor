package com.careerintelligence.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class Assessment {

    private Long assessmentId;
    private Long userId;
    private String title;
    private int totalQuestions;
    private int durationMinutes;
    private AssessmentStatus status;
    private AssessmentMode mode = AssessmentMode.STANDARD;
    private Long sourceAssessmentId;
    private BigDecimal totalScore = BigDecimal.ZERO;
    private BigDecimal maxScore = BigDecimal.ZERO;
    private int correctCount;
    private int wrongCount;
    private int unansweredCount;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private LocalDateTime createdAt;
    private List<Integer> topicIds = new ArrayList<>();
    private List<String> topicNames = new ArrayList<>();

    public Long getAssessmentId() {
        return assessmentId;
    }

    public void setAssessmentId(Long assessmentId) {
        this.assessmentId = assessmentId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public int getTotalQuestions() {
        return totalQuestions;
    }

    public void setTotalQuestions(int totalQuestions) {
        this.totalQuestions = totalQuestions;
    }

    public int getDurationMinutes() {
        return durationMinutes;
    }

    public void setDurationMinutes(int durationMinutes) {
        this.durationMinutes = durationMinutes;
    }

    public AssessmentStatus getStatus() {
        return status;
    }

    public void setStatus(AssessmentStatus status) {
        this.status = status;
    }

    public BigDecimal getTotalScore() {
        return totalScore;
    }

    public void setTotalScore(BigDecimal totalScore) {
        this.totalScore = totalScore;
    }

    public BigDecimal getMaxScore() {
        return maxScore;
    }

    public void setMaxScore(BigDecimal maxScore) {
        this.maxScore = maxScore;
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

    public int getUnansweredCount() {
        return unansweredCount;
    }

    public void setUnansweredCount(int unansweredCount) {
        this.unansweredCount = unansweredCount;
    }

    public LocalDateTime getStartTime() {
        return startTime;
    }

    public void setStartTime(LocalDateTime startTime) {
        this.startTime = startTime;
    }

    public LocalDateTime getEndTime() {
        return endTime;
    }

    public void setEndTime(LocalDateTime endTime) {
        this.endTime = endTime;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public AssessmentMode getMode() {
        return mode;
    }

    public void setMode(AssessmentMode mode) {
        this.mode = mode;
    }

    public Long getSourceAssessmentId() {
        return sourceAssessmentId;
    }

    public void setSourceAssessmentId(Long sourceAssessmentId) {
        this.sourceAssessmentId = sourceAssessmentId;
    }

    public List<Integer> getTopicIds() {
        return topicIds;
    }

    public void setTopicIds(List<Integer> topicIds) {
        this.topicIds = topicIds;
    }

    public List<String> getTopicNames() {
        return topicNames;
    }

    public void setTopicNames(List<String> topicNames) {
        this.topicNames = topicNames;
    }
}
