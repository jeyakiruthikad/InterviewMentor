package com.careerintelligence.model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents a row in `learning_roadmaps`: one generated, personalised
 * preparation plan for a user, built from their resume skills, skill
 * gaps, mistakes, assessment performance and readiness score
 * (service.RoadmapService#generate).
 */
public class LearningRoadmap {

    private Long roadmapId;
    private Long userId;
    private Double readinessScore;
    private String readinessBand;
    private String summary;
    private LocalDateTime generatedAt;
    private List<RoadmapItem> items = new ArrayList<>();

    public Long getRoadmapId() {
        return roadmapId;
    }

    public void setRoadmapId(Long roadmapId) {
        this.roadmapId = roadmapId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Double getReadinessScore() {
        return readinessScore;
    }

    public void setReadinessScore(Double readinessScore) {
        this.readinessScore = readinessScore;
    }

    public String getReadinessBand() {
        return readinessBand;
    }

    public void setReadinessBand(String readinessBand) {
        this.readinessBand = readinessBand;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public LocalDateTime getGeneratedAt() {
        return generatedAt;
    }

    public void setGeneratedAt(LocalDateTime generatedAt) {
        this.generatedAt = generatedAt;
    }

    public List<RoadmapItem> getItems() {
        return items;
    }

    public void setItems(List<RoadmapItem> items) {
        this.items = items;
    }
}
