package com.hidani.hiremate.model;

import java.util.List;

public class JobPageResponse {
    private List<Job> jobs;
    private int page;
    private int size;
    private long totalElements;
    private int totalPages;

    public JobPageResponse() {
    }

    public JobPageResponse(List<Job> jobs, int page, int size, long totalElements, int totalPages) {
        this.jobs = jobs;
        this.page = page;
        this.size = size;
        this.totalElements = totalElements;
        this.totalPages = totalPages;
    }

    public List<Job> getJobs() {
        return jobs;
    }

    public void setJobs(List<Job> jobs) {
        this.jobs = jobs;
    }

    public int getPage() {
        return page;
    }

    public void setPage(int page) {
        this.page = page;
    }

    public int getSize() {
        return size;
    }

    public void setSize(int size) {
        this.size = size;
    }

    public long getTotalElements() {
        return totalElements;
    }

    public void setTotalElements(long totalElements) {
        this.totalElements = totalElements;
    }

    public int getTotalPages() {
        return totalPages;
    }

    public void setTotalPages(int totalPages) {
        this.totalPages = totalPages;
    }
}

