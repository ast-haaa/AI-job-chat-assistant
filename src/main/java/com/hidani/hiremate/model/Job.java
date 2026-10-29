package com.hidani.hiremate.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class Job {
    private String jobId;
    private String title;
    private String company;
    private String location;
    private String experienceRequired;
    private List<String> skills = new ArrayList<>();
    private String description;
    private String sourceLink;
    private String employmentType;
    private boolean remote;

    public Job() {
    }

    public Job(String jobId, String title, String company, String location, String experienceRequired,
               List<String> skills, String description, String sourceLink, String employmentType, boolean remote) {
        this.jobId = jobId;
        this.title = title;
        this.company = company;
        this.location = location;
        this.experienceRequired = experienceRequired;
        this.skills = skills != null ? skills : new ArrayList<>();
        this.description = description;
        this.sourceLink = sourceLink;
        this.employmentType = employmentType;
        this.remote = remote;
    }

    public String getJobId() {
        return jobId;
    }

    public void setJobId(String jobId) {
        this.jobId = jobId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getCompany() {
        return company;
    }

    public void setCompany(String company) {
        this.company = company;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public String getExperienceRequired() {
        return experienceRequired;
    }

    public void setExperienceRequired(String experienceRequired) {
        this.experienceRequired = experienceRequired;
    }

    public List<String> getSkills() {
        return skills;
    }

    public void setSkills(List<String> skills) {
        this.skills = skills != null ? skills : new ArrayList<>();
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getSourceLink() {
        return sourceLink;
    }

    public void setSourceLink(String sourceLink) {
        this.sourceLink = sourceLink;
    }

    public String getEmploymentType() {
        return employmentType;
    }

    public void setEmploymentType(String employmentType) {
        this.employmentType = employmentType;
    }

    public boolean isRemote() {
        return remote;
    }

    public void setRemote(boolean remote) {
        this.remote = remote;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Job job = (Job) o;
        return Objects.equals(jobId, job.jobId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(jobId);
    }
}

