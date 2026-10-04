package com.careerintelligence.model;

public class Topic {

    private Integer topicId;
    private String topicName;
    private String category;
    private String description;
    private boolean active;

    public Topic() {
    }

    public Topic(Integer topicId, String topicName, String category, String description) {
        this.topicId = topicId;
        this.topicName = topicName;
        this.category = category;
        this.description = description;
        this.active = true;
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

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    @Override
    public String toString() {
        return topicId + ". " + topicName + " (" + category + ")";
    }
}
