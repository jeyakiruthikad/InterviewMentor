package com.careerintelligence.model;

public class QuestionOption {

    private Long optionId;
    private Long questionId;
    private String optionLabel; // A, B, C, D
    private String optionText;
    private boolean correct;

    public QuestionOption() {
    }

    public QuestionOption(Long optionId, Long questionId, String optionLabel, String optionText, boolean correct) {
        this.optionId = optionId;
        this.questionId = questionId;
        this.optionLabel = optionLabel;
        this.optionText = optionText;
        this.correct = correct;
    }

    public Long getOptionId() {
        return optionId;
    }

    public void setOptionId(Long optionId) {
        this.optionId = optionId;
    }

    public Long getQuestionId() {
        return questionId;
    }

    public void setQuestionId(Long questionId) {
        this.questionId = questionId;
    }

    public String getOptionLabel() {
        return optionLabel;
    }

    public void setOptionLabel(String optionLabel) {
        this.optionLabel = optionLabel;
    }

    public String getOptionText() {
        return optionText;
    }

    public void setOptionText(String optionText) {
        this.optionText = optionText;
    }

    public boolean isCorrect() {
        return correct;
    }

    public void setCorrect(boolean correct) {
        this.correct = correct;
    }

    @Override
    public String toString() {
        return optionLabel + ") " + optionText;
    }
}
