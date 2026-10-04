package com.careerintelligence.model;

import java.time.LocalDateTime;

/**
 * Represents a row in `resume_skills`: one AI-extracted, categorised skill
 * from a user's uploaded resume, optionally matched to a `topics` row so
 * it can drive resume-based question generation and weak-skill detection
 * against the existing topic_performance data.
 */
public class ResumeSkill {

    private Long id;
    private Long userId;
    private String skillName;
    private String category;
    private Integer matchedTopicId;
    private String matchedTopicName; // convenience field populated by joined queries
    private LocalDateTime extractedAt;

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

    public String getSkillName() {
        return skillName;
    }

    public void setSkillName(String skillName) {
        this.skillName = skillName;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public Integer getMatchedTopicId() {
        return matchedTopicId;
    }

    public void setMatchedTopicId(Integer matchedTopicId) {
        this.matchedTopicId = matchedTopicId;
    }

    public String getMatchedTopicName() {
        return matchedTopicName;
    }

    public void setMatchedTopicName(String matchedTopicName) {
        this.matchedTopicName = matchedTopicName;
    }

    public LocalDateTime getExtractedAt() {
        return extractedAt;
    }

    public void setExtractedAt(LocalDateTime extractedAt) {
        this.extractedAt = extractedAt;
    }
}
