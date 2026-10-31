package com.hidani.hiremate.controller;

import com.hidani.hiremate.agent.OrchestratorAgent;
import com.hidani.hiremate.model.ResumeProfile;
import com.hidani.hiremate.model.SessionConversationContext;
import com.hidani.hiremate.service.ResumeParserService;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api/resume")
public class ResumeController {

    private static final Logger log = LoggerFactory.getLogger(ResumeController.class);

    private final ResumeParserService resumeParserService;
    private final OrchestratorAgent orchestratorAgent;

    public ResumeController(ResumeParserService resumeParserService, OrchestratorAgent orchestratorAgent) {
        this.resumeParserService = resumeParserService;
        this.orchestratorAgent = orchestratorAgent;
    }

    @PostMapping("/parse")
    public ResponseEntity<?> uploadAndParseResume(@RequestParam("file") MultipartFile file,
                                                  @RequestParam(value = "conversationId", required = false) String conversationId,
                                                  HttpSession session) {
        try {
            ResumeProfile profile = resumeParserService.parseResume(file);

            // Store in global user session context
            SessionConversationContext sessionContext = orchestratorAgent.getOrCreateSessionContext(session.getId());
            sessionContext.setResumeProfile(profile);

            // Also attach to specific conversation if provided
            if (conversationId != null && !conversationId.isBlank()) {
                SessionConversationContext convContext = orchestratorAgent.getOrCreateSessionContext(conversationId.trim());
                convContext.setResumeProfile(profile);
            }

            log.info("Successfully parsed and stored resume for session {}: {} ({})",
                    session.getId(), profile.getName(), profile.getFileName());

            return ResponseEntity.ok(profile);

        } catch (IllegalArgumentException e) {
            log.warn("Resume validation error: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Unexpected error parsing resume: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to process resume: " + e.getMessage()));
        }
    }

    @GetMapping("/current")
    public ResponseEntity<?> getCurrentProfile(HttpSession session) {
        SessionConversationContext context = orchestratorAgent.getOrCreateSessionContext(session.getId());
        ResumeProfile profile = context.getResumeProfile();
        if (profile == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", "No resume uploaded in this session."));
        }
        return ResponseEntity.ok(profile);
    }
}

