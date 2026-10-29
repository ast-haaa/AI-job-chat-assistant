package com.hidani.hiremate.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hidani.hiremate.agent.tool.*;
import com.hidani.hiremate.model.*;
import com.hidani.hiremate.service.ApplicationService;
import com.hidani.hiremate.service.JobDataService;
import com.hidani.hiremate.service.JobMatchingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class OrchestratorAgentTest {

    private OrchestratorAgent orchestratorAgent;
    private IntentClassifier intentClassifier;
    private JobDataService jobDataService;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() throws IOException {
        ObjectMapper mapper = new ObjectMapper();

        Path jobsFile = tempDir.resolve("jobs.json");
        List<Job> sampleJobs = List.of(
                new Job("j-1", "Java Developer", "Alpha Tech", "Remote", "1 yr", List.of("Java"), "Desc", "url1", "Full Time", true),
                new Job("j-2", "React Frontend Engineer", "Beta Labs", "Bengaluru", "0 yrs", List.of("React"), "Desc", "url2", "Internship", true),
                new Job("j-3", "DevOps Engineer", "Gamma Cloud", "Remote", "3 yrs", List.of("Docker", "AWS"), "Desc", "url3", "Full Time", true)
        );
        mapper.writeValue(jobsFile.toFile(), sampleJobs);

        jobDataService = new JobDataService(mapper);
        jobDataService.setJobsFilePath(jobsFile.toString());
        jobDataService.loadJobsFromFile();

        JobMatchingService jobMatchingService = new JobMatchingService(jobDataService);

        ApplicationService applicationService = new ApplicationService(mapper, jobDataService);
        applicationService.setApplicationsFilePath(tempDir.resolve("applications.json").toString());
        applicationService.init();

        intentClassifier = new IntentClassifier();
        GeneralHelpTool helpTool = new GeneralHelpTool();
        ResumeParserTool resumeTool = new ResumeParserTool();
        JobMatchingTool matchTool = new JobMatchingTool(jobMatchingService);
        ApplicationTool appTool = new ApplicationTool(applicationService);
        ApplicationStatusTool statusTool = new ApplicationStatusTool(applicationService);

        orchestratorAgent = new OrchestratorAgent(
                intentClassifier, helpTool, resumeTool, matchTool, appTool, statusTool
        );
    }

    @Test
    void testIntentClassification_Greetings() {
        SessionConversationContext ctx = new SessionConversationContext();
        assertEquals(Intent.GREETING_OR_HELP, intentClassifier.classifyIntent("Hi, what can you do?", ctx));
        assertEquals(Intent.GREETING_OR_HELP, intentClassifier.classifyIntent("hello", ctx));
    }

    @Test
    void testIntentClassification_JobMatchingAndFilters() {
        SessionConversationContext ctx = new SessionConversationContext();
        assertEquals(Intent.MATCH_JOBS, intentClassifier.classifyIntent("Find jobs matching my resume", ctx));
        assertEquals(Intent.MATCH_JOBS, intentClassifier.classifyIntent("Show remote Java internships", ctx));

        JobMatchFilter filter = intentClassifier.extractFilters("Show remote Java internships with at least 70% match");
        assertTrue(filter.getRemoteOnly());
        assertEquals("internship", filter.getEmploymentType());
        assertTrue(filter.getRequiredSkills().contains("Java"));
        assertEquals(70, filter.getMinMatchPercentage());
    }

    @Test
    void testMissingResumeResponse() {
        ChatResponse resp = orchestratorAgent.processMessage("sess-1", "Find jobs matching my resume");
        assertNotNull(resp);
        assertTrue(resp.getMessage().contains("no resume has been uploaded"));
        assertTrue(resp.getJobMatches().isEmpty());
    }

    @Test
    void testContextPreservationAndJobNumberResolution() {
        String sessionId = "sess-context-test";
        SessionConversationContext ctx = orchestratorAgent.getOrCreateSessionContext(sessionId);

        // Populate mock resume profile
        ResumeProfile profile = new ResumeProfile();
        profile.setName("Alex Developer");
        profile.setTargetDesignation("Java Developer");
        profile.setSkills(List.of("Java", "React"));
        ctx.setResumeProfile(profile);

        // 1. Ask for jobs
        ChatResponse matchResp = orchestratorAgent.processMessage(sessionId, "Find jobs matching my resume");
        assertFalse(matchResp.getJobMatches().isEmpty());
        int firstResultDisplayIndex = matchResp.getJobMatches().get(0).getDisplayIndex();
        assertEquals(1, firstResultDisplayIndex);

        // 2. Say "Apply to job 1"
        ChatResponse applyResp = orchestratorAgent.processMessage(sessionId, "Apply to job 1");
        assertTrue(applyResp.isConfirmationRequired());
        assertNotNull(applyResp.getPendingConfirmation());
        assertTrue(applyResp.getMessage().contains("Would you like me to record this application?"));

        // 3. Confirm with "Yes, apply"
        ChatResponse confirmResp = orchestratorAgent.processMessage(sessionId, "Yes, apply");
        assertTrue(confirmResp.getMessage().contains("Application successfully recorded"));
        assertFalse(confirmResp.getApplications().isEmpty());
    }

    @Test
    void testInvalidJobNumberRequest() {
        String sessionId = "sess-invalid-num";
        SessionConversationContext ctx = orchestratorAgent.getOrCreateSessionContext(sessionId);
        ResumeProfile profile = new ResumeProfile();
        profile.setSkills(List.of("Java"));
        ctx.setResumeProfile(profile);

        orchestratorAgent.processMessage(sessionId, "Find jobs matching my resume");

        // Reference out of bounds job number
        ChatResponse invalidResp = orchestratorAgent.processMessage(sessionId, "Apply to job 999");
        assertTrue(invalidResp.getMessage().contains("Please specify a valid job number"));
    }
}

