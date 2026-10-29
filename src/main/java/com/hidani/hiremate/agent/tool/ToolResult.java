package com.hidani.hiremate.agent.tool;

import com.hidani.hiremate.agent.Intent;
import com.hidani.hiremate.model.Application;
import com.hidani.hiremate.model.JobMatchResult;
import com.hidani.hiremate.model.ResumeProfile;
import com.hidani.hiremate.model.SessionConversationContext;

import java.util.ArrayList;
import java.util.List;

public class ToolResult {
    private final boolean success;
    private final String responseText;
    private final Intent intent;
    private ResumeProfile resumeProfile;
    private List<JobMatchResult> jobMatches = new ArrayList<>();
    private List<Application> applications = new ArrayList<>();
    private boolean confirmationRequired;
    private SessionConversationContext.PendingConfirmation pendingConfirmation;

    public ToolResult(boolean success, String responseText, Intent intent) {
        this.success = success;
        this.responseText = responseText;
        this.intent = intent;
    }

    public static ToolResult ok(String responseText, Intent intent) {
        return new ToolResult(true, responseText, intent);
    }

    public static ToolResult error(String errorMessage, Intent intent) {
        return new ToolResult(false, errorMessage, intent);
    }

    public boolean isSuccess() {
        return success;
    }

    public String getResponseText() {
        return responseText;
    }

    public Intent getIntent() {
        return intent;
    }

    public ResumeProfile getResumeProfile() {
        return resumeProfile;
    }

    public ToolResult setResumeProfile(ResumeProfile resumeProfile) {
        this.resumeProfile = resumeProfile;
        return this;
    }

    public List<JobMatchResult> getJobMatches() {
        return jobMatches;
    }

    public ToolResult setJobMatches(List<JobMatchResult> jobMatches) {
        this.jobMatches = jobMatches != null ? jobMatches : new ArrayList<>();
        return this;
    }

    public List<Application> getApplications() {
        return applications;
    }

    public ToolResult setApplications(List<Application> applications) {
        this.applications = applications != null ? applications : new ArrayList<>();
        return this;
    }

    public boolean isConfirmationRequired() {
        return confirmationRequired;
    }

    public ToolResult setConfirmationRequired(boolean confirmationRequired) {
        this.confirmationRequired = confirmationRequired;
        return this;
    }

    public SessionConversationContext.PendingConfirmation getPendingConfirmation() {
        return pendingConfirmation;
    }

    public ToolResult setPendingConfirmation(SessionConversationContext.PendingConfirmation pendingConfirmation) {
        this.pendingConfirmation = pendingConfirmation;
        return this;
    }
}

