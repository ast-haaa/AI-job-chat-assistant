package com.hidani.hiremate.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class ResumeProfile {
    private String fileName;
    private String name;
    private String email;
    private String phone;
    private String targetDesignation;
    private List<String> skills = new ArrayList<>();
    private List<String> experience = new ArrayList<>();
    private List<String> education = new ArrayList<>();
    private List<String> projects = new ArrayList<>();
    private Instant parsedAt = Instant.now();

    public ResumeProfile() {
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getTargetDesignation() {
        return targetDesignation;
    }

    public void setTargetDesignation(String targetDesignation) {
        this.targetDesignation = targetDesignation;
    }

    public List<String> getSkills() {
        return skills;
    }

    public void setSkills(List<String> skills) {
        this.skills = skills != null ? skills : new ArrayList<>();
    }

    public List<String> getExperience() {
        return experience;
    }

    public void setExperience(List<String> experience) {
        this.experience = experience != null ? experience : new ArrayList<>();
    }

    public List<String> getEducation() {
        return education;
    }

    public void setEducation(List<String> education) {
        this.education = education != null ? education : new ArrayList<>();
    }

    public List<String> getProjects() {
        return projects;
    }

    public void setProjects(List<String> projects) {
        this.projects = projects != null ? projects : new ArrayList<>();
    }

    public Instant getParsedAt() {
        return parsedAt;
    }

    public void setParsedAt(Instant parsedAt) {
        this.parsedAt = parsedAt;
    }
}

