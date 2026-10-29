package com.hidani.hiremate.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hidani.hiremate.model.Job;
import com.hidani.hiremate.model.JobMatchFilter;
import com.hidani.hiremate.model.JobMatchResult;
import com.hidani.hiremate.model.ResumeProfile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class JobMatchingServiceTest {

    private JobDataService jobDataService;
    private JobMatchingService matchingService;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        jobDataService = new JobDataService(mapper);

        Path jobsFile = tempDir.resolve("test_jobs.json");
        List<Job> dataset = List.of(
                new Job("job-java-remote", "Java Backend Developer", "FinTech Corp", "London, UK", "0-2 years",
                        List.of("Java", "Spring Boot", "SQL"), "Build banking services.", "https://example.com/j1", "Full Time", true),

                new Job("job-intern-java", "Software Engineering Intern", "Startup Inc", "Bengaluru, India", "Internship (0 years)",
                        List.of("Java", "Git"), "Internship program for undergraduates.", "https://example.com/j2", "Internship", true),

                new Job("job-react-onsite", "Senior Frontend Engineer", "Design Studio", "Berlin, Germany", "5+ years",
                        List.of("React", "TypeScript", "CSS"), "Frontend design systems.", "https://example.com/j3", "Full Time", false),

                new Job("job-python-data", "Python Data Scientist", "DataLab", "Remote", "3 years",
                        List.of("Python", "Pandas", "SQL"), "Data science modeling.", "https://example.com/j4", "Full Time", true)
        );

        mapper.writeValue(jobsFile.toFile(), dataset);
        jobDataService.setJobsFilePath(jobsFile.toString());
        jobDataService.loadJobsFromFile();

        matchingService = new JobMatchingService(jobDataService);
    }

    @Test
    void testMissingResumeThrowsException() {
        assertThrows(IllegalArgumentException.class, () ->
                matchingService.matchJobs(null, new JobMatchFilter())
        );
    }

    @Test
    void testSkillMatchingAndRanking() {
        ResumeProfile profile = new ResumeProfile();
        profile.setTargetDesignation("Java Developer");
        profile.setSkills(List.of("Java", "Spring Boot", "SQL"));

        List<JobMatchResult> matches = matchingService.matchJobs(profile, new JobMatchFilter());

        assertFalse(matches.isEmpty());
        // Highest ranked job should be the Java Backend Developer job
        assertEquals("job-java-remote", matches.get(0).getJob().getJobId());
        assertTrue(matches.get(0).getMatchPercentage() >= 75);
        assertTrue(matches.get(0).getMatchingSkills().contains("java"));
    }

    @Test
    void testRemoteOnlyFilter() {
        ResumeProfile profile = new ResumeProfile();
        profile.setSkills(List.of("React", "TypeScript"));

        JobMatchFilter filter = new JobMatchFilter();
        filter.setRemoteOnly(true);

        List<JobMatchResult> matches = matchingService.matchJobs(profile, filter);

        // All returned jobs must be remote
        for (JobMatchResult res : matches) {
            assertTrue(res.getJob().isRemote());
        }
    }

    @Test
    void testCombinedFilter_RemoteJavaInternship() {
        ResumeProfile profile = new ResumeProfile();
        profile.setSkills(List.of("Java", "Git", "SQL"));
        profile.setTargetDesignation("Software Engineer");

        JobMatchFilter filter = new JobMatchFilter();
        filter.setRemoteOnly(true);
        filter.setEmploymentType("internship");

        List<JobMatchResult> matches = matchingService.matchJobs(profile, filter);

        assertEquals(1, matches.size());
        assertEquals("job-intern-java", matches.get(0).getJob().getJobId());
        assertTrue(matches.get(0).getJob().isRemote());
        assertTrue(matches.get(0).getJob().getTitle().toLowerCase().contains("intern"));
    }

    @Test
    void testExperienceMismatchDetection() {
        ResumeProfile fresherProfile = new ResumeProfile();
        fresherProfile.setTargetDesignation("Frontend Engineer");
        fresherProfile.setSkills(List.of("React", "TypeScript", "CSS"));
        // 0 years experience

        List<JobMatchResult> matches = matchingService.matchJobs(fresherProfile, new JobMatchFilter());
        JobMatchResult seniorJob = matches.stream()
                .filter(m -> m.getJob().getJobId().equals("job-react-onsite"))
                .findFirst().orElse(null);

        assertNotNull(seniorJob);
        assertNotNull(seniorJob.getEligibilityMismatch());
        assertTrue(seniorJob.getEligibilityMismatch().contains("5"));
    }
}

