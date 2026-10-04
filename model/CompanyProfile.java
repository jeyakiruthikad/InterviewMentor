package com.careerintelligence.model;

import java.time.LocalDateTime;

/** Represents a row in `company_profiles`: admin-managed reference info about a target company. */
public class CompanyProfile {

    private Integer companyId;
    private String companyName;
    private String industry;
    private String interviewProcess;
    private String notes;
    private boolean active;
    private LocalDateTime createdAt;

    public Integer getCompanyId() {
        return companyId;
    }

    public void setCompanyId(Integer companyId) {
        this.companyId = companyId;
    }

    public String getCompanyName() {
        return companyName;
    }

    public void setCompanyName(String companyName) {
        this.companyName = companyName;
    }

    public String getIndustry() {
        return industry;
    }

    public void setIndustry(String industry) {
        this.industry = industry;
    }

    public String getInterviewProcess() {
        return interviewProcess;
    }

    public void setInterviewProcess(String interviewProcess) {
        this.interviewProcess = interviewProcess;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
