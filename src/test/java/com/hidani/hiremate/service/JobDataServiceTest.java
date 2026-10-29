package com.hidani.hiremate.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hidani.hiremate.model.Job;
import com.hidani.hiremate.model.JobPageResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

public class JobDataServiceTest {

    private ObjectMapper objectMapper;
    private JobDataService jobDataService;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        jobDataService = new JobDataService(objectMapper);
    }

    @Test
    void testMissingFile_ReturnsEmptyDatasetSafely() {
        jobDataService.setJobsFilePath(tempDir.resolve("non_existent_jobs.json").toString());
        jobDataService.loadJobsFromFile();

        assertEquals(0, jobDataService.getJobCount());
        assertTrue(jobDataService.getAllJobs().isEmpty());
    }

    @Test
    void testEmptyDatasetFile_HandledGracefully() throws IOException {
        Path emptyFile = tempDir.resolve("empty.json");
        objectMapper.writeValue(emptyFile.toFile(), List.of());

        jobDataService.setJobsFilePath(emptyFile.toString());
        jobDataService.loadJobsFromFile();

        assertEquals(0, jobDataService.getJobCount());
    }

    @Test
    void testDeduplicateAndValidateJobs() throws IOException {
        Path jobsFile = tempDir.resolve("test_jobs.json");

        Job valid1 = new Job("job-1", "Software Engineer", "Google", "Remote", "2 yrs",
                List.of("Java"), "Desc", "https://example.com/job-1", "Full Time", true);

        // Duplicate by ID
        Job dupId = new Job("job-1", "Duplicate SDE", "Other Co", "Remote", "1 yr",
                List.of("Python"), "Desc", "https://example.com/job-other", "Full Time", true);

        // Duplicate by Source URL
        Job dupUrl = new Job("job-2", "Backend Eng", "Amazon", "Seattle", "3 yrs",
                List.of("Go"), "Desc", "https://example.com/job-1", "Full Time", false);

        // Invalid (missing title)
        Job invalidNoTitle = new Job("job-3", "", "Amazon", "Seattle", "3 yrs",
                List.of("Go"), "Desc", "https://example.com/job-3", "Full Time", false);

        // Valid 2
        Job valid2 = new Job("job-4", "Frontend Developer", "Netflix", "Remote", "1 yr",
                List.of("React"), "Desc", "https://example.com/job-4", "Full Time", true);

        objectMapper.writeValue(jobsFile.toFile(), List.of(valid1, dupId, dupUrl, invalidNoTitle, valid2));

        jobDataService.setJobsFilePath(jobsFile.toString());
        jobDataService.loadJobsFromFile();

        assertEquals(2, jobDataService.getJobCount());
        assertTrue(jobDataService.getJobById("job-1").isPresent());
        assertTrue(jobDataService.getJobById("job-4").isPresent());
        assertFalse(jobDataService.getJobById("job-3").isPresent());
    }

    @Test
    void testPagination() throws IOException {
        Path jobsFile = tempDir.resolve("paged_jobs.json");
        List<Job> sampleList = List.of(
                new Job("j1", "Role 1", "Co A", "Loc", "1y", List.of(), "D", "u1", "FT", true),
                new Job("j2", "Role 2", "Co B", "Loc", "1y", List.of(), "D", "u2", "FT", true),
                new Job("j3", "Role 3", "Co C", "Loc", "1y", List.of(), "D", "u3", "FT", true)
        );
        objectMapper.writeValue(jobsFile.toFile(), sampleList);

        jobDataService.setJobsFilePath(jobsFile.toString());
        jobDataService.loadJobsFromFile();

        JobPageResponse page0 = jobDataService.getJobs(0, 2);
        assertEquals(2, page0.getJobs().size());
        assertEquals(3, page0.getTotalElements());
        assertEquals(2, page0.getTotalPages());

        JobPageResponse page1 = jobDataService.getJobs(1, 2);
        assertEquals(1, page1.getJobs().size());
        assertEquals("j3", page1.getJobs().get(0).getJobId());
    }

    @Test
    void testGetJobById_InvalidOrNotFound() {
        jobDataService.setJobsFilePath(tempDir.resolve("none.json").toString());
        jobDataService.loadJobsFromFile();

        assertEquals(Optional.empty(), jobDataService.getJobById(""));
        assertEquals(Optional.empty(), jobDataService.getJobById(null));
        assertEquals(Optional.empty(), jobDataService.getJobById("non-existent"));
    }
}

