package com.hidani.hiremate.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class SessionConversationContext implements Serializable {
    private static final long serialVersionUID = 1L;

    private ResumeProfile resumeProfile;
    private List<JobMatchResult> lastRecommendations = new ArrayList<>();
    private JobMatchFilter activeFilters = new JobMatchFilter();
    private PendingConfirmation pendingConfirmation;

    public static class PendingConfirmation implements Serializable {
        private static final long serialVersionUID = 1L;
        private String actionType; // e.g. "APPLY_JOB"
        private String jobId;
        private String jobTitle;
        private String company;
        private int displayIndex;

        public PendingConfirmation() {
        }

        public PendingConfirmation(String actionType, String jobId, String jobTitle, String company, int displayIndex) {
            this.actionType = actionType;
            this.jobId = jobId;
            this.jobTitle = jobTitle;
            this.company = company;
            this.displayIndex = displayIndex;
        }

        public String getActionType() {
            return actionType;
        }

        public void setActionType(String actionType) {
            this.actionType = actionType;
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

        public int getDisplayIndex() {
            return displayIndex;
        }

        public void setDisplayIndex(int displayIndex) {
            this.displayIndex = displayIndex;
        }
    }

    public SessionConversationContext() {
    }

    public ResumeProfile getResumeProfile() {
        return resumeProfile;
    }

    public void setResumeProfile(ResumeProfile resumeProfile) {
        this.resumeProfile = resumeProfile;
    }

    public List<JobMatchResult> getLastRecommendations() {
        return lastRecommendations;
    }

    public void setLastRecommendations(List<JobMatchResult> lastRecommendations) {
        this.lastRecommendations = lastRecommendations != null ? lastRecommendations : new ArrayList<>();
    }

    public JobMatchFilter getActiveFilters() {
        return activeFilters;
    }

    public void setActiveFilters(JobMatchFilter activeFilters) {
        this.activeFilters = activeFilters != null ? activeFilters : new JobMatchFilter();
    }

    public PendingConfirmation getPendingConfirmation() {
        return pendingConfirmation;
    }

    public void setPendingConfirmation(PendingConfirmation pendingConfirmation) {
        this.pendingConfirmation = pendingConfirmation;
    }

    public void clearPendingConfirmation() {
        this.pendingConfirmation = null;
    }

    public JobMatchResult findRecommendationByDisplayIndex(int index) {
        if (lastRecommendations == null) {
            return null;
        }
        for (JobMatchResult res : lastRecommendations) {
            if (res.getDisplayIndex() == index) {
                return res;
            }
        }
        return null;
    }
}

