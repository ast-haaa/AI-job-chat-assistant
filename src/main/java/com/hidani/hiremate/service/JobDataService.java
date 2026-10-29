package com.hidani.hiremate.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.hidani.hiremate.model.Job;
import com.hidani.hiremate.model.JobPageResponse;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
public class JobDataService {

    private static final Logger log = LoggerFactory.getLogger(JobDataService.class);

    @Value("${hiremate.data.jobs-path:data/jobs.json}")
    private String jobsFilePath;

    private final ObjectMapper objectMapper;
    private final List<Job> jobList = new CopyOnWriteArrayList<>();
    private final Map<String, Job> jobMap = new ConcurrentHashMap<>();

    public JobDataService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public synchronized void init() {
        loadJobsFromFile();
    }

    /**
     * Loads jobs from data/jobs.json, deduplicating and validating required fields.
     */
    public synchronized void loadJobsFromFile() {
        jobList.clear();
        jobMap.clear();

        Path path = Paths.get(jobsFilePath);
        if (!Files.exists(path)) {
            log.warn("Jobs data file does not exist at: {}. Initializing with empty dataset.", path.toAbsolutePath());
            return;
        }

        try {
            byte[] bytes = Files.readAllBytes(path);
            if (bytes.length == 0) {
                log.info("Jobs data file is empty.");
                return;
            }

            List<Job> rawJobs = objectMapper.readValue(bytes, new TypeReference<List<Job>>() {});
            if (rawJobs == null) {
                return;
            }

            Set<String> seenIds = new HashSet<>();
            Set<String> seenUrls = new HashSet<>();

            for (Job job : rawJobs) {
                if (!isValidJob(job)) {
                    continue;
                }

                String idKey = job.getJobId() != null ? job.getJobId().trim() : "";
                String urlKey = job.getSourceLink() != null ? job.getSourceLink().trim().toLowerCase() : "";

                if (!idKey.isEmpty() && seenIds.contains(idKey)) {
                    continue; // Skip duplicate ID
                }
                if (!urlKey.isEmpty() && seenUrls.contains(urlKey)) {
                    continue; // Skip duplicate source URL
                }

                if (!idKey.isEmpty()) seenIds.add(idKey);
                if (!urlKey.isEmpty()) seenUrls.add(urlKey);

                jobList.add(job);
                jobMap.put(job.getJobId(), job);
            }

            log.info("Successfully loaded and validated {} unique jobs from {}", jobList.size(), jobsFilePath);

        } catch (IOException e) {
            log.error("Failed or malformed JSON when reading jobs from {}: {}", jobsFilePath, e.getMessage());
        }
    }

    public boolean isValidJob(Job job) {
        if (job == null) return false;
        if (job.getJobId() == null || job.getJobId().isBlank()) return false;
        if (job.getTitle() == null || job.getTitle().isBlank()) return false;
        if (job.getCompany() == null || job.getCompany().isBlank()) return false;
        return true;
    }

    public List<Job> getAllJobs() {
        return Collections.unmodifiableList(jobList);
    }

    public Optional<Job> getJobById(String jobId) {
        if (jobId == null || jobId.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(jobMap.get(jobId.trim()));
    }

    public long getJobCount() {
        return jobList.size();
    }

    public JobPageResponse getJobs(int page, int size) {
        if (page < 0) page = 0;
        if (size <= 0) size = 10;
        if (size > 100) size = 100;

        int totalElements = jobList.size();
        int totalPages = (int) Math.ceil((double) totalElements / size);

        int fromIndex = page * size;
        if (fromIndex >= totalElements) {
            return new JobPageResponse(Collections.emptyList(), page, size, totalElements, totalPages);
        }

        int toIndex = Math.min(fromIndex + size, totalElements);
        List<Job> paged = new ArrayList<>(jobList.subList(fromIndex, toIndex));

        return new JobPageResponse(paged, page, size, totalElements, totalPages);
    }

    /**
     * Persist listings to file (used by ingestion tool).
     */
    public synchronized void saveJobs(List<Job> jobsToSave) throws IOException {
        Path path = Paths.get(jobsFilePath);
        if (path.getParent() != null && !Files.exists(path.getParent())) {
            Files.createDirectories(path.getParent());
        }

        objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
        objectMapper.writeValue(path.toFile(), jobsToSave);
        loadJobsFromFile();
    }

    public void setJobsFilePath(String path) {
        this.jobsFilePath = path;
    }
}

