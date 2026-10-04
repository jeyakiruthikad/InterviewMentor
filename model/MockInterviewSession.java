package com.careerintelligence.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents a row in `mock_interview_sessions`: one AI mock-interview
 * attempt (role/topic selection through final feedback).
 */
public class MockInterviewSession {

    private Long sessionId;
    private Long userId;
    private String roleName;
    private String companyName;
    private List<Integer> topicIds = new ArrayList<>();
    private MockSessionStatus status = MockSessionStatus.IN_PROGRESS;
    private int totalQuestions;
    private BigDecimal averageScore;
    private BigDecimal readinessScore;
    private LocalDateTime createdAt;
    private LocalDateTime completedAt;

    public Long getSessionId() {
        return sessionId;
    }

    public void setSessionId(Long sessionId) {
        this.sessionId = sessionId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getRoleName() {
        return roleName;
    }

    public void setRoleName(String roleName) {
        this.roleName = roleName;
    }

    public String getCompanyName() {
        return companyName;
    }

    public void setCompanyName(String companyName) {
        this.companyName = companyName;
    }

    public List<Integer> getTopicIds() {
        return topicIds;
    }

    public void setTopicIds(List<Integer> topicIds) {
        this.topicIds = topicIds;
    }

    public MockSessionStatus getStatus() {
        return status;
    }

    public void setStatus(MockSessionStatus status) {
        this.status = status;
    }

    public int getTotalQuestions() {
        return totalQuestions;
    }

    public void setTotalQuestions(int totalQuestions) {
        this.totalQuestions = totalQuestions;
    }

    public BigDecimal getAverageScore() {
        return averageScore;
    }

    public void setAverageScore(BigDecimal averageScore) {
        this.averageScore = averageScore;
    }

    public BigDecimal getReadinessScore() {
        return readinessScore;
    }

    public void setReadinessScore(BigDecimal readinessScore) {
        this.readinessScore = readinessScore;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(LocalDateTime completedAt) {
        this.completedAt = completedAt;
    }
}
