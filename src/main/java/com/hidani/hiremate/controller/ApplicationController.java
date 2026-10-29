package com.hidani.hiremate.controller;

import com.hidani.hiremate.agent.OrchestratorAgent;
import com.hidani.hiremate.model.Application;
import com.hidani.hiremate.model.ApplicationRequest;
import com.hidani.hiremate.model.JobMatchResult;
import com.hidani.hiremate.model.SessionConversationContext;
import com.hidani.hiremate.service.ApplicationService;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/applications")
public class ApplicationController {

    private final ApplicationService applicationService;
    private final OrchestratorAgent orchestratorAgent;

    public ApplicationController(ApplicationService applicationService, OrchestratorAgent orchestratorAgent) {
        this.applicationService = applicationService;
        this.orchestratorAgent = orchestratorAgent;
    }

    @PostMapping
    public ResponseEntity<?> applyToJob(@RequestBody ApplicationRequest request, HttpSession session) {
        if (request == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Application request cannot be empty."));
        }

        String jobId = request.getJobId();

        // If displayIndex was provided instead of jobId, resolve from session's recommendations
        if ((jobId == null || jobId.isBlank()) && request.getDisplayIndex() != null) {
            SessionConversationContext context = orchestratorAgent.getOrCreateSessionContext(session.getId());
            JobMatchResult match = context.findRecommendationByDisplayIndex(request.getDisplayIndex());
            if (match != null && match.getJob() != null) {
                jobId = match.getJob().getJobId();
            } else {
                return ResponseEntity.badRequest().body(Map.of("error", "Invalid job recommendation number: " + request.getDisplayIndex()));
            }
        }

        if (jobId == null || jobId.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Either jobId or a valid displayIndex must be provided."));
        }

        try {
            Application application = applicationService.applyToJob(session.getId(), jobId, request.getNotes());
            return ResponseEntity.status(HttpStatus.CREATED).body(application);
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to record application: " + e.getMessage()));
        }
    }

    @GetMapping
    public ResponseEntity<List<Application>> getApplications(HttpSession session) {
        List<Application> list = applicationService.getApplicationsForSession(session.getId());
        return ResponseEntity.ok(list);
    }

    @GetMapping("/{applicationId}")
    public ResponseEntity<?> getApplicationById(@PathVariable("applicationId") String applicationId,
                                                HttpSession session) {
        return applicationService.getApplicationById(session.getId(), applicationId)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "Application not found with ID: " + applicationId)));
    }
}

