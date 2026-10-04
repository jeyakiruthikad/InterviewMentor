package com.careerintelligence.model;

/** Represents a row in the user_profiles table. */
public class UserProfile {

    private Long profileId;
    private Long userId;
    private String targetRole;
    private String targetCompany;
    private String experienceLevel; // FRESHER, JUNIOR, MID, SENIOR
    private String bio;
    private String resumePath;
    private String resumeExtractedSkills;

    public Long getProfileId() {
        return profileId;
    }

    public void setProfileId(Long profileId) {
        this.profileId = profileId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getTargetRole() {
        return targetRole;
    }

    public void setTargetRole(String targetRole) {
        this.targetRole = targetRole;
    }

    public String getTargetCompany() {
        return targetCompany;
    }

    public void setTargetCompany(String targetCompany) {
        this.targetCompany = targetCompany;
    }

    public String getExperienceLevel() {
        return experienceLevel;
    }

    public void setExperienceLevel(String experienceLevel) {
        this.experienceLevel = experienceLevel;
    }

    public String getBio() {
        return bio;
    }

    public void setBio(String bio) {
        this.bio = bio;
    }

    public String getResumePath() {
        return resumePath;
    }

    public void setResumePath(String resumePath) {
        this.resumePath = resumePath;
    }

    public String getResumeExtractedSkills() {
        return resumeExtractedSkills;
    }

    public void setResumeExtractedSkills(String resumeExtractedSkills) {
        this.resumeExtractedSkills = resumeExtractedSkills;
    }
}
