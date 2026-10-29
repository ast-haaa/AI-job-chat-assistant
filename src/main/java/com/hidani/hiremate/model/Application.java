package com.hidani.hiremate.model;

import java.time.Instant;

public class Application {
    private String applicationId;
    private String sessionId;
    private String jobId;
    private String jobTitle;
    private String company;
    private Instant appliedDate;
    private String status; // "Applied", "Recorded"
    private String notes;

    public Application() {
    }

    public Application(String applicationId, String sessionId, String jobId, String jobTitle, String company,
                       Instant appliedDate, String status, String notes) {
        this.applicationId = applicationId;
        this.sessionId = sessionId;
        this.jobId = jobId;
        this.jobTitle = jobTitle;
        this.company = company;
        this.appliedDate = appliedDate;
        this.status = status;
        this.notes = notes;
    }

    public String getApplicationId() {
        return applicationId;
    }

    public void setApplicationId(String applicationId) {
        this.applicationId = applicationId;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getJobId() {
        return jobId;
    }

    public void setJobId(String jobId) {
        this.jobId = jobId;
    }

    public String getJobTitle() {
        return jobTitle;
    }

    public void setJobTitle(String jobTitle) {
        this.jobTitle = jobTitle;
    }

    public String getCompany() {
        return company;
    }

    public void setCompany(String company) {
        this.company = company;
    }

    public Instant getAppliedDate() {
        return appliedDate;
    }

    public void setAppliedDate(Instant appliedDate) {
        this.appliedDate = appliedDate;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }
}

