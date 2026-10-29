package com.hidani.hiremate.model;

import java.util.ArrayList;
import java.util.List;

public class JobMatchFilter {
    private Boolean remoteOnly;
    private List<String> requiredSkills = new ArrayList<>();
    private String titleKeyword;
    private String location;
    private String employmentType; // "internship", "full-time", etc.
    private Integer minExperienceYears;
    private Integer maxExperienceYears;
    private Integer minMatchPercentage;
    private int limit = 5;

    public JobMatchFilter() {
    }

    public Boolean getRemoteOnly() {
        return remoteOnly;
    }

    public void setRemoteOnly(Boolean remoteOnly) {
        this.remoteOnly = remoteOnly;
    }

    public List<String> getRequiredSkills() {
        return requiredSkills;
    }

    public void setRequiredSkills(List<String> requiredSkills) {
        this.requiredSkills = requiredSkills != null ? requiredSkills : new ArrayList<>();
    }

    public String getTitleKeyword() {
        return titleKeyword;
    }

    public void setTitleKeyword(String titleKeyword) {
        this.titleKeyword = titleKeyword;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public String getEmploymentType() {
        return employmentType;
    }

    public void setEmploymentType(String employmentType) {
        this.employmentType = employmentType;
    }

    public Integer getMinExperienceYears() {
        return minExperienceYears;
    }

    public void setMinExperienceYears(Integer minExperienceYears) {
        this.minExperienceYears = minExperienceYears;
    }

    public Integer getMaxExperienceYears() {
        return maxExperienceYears;
    }

    public void setMaxExperienceYears(Integer maxExperienceYears) {
        this.maxExperienceYears = maxExperienceYears;
    }

    public Integer getMinMatchPercentage() {
        return minMatchPercentage;
    }

    public void setMinMatchPercentage(Integer minMatchPercentage) {
        this.minMatchPercentage = minMatchPercentage;
    }

    public int getLimit() {
        return limit;
    }

    public void setLimit(int limit) {
        this.limit = limit;
    }

    public boolean hasAnyFilter() {
        return Boolean.TRUE.equals(remoteOnly)
                || (requiredSkills != null && !requiredSkills.isEmpty())
                || (titleKeyword != null && !titleKeyword.isBlank())
                || (location != null && !location.isBlank())
                || (employmentType != null && !employmentType.isBlank())
                || minExperienceYears != null
                || minMatchPercentage != null;
    }
}

