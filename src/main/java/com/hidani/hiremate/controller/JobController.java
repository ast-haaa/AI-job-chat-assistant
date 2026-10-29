package com.hidani.hiremate.controller;

import com.hidani.hiremate.agent.OrchestratorAgent;
import com.hidani.hiremate.model.*;
import com.hidani.hiremate.service.JobDataService;
import com.hidani.hiremate.service.JobMatchingService;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/jobs")
public class JobController {

    private final JobDataService jobDataService;
    private final JobMatchingService jobMatchingService;
    private final OrchestratorAgent orchestratorAgent;

    public JobController(JobDataService jobDataService,
                         JobMatchingService jobMatchingService,
                         OrchestratorAgent orchestratorAgent) {
        this.jobDataService = jobDataService;
        this.jobMatchingService = jobMatchingService;
        this.orchestratorAgent = orchestratorAgent;
    }

    @GetMapping
    public ResponseEntity<JobPageResponse> listJobs(@RequestParam(value = "page", defaultValue = "0") int page,
                                                    @RequestParam(value = "size", defaultValue = "10") int size) {
        JobPageResponse response = jobDataService.getJobs(page, size);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/count")
    public ResponseEntity<Map<String, Long>> getJobCount() {
        return ResponseEntity.ok(Map.of("count", jobDataService.getJobCount()));
    }

    @GetMapping("/{jobId}")
    public ResponseEntity<?> getJobById(@PathVariable("jobId") String jobId) {
        return jobDataService.getJobById(jobId)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "Job not found with ID: " + jobId)));
    }

    @PostMapping("/match")
    public ResponseEntity<?> matchJobs(@RequestBody(required = false) JobMatchFilter filter,
                                       HttpSession session) {
        SessionConversationContext context = orchestratorAgent.getOrCreateSessionContext(session.getId());
        ResumeProfile profile = context.getResumeProfile();

        if (profile == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "No parsed resume found in current session. Please upload a resume first."));
        }

        JobMatchFilter activeFilter = filter != null ? filter : context.getActiveFilters();
        List<JobMatchResult> matches = jobMatchingService.matchJobs(profile, activeFilter);

        // Update session's last recommendations
        context.setLastRecommendations(matches);

        return ResponseEntity.ok(Map.of(
                "totalMatches", matches.size(),
                "results", matches
        ));
    }
}

