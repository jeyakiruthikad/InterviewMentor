package com.careerintelligence.model;

import java.time.LocalDateTime;

/**
 * Represents a row in `roadmap_items`: one concrete, actionable
 * recommendation inside a generated {@link LearningRoadmap} (e.g. "Close
 * skill gap: Docker", "Practice weak topic: Operating Systems").
 */
public class RoadmapItem {

    private Long id;
    private Long roadmapId;
    private int itemOrder;
    private RoadmapCategory category;
    private RoadmapPriority priority;
    private String title;
    private String description;
    private Integer relatedTopicId;
    private String relatedTopicName; // convenience field populated by joined queries
    private RoadmapItemStatus status = RoadmapItemStatus.PENDING;
    private LocalDateTime completedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getRoadmapId() {
        return roadmapId;
    }

    public void setRoadmapId(Long roadmapId) {
        this.roadmapId = roadmapId;
    }

    public int getItemOrder() {
        return itemOrder;
    }

    public void setItemOrder(int itemOrder) {
        this.itemOrder = itemOrder;
    }

    public RoadmapCategory getCategory() {
        return category;
    }

    public void setCategory(RoadmapCategory category) {
        this.category = category;
    }

    public RoadmapPriority getPriority() {
        return priority;
    }

    public void setPriority(RoadmapPriority priority) {
        this.priority = priority;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Integer getRelatedTopicId() {
        return relatedTopicId;
    }

    public void setRelatedTopicId(Integer relatedTopicId) {
        this.relatedTopicId = relatedTopicId;
    }

    public String getRelatedTopicName() {
        return relatedTopicName;
    }

    public void setRelatedTopicName(String relatedTopicName) {
        this.relatedTopicName = relatedTopicName;
    }

    public RoadmapItemStatus getStatus() {
        return status;
    }

    public void setStatus(RoadmapItemStatus status) {
        this.status = status;
    }

    public LocalDateTime getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(LocalDateTime completedAt) {
        this.completedAt = completedAt;
    }
}
