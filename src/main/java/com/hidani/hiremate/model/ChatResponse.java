package com.hidani.hiremate.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class ChatResponse {
    private String message;
    private String intent;
    private ResumeProfile resumeProfile;
    private List<JobMatchResult> jobMatches = new ArrayList<>();
    private List<Application> applications = new ArrayList<>();
    private boolean confirmationRequired;
    private SessionConversationContext.PendingConfirmation pendingConfirmation;
    private Instant timestamp = Instant.now();

    public ChatResponse() {
    }

    public ChatResponse(String message) {
        this.message = message;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getIntent() {
        return intent;
    }

    public void setIntent(String intent) {
        this.intent = intent;
    }

    public ResumeProfile getResumeProfile() {
        return resumeProfile;
    }

    public void setResumeProfile(ResumeProfile resumeProfile) {
        this.resumeProfile = resumeProfile;
    }

    public List<JobMatchResult> getJobMatches() {
        return jobMatches;
    }

    public void setJobMatches(List<JobMatchResult> jobMatches) {
        this.jobMatches = jobMatches != null ? jobMatches : new ArrayList<>();
    }

    public List<Application> getApplications() {
        return applications;
    }

    public void setApplications(List<Application> applications) {
        this.applications = applications != null ? applications : new ArrayList<>();
    }

    public boolean isConfirmationRequired() {
        return confirmationRequired;
    }

    public void setConfirmationRequired(boolean confirmationRequired) {
        this.confirmationRequired = confirmationRequired;
    }

    public SessionConversationContext.PendingConfirmation getPendingConfirmation() {
        return pendingConfirmation;
    }

    public void setPendingConfirmation(SessionConversationContext.PendingConfirmation pendingConfirmation) {
        this.pendingConfirmation = pendingConfirmation;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }
}

