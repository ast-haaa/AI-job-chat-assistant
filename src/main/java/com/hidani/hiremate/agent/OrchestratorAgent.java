package com.hidani.hiremate.agent;

import com.hidani.hiremate.agent.tool.*;
import com.hidani.hiremate.model.ChatResponse;
import com.hidani.hiremate.model.JobMatchFilter;
import com.hidani.hiremate.model.SessionConversationContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class OrchestratorAgent {

    private static final Logger log = LoggerFactory.getLogger(OrchestratorAgent.class);

    private final IntentClassifier intentClassifier;
    private final GeneralHelpTool generalHelpTool;
    private final ResumeParserTool resumeParserTool;
    private final JobMatchingTool jobMatchingTool;
    private final ApplicationTool applicationTool;
    private final ApplicationStatusTool applicationStatusTool;

    // Session-isolated context registry (sessionId -> SessionConversationContext)
    private final Map<String, SessionConversationContext> sessionRegistry = new ConcurrentHashMap<>();

    public OrchestratorAgent(IntentClassifier intentClassifier,
                             GeneralHelpTool generalHelpTool,
                             ResumeParserTool resumeParserTool,
                             JobMatchingTool jobMatchingTool,
                             ApplicationTool applicationTool,
                             ApplicationStatusTool applicationStatusTool) {
        this.intentClassifier = intentClassifier;
        this.generalHelpTool = generalHelpTool;
        this.resumeParserTool = resumeParserTool;
        this.jobMatchingTool = jobMatchingTool;
        this.applicationTool = applicationTool;
        this.applicationStatusTool = applicationStatusTool;
    }

    /**
     * Retrieves or initializes session-isolated conversation context.
     */
    public SessionConversationContext getOrCreateSessionContext(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            throw new IllegalArgumentException("Session ID cannot be empty.");
        }
        return sessionRegistry.computeIfAbsent(sessionId, k -> new SessionConversationContext());
    }

    /**
     * Process an incoming conversational message from a user session.
     */
    public ChatResponse processMessage(String sessionId, String userMessage) {
        SessionConversationContext sessionContext = getOrCreateSessionContext(sessionId);

        Intent intent = intentClassifier.classifyIntent(userMessage, sessionContext);
        log.info("Classified intent: '{}' for message: '{}' (session: {})", intent, userMessage, sessionId);

        ToolContext toolContext = new ToolContext(sessionId, userMessage, intent, sessionContext);

        // Pre-populate tool context based on intent
        if (intent == Intent.MATCH_JOBS || intent == Intent.REFINE_FILTER) {
            JobMatchFilter filters = intentClassifier.extractFilters(userMessage);
            toolContext.setExtractedFilter(filters);
        } else if (intent == Intent.APPLY_JOB) {
            Integer targetJobNum = intentClassifier.extractJobNumber(userMessage);
            toolContext.setTargetJobNumber(targetJobNum);
        }

        // Route to the appropriate tool
        ToolResult result;
        try {
            switch (intent) {
                case GREETING_OR_HELP -> result = generalHelpTool.execute(toolContext);
                case RESUME_STATUS -> result = resumeParserTool.execute(toolContext);
                case MATCH_JOBS, REFINE_FILTER -> result = jobMatchingTool.execute(toolContext);
                case APPLY_JOB, CONFIRM_APPLICATION, CANCEL_APPLICATION -> result = applicationTool.execute(toolContext);
                case VIEW_APPLICATIONS, CHECK_APPLICATION_STATUS -> result = applicationStatusTool.execute(toolContext);
                case UNKNOWN -> {
                    // Try to provide a helpful response or fallback to help
                    if (sessionContext.getResumeProfile() != null) {
                        result = ToolResult.ok(
                                "I'm not quite sure how to handle that. Would you like me to find jobs matching your resume, or filter existing listings (e.g., *\"Show remote jobs\"*, *\"Apply to job 1\"*)?",
                                Intent.UNKNOWN
                        );
                    } else {
                        result = generalHelpTool.execute(toolContext);
                    }
                }
                default -> result = generalHelpTool.execute(toolContext);
            }
        } catch (Exception e) {
            log.error("Tool execution failed for intent {}: {}", intent, e.getMessage(), e);
            result = ToolResult.error("An unexpected error occurred: " + e.getMessage(), intent);
        }

        // Map ToolResult to user-facing ChatResponse
        ChatResponse response = new ChatResponse(result.getResponseText());
        response.setIntent(intent.name());
        response.setResumeProfile(result.getResumeProfile());
        response.setJobMatches(result.getJobMatches());
        response.setApplications(result.getApplications());
        response.setConfirmationRequired(result.isConfirmationRequired());
        response.setPendingConfirmation(result.getPendingConfirmation());

        return response;
    }
}

