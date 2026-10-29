package com.hidani.hiremate.controller;

import com.hidani.hiremate.agent.OrchestratorAgent;
import com.hidani.hiremate.model.ChatRequest;
import com.hidani.hiremate.model.ChatResponse;
import com.hidani.hiremate.model.ResumeProfile;
import com.hidani.hiremate.model.SessionConversationContext;
import com.hidani.hiremate.service.ResumeParserService;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private static final Logger log = LoggerFactory.getLogger(ChatController.class);

    private final OrchestratorAgent orchestratorAgent;
    private final ResumeParserService resumeParserService;

    public ChatController(OrchestratorAgent orchestratorAgent, ResumeParserService resumeParserService) {
        this.orchestratorAgent = orchestratorAgent;
        this.resumeParserService = resumeParserService;
    }

    /**
     * Standard JSON chat message endpoint.
     */
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ChatResponse> chatJson(@RequestBody ChatRequest request, HttpSession session) {
        String userMessage = request != null && request.getMessage() != null ? request.getMessage().trim() : "";
        log.info("Received chat message from session {}: '{}'", session.getId(), userMessage);

        ChatResponse response = orchestratorAgent.processMessage(session.getId(), userMessage);
        return ResponseEntity.ok(response);
    }

    /**
     * Multipart chat endpoint supporting file attachment along with message.
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ChatResponse> chatMultipart(@RequestParam(value = "message", required = false) String message,
                                                      @RequestParam(value = "file", required = false) MultipartFile file,
                                                      HttpSession session) {
        String userMessage = message != null ? message.trim() : "";

        // If file is attached in chat request, parse and save to session
        if (file != null && !file.isEmpty()) {
            try {
                ResumeProfile profile = resumeParserService.parseResume(file);
                SessionConversationContext ctx = orchestratorAgent.getOrCreateSessionContext(session.getId());
                ctx.setResumeProfile(profile);

                String followUp = !userMessage.isBlank() ? userMessage : "Find jobs matching my resume";
                ChatResponse response = orchestratorAgent.processMessage(session.getId(), followUp);
                response.setResumeProfile(profile);

                String combinedMessage = "Uploaded and parsed **" + file.getOriginalFilename() + "** successfully!\n\n"
                        + response.getMessage();
                response.setMessage(combinedMessage);

                return ResponseEntity.ok(response);

            } catch (Exception e) {
                ChatResponse errResponse = new ChatResponse("Could not parse attached resume: " + e.getMessage());
                return ResponseEntity.badRequest().body(errResponse);
            }
        }

        ChatResponse response = orchestratorAgent.processMessage(session.getId(), userMessage);
        return ResponseEntity.ok(response);
    }

    /**
     * Clear session context / start a new chat.
     */
    @PostMapping("/reset")
    public ResponseEntity<?> resetSession(HttpSession session) {
        session.invalidate();
        return ResponseEntity.ok(Map.of("message", "Session reset successfully."));
    }
}

