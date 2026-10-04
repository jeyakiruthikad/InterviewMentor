package com.careerintelligence.model;

/**
 * Links an Assessment to a specific Question in a fixed presentation order.
 * Typically loaded together with the full Question object for rendering.
 */
public class AssessmentQuestion {

    private Long id;
    private Long assessmentId;
    private Long questionId;
    private int questionOrder;
    private Question question; // populated via join for convenience

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getAssessmentId() {
        return assessmentId;
    }

    public void setAssessmentId(Long assessmentId) {
        this.assessmentId = assessmentId;
    }

    public Long getQuestionId() {
        return questionId;
    }

    public void setQuestionId(Long questionId) {
        this.questionId = questionId;
    }

    public int getQuestionOrder() {
        return questionOrder;
    }

    public void setQuestionOrder(int questionOrder) {
        this.questionOrder = questionOrder;
    }

    public Question getQuestion() {
        return question;
    }

    public void setQuestion(Question question) {
        this.question = question;
    }
}
