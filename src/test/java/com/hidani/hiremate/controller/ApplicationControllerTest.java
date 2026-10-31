package com.hidani.hiremate.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hidani.hiremate.model.ApplicationRequest;
import com.hidani.hiremate.service.ApplicationService;
import com.hidani.hiremate.service.JobDataService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "hiremate.data.applications-path=${java.io.tmpdir}/test-applications.json"
})
public class ApplicationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JobDataService jobDataService;

    @Test
    void testApplicationRestEndpoints_EndToEnd() throws Exception {
        MockHttpSession session = new MockHttpSession(null, "test-session-" + java.util.UUID.randomUUID());

        // Initially applications list for session is empty
        mockMvc.perform(get("/api/applications").session(session))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$", hasSize(0)));

        // Retrieve a valid job ID from loaded dataset
        String validJobId = jobDataService.getAllJobs().get(0).getJobId();

        // Apply to valid job
        ApplicationRequest req = new ApplicationRequest(validJobId);
        req.setNotes("First application attempt");

        mockMvc.perform(post("/api/applications")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.applicationId").isNotEmpty())
                .andExpect(jsonPath("$.jobId").value(validJobId))
                .andExpect(jsonPath("$.status").value("Applied"));

        // Attempting duplicate application in same session must return 409 Conflict
        mockMvc.perform(post("/api/applications")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error", containsString("already recorded")));

        // Applying to invalid non-existent job must return 404 Not Found
        ApplicationRequest invalidReq = new ApplicationRequest("non-existent-job-id-99999");
        mockMvc.perform(post("/api/applications")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidReq)))
                .andExpect(status().isNotFound());

        // Retrieve all applications for session (should now be 1)
        mockMvc.perform(get("/api/applications").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].jobId").value(validJobId));
    }
}
