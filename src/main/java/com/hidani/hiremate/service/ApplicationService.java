package com.hidani.hiremate.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.hidani.hiremate.model.Application;
import com.hidani.hiremate.model.Job;
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
import java.time.Instant;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

@Service
public class ApplicationService {

    private static final Logger log = LoggerFactory.getLogger(ApplicationService.class);

    @Value("${hiremate.data.applications-path:data/applications.json}")
    private String applicationsFilePath;

    private final ObjectMapper objectMapper;
    private final JobDataService jobDataService;
    private final ReentrantLock fileLock = new ReentrantLock();

    public ApplicationService(ObjectMapper objectMapper, JobDataService jobDataService) {
        this.objectMapper = objectMapper.copy().registerModule(new JavaTimeModule());
        this.jobDataService = jobDataService;
    }

    @PostConstruct
    public void init() {
        initFile();
    }

    private void initFile() {
        fileLock.lock();
        try {
            Path path = Paths.get(applicationsFilePath);
            if (!Files.exists(path)) {
                if (path.getParent() != null && !Files.exists(path.getParent())) {
                    Files.createDirectories(path.getParent());
                }
                Files.write(path, "[]".getBytes());
                log.info("Initialized applications file at {}", path.toAbsolutePath());
            }
        } catch (IOException e) {
            log.error("Failed to initialize applications file: {}", e.getMessage(), e);
        } finally {
            fileLock.unlock();
        }
    }

    /**
     * Records a new application for the given session and job.
     */
    public Application applyToJob(String sessionId, String jobId, String notes) {
        if (sessionId == null || sessionId.isBlank()) {
            throw new IllegalArgumentException("Session ID cannot be empty.");
        }
        if (jobId == null || jobId.isBlank()) {
            throw new IllegalArgumentException("Job ID cannot be empty.");
        }

        Job job = jobDataService.getJobById(jobId.trim())
                .orElseThrow(() -> new IllegalArgumentException("Job not found with ID: " + jobId));

        fileLock.lock();
        try {
            List<Application> allApps = readAllApplicationsUnsafe();

            // Check for duplicate application in the same session
            boolean alreadyApplied = allApps.stream()
                    .anyMatch(a -> sessionId.equals(a.getSessionId()) && jobId.equalsIgnoreCase(a.getJobId()));

            if (alreadyApplied) {
                throw new IllegalStateException("You have already recorded an application for '" + job.getTitle() + "' at " + job.getCompany() + ".");
            }

            Application newApp = new Application(
                    UUID.randomUUID().toString(),
                    sessionId,
                    job.getJobId(),
                    job.getTitle(),
                    job.getCompany(),
                    Instant.now(),
                    "Applied",
                    notes != null ? notes.trim() : "Applied via HireMate AI Assistant"
            );

            allApps.add(newApp);
            saveAllApplicationsUnsafe(allApps);

            log.info("Recorded application {} for job '{}' (session: {})", newApp.getApplicationId(), job.getTitle(), sessionId);
            return newApp;

        } finally {
            fileLock.unlock();
        }
    }

    /**
     * Retrieves all applications submitted by the given session.
     */
    public List<Application> getApplicationsForSession(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return Collections.emptyList();
        }

        fileLock.lock();
        try {
            List<Application> allApps = readAllApplicationsUnsafe();
            return allApps.stream()
                    .filter(a -> sessionId.equals(a.getSessionId()))
                    .sorted(Comparator.comparing(Application::getAppliedDate).reversed())
                    .collect(Collectors.toList());
        } finally {
            fileLock.unlock();
        }
    }

    /**
     * Retrieves a specific application by ID, scoped to the user session.
     */
    public Optional<Application> getApplicationById(String sessionId, String applicationId) {
        if (sessionId == null || applicationId == null) {
            return Optional.empty();
        }

        fileLock.lock();
        try {
            List<Application> allApps = readAllApplicationsUnsafe();
            return allApps.stream()
                    .filter(a -> sessionId.equals(a.getSessionId()) && applicationId.equals(a.getApplicationId()))
                    .findFirst();
        } finally {
            fileLock.unlock();
        }
    }

    /**
     * Checks if user has already applied to a company or job title in this session.
     */
    public List<Application> findApplicationsByCompany(String sessionId, String companyName) {
        if (sessionId == null || companyName == null) return Collections.emptyList();
        String query = companyName.toLowerCase().trim();

        fileLock.lock();
        try {
            List<Application> allApps = readAllApplicationsUnsafe();
            return allApps.stream()
                    .filter(a -> sessionId.equals(a.getSessionId()) && a.getCompany() != null && a.getCompany().toLowerCase().contains(query))
                    .collect(Collectors.toList());
        } finally {
            fileLock.unlock();
        }
    }

    private List<Application> readAllApplicationsUnsafe() {
        Path path = Paths.get(applicationsFilePath);
        if (!Files.exists(path)) {
            return new ArrayList<>();
        }
        try {
            byte[] bytes = Files.readAllBytes(path);
            if (bytes.length == 0) {
                return new ArrayList<>();
            }
            List<Application> list = objectMapper.readValue(bytes, new TypeReference<List<Application>>() {});
            return list != null ? list : new ArrayList<>();
        } catch (IOException e) {
            log.error("Error reading applications from {}: {}", applicationsFilePath, e.getMessage());
            return new ArrayList<>();
        }
    }

    private void saveAllApplicationsUnsafe(List<Application> applications) {
        Path path = Paths.get(applicationsFilePath);
        try {
            if (path.getParent() != null && !Files.exists(path.getParent())) {
                Files.createDirectories(path.getParent());
            }
            objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
            objectMapper.writeValue(path.toFile(), applications);
        } catch (IOException e) {
            log.error("Error writing applications to {}: {}", applicationsFilePath, e.getMessage(), e);
            throw new RuntimeException("Failed to persist application record to file storage.", e);
        }
    }

    public void setApplicationsFilePath(String path) {
        this.applicationsFilePath = path;
    }
}

