package com.careerintelligence.model;

import java.time.LocalDateTime;

/**
 * Represents a row in `mistake_log`, joined with the question/topic text
 * needed to display it in the Mistake Analyzer screen and to build a
 * RETRY-mode assessment.
 */
public class MistakeEntry {

    private Long id;
    private Long userId;
    private Long questionId;
    private String questionText;
    private QuestionType questionType;
    private Integer topicId;
    private String topicName;
    private int timesWrong;
    private boolean resolved;
    private LocalDateTime lastWrongAt;
    private LocalDateTime resolvedAt;

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

    public Long getQuestionId() {
        return questionId;
    }

    public void setQuestionId(Long questionId) {
        this.questionId = questionId;
    }

    public String getQuestionText() {
        return questionText;
    }

    public void setQuestionText(String questionText) {
        this.questionText = questionText;
    }

    public QuestionType getQuestionType() {
        return questionType;
    }

    public void setQuestionType(QuestionType questionType) {
        this.questionType = questionType;
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

    public int getTimesWrong() {
        return timesWrong;
    }

    public void setTimesWrong(int timesWrong) {
        this.timesWrong = timesWrong;
    }

    public boolean isResolved() {
        return resolved;
    }

    public void setResolved(boolean resolved) {
        this.resolved = resolved;
    }

    public LocalDateTime getLastWrongAt() {
        return lastWrongAt;
    }

    public void setLastWrongAt(LocalDateTime lastWrongAt) {
        this.lastWrongAt = lastWrongAt;
    }

    public LocalDateTime getResolvedAt() {
        return resolvedAt;
    }

    public void setResolvedAt(LocalDateTime resolvedAt) {
        this.resolvedAt = resolvedAt;
    }
}
