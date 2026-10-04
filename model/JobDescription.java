package com.careerintelligence.model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents a row in `job_descriptions`: one job description a user has
 * pasted in for AI analysis (Job Description Analysis feature), plus the
 * structured fields {@code ai.AIService#analyzeJobDescription} extracted
 * from its raw text - required/preferred skills, technologies,
 * responsibilities, experience required, and soft skills. This is the
 * input side of Resume + JD Matching (service.ResumeService#matchResumeToJobDescription).
 */
public class JobDescription {

    private Long id;
    private Long userId;
    private String title;
    private String rawText;
    private List<String> requiredSkills = new ArrayList<>();
    private List<String> preferredSkills = new ArrayList<>();
    private List<String> technologies = new ArrayList<>();
    private List<String> responsibilities = new ArrayList<>();
    private List<String> softSkills = new ArrayList<>();
    private String experienceRequired;
    private LocalDateTime analyzedAt;

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

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getRawText() {
        return rawText;
    }

    public void setRawText(String rawText) {
        this.rawText = rawText;
    }

    public List<String> getRequiredSkills() {
        return requiredSkills;
    }

    public void setRequiredSkills(List<String> requiredSkills) {
        this.requiredSkills = requiredSkills == null ? new ArrayList<>() : requiredSkills;
    }

    public List<String> getPreferredSkills() {
        return preferredSkills;
    }

    public void setPreferredSkills(List<String> preferredSkills) {
        this.preferredSkills = preferredSkills == null ? new ArrayList<>() : preferredSkills;
    }

    public List<String> getTechnologies() {
        return technologies;
    }

    public void setTechnologies(List<String> technologies) {
        this.technologies = technologies == null ? new ArrayList<>() : technologies;
    }

    public List<String> getResponsibilities() {
        return responsibilities;
    }

    public void setResponsibilities(List<String> responsibilities) {
        this.responsibilities = responsibilities == null ? new ArrayList<>() : responsibilities;
    }

    public List<String> getSoftSkills() {
        return softSkills;
    }

    public void setSoftSkills(List<String> softSkills) {
        this.softSkills = softSkills == null ? new ArrayList<>() : softSkills;
    }

    public String getExperienceRequired() {
        return experienceRequired;
    }

    public void setExperienceRequired(String experienceRequired) {
        this.experienceRequired = experienceRequired;
    }

    public LocalDateTime getAnalyzedAt() {
        return analyzedAt;
    }

    public void setAnalyzedAt(LocalDateTime analyzedAt) {
        this.analyzedAt = analyzedAt;
    }
}
