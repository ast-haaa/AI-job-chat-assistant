package com.hidani.hiremate.model;

import java.util.ArrayList;
import java.util.List;

public class JobMatchResult {
    private int displayIndex;
    private Job job;
    private int matchPercentage;
    private List<String> matchingSkills = new ArrayList<>();
    private List<String> missingSkills = new ArrayList<>();
    private String explanation;
    private String eligibilityMismatch;

    public JobMatchResult() {
    }

    public JobMatchResult(int displayIndex, Job job, int matchPercentage, List<String> matchingSkills,
                          List<String> missingSkills, String explanation, String eligibilityMismatch) {
        this.displayIndex = displayIndex;
        this.job = job;
        this.matchPercentage = matchPercentage;
        this.matchingSkills = matchingSkills != null ? matchingSkills : new ArrayList<>();
        this.missingSkills = missingSkills != null ? missingSkills : new ArrayList<>();
        this.explanation = explanation;
        this.eligibilityMismatch = eligibilityMismatch;
    }

    public int getDisplayIndex() {
        return displayIndex;
    }

    public void setDisplayIndex(int displayIndex) {
        this.displayIndex = displayIndex;
    }

    public Job getJob() {
        return job;
    }

    public void setJob(Job job) {
        this.job = job;
    }

    public int getMatchPercentage() {
        return matchPercentage;
    }

    public void setMatchPercentage(int matchPercentage) {
        this.matchPercentage = matchPercentage;
    }

    public List<String> getMatchingSkills() {
        return matchingSkills;
    }

    public void setMatchingSkills(List<String> matchingSkills) {
        this.matchingSkills = matchingSkills != null ? matchingSkills : new ArrayList<>();
    }

    public List<String> getMissingSkills() {
        return missingSkills;
    }

    public void setMissingSkills(List<String> missingSkills) {
        this.missingSkills = missingSkills != null ? missingSkills : new ArrayList<>();
    }

    public String getExplanation() {
        return explanation;
    }

    public void setExplanation(String explanation) {
        this.explanation = explanation;
    }

    public String getEligibilityMismatch() {
        return eligibilityMismatch;
    }

    public void setEligibilityMismatch(String eligibilityMismatch) {
        this.eligibilityMismatch = eligibilityMismatch;
    }
}

