package com.hidani.hiremate.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hidani.hiremate.model.Application;
import com.hidani.hiremate.model.Job;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

public class ApplicationServiceTest {

    private ApplicationService applicationService;
    private JobDataService jobDataService;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() throws IOException {
        ObjectMapper mapper = new ObjectMapper();

        Path jobsFile = tempDir.resolve("test_jobs.json");
        List<Job> sampleJobs = List.of(
                new Job("job-abc", "Junior Backend Dev", "Google", "Remote", "1 yr", List.of("Java"), "Desc", "url1", "FT", true),
                new Job("job-xyz", "Frontend Intern", "Meta", "London", "0 yrs", List.of("React"), "Desc", "url2", "Internship", false)
        );
        mapper.writeValue(jobsFile.toFile(), sampleJobs);

        jobDataService = new JobDataService(mapper);
        jobDataService.setJobsFilePath(jobsFile.toString());
        jobDataService.loadJobsFromFile();

        applicationService = new ApplicationService(mapper, jobDataService);
        applicationService.setApplicationsFilePath(tempDir.resolve("applications.json").toString());
        applicationService.init();
    }

    @Test
    void testSuccessfulApplicationRecording() {
        Application app = applicationService.applyToJob("session-1", "job-abc", "Excited for this role");

        assertNotNull(app);
        assertNotNull(app.getApplicationId());
        assertEquals("session-1", app.getSessionId());
        assertEquals("job-abc", app.getJobId());
        assertEquals("Junior Backend Dev", app.getJobTitle());
        assertEquals("Google", app.getCompany());
        assertEquals("Applied", app.getStatus());

        List<Application> history = applicationService.getApplicationsForSession("session-1");
        assertEquals(1, history.size());
    }

    @Test
    void testDuplicateApplicationPrevention() {
        applicationService.applyToJob("session-dup", "job-abc", "First attempt");

        // Attempting to apply to the same job in the same session must throw IllegalStateException
        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                applicationService.applyToJob("session-dup", "job-abc", "Second attempt")
        );
        assertTrue(ex.getMessage().contains("already recorded an application"));
    }

    @Test
    void testInvalidJobReference() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                applicationService.applyToJob("session-invalid", "non-existent-id", "Notes")
        );
        assertTrue(ex.getMessage().contains("Job not found"));
    }

    @Test
    void testSessionIsolation() {
        // User A applies to job-abc
        applicationService.applyToJob("session-A", "job-abc", "Notes A");

        // User B applies to job-xyz
        applicationService.applyToJob("session-B", "job-xyz", "Notes B");

        // Session A can only view Session A's applications
        List<Application> appsA = applicationService.getApplicationsForSession("session-A");
        assertEquals(1, appsA.size());
        assertEquals("job-abc", appsA.get(0).getJobId());

        // Session B can only view Session B's applications
        List<Application> appsB = applicationService.getApplicationsForSession("session-B");
        assertEquals(1, appsB.size());
        assertEquals("job-xyz", appsB.get(0).getJobId());

        // Session A cannot look up Session B's application by ID
        Optional<Application> lookup = applicationService.getApplicationById("session-A", appsB.get(0).getApplicationId());
        assertTrue(lookup.isEmpty());
    }

    @Test
    void testConcurrentApplications() throws InterruptedException, ExecutionException {
        int threads = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        List<Callable<Application>> tasks = new ArrayList<>();

        // Different sessions concurrently applying
        for (int i = 0; i < threads; i++) {
            final String sess = "session-thread-" + i;
            tasks.add(() -> applicationService.applyToJob(sess, "job-abc", "Concurrent apply"));
        }

        List<Future<Application>> futures = executor.invokeAll(tasks);
        executor.shutdown();

        for (Future<Application> f : futures) {
            Application a = f.get();
            assertNotNull(a);
            assertEquals("job-abc", a.getJobId());
        }
    }
}

