package com.hidani.hiremate.agent;

import com.hidani.hiremate.agent.tool.*;
import com.hidani.hiremate.model.ChatResponse;
import com.hidani.hiremate.model.GeminiClassificationResult;
import com.hidani.hiremate.model.JobMatchFilter;
import com.hidani.hiremate.model.SessionConversationContext;
import com.hidani.hiremate.service.GeminiService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
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
    private final GeminiService geminiService;

    // Session-isolated context registry (sessionId -> SessionConversationContext)
    private final Map<String, SessionConversationContext> sessionRegistry = new ConcurrentHashMap<>();

    @Autowired
    public OrchestratorAgent(IntentClassifier intentClassifier,
                             GeneralHelpTool generalHelpTool,
                             ResumeParserTool resumeParserTool,
                             JobMatchingTool jobMatchingTool,
                             ApplicationTool applicationTool,
                             ApplicationStatusTool applicationStatusTool,
                             GeminiService geminiService) {
        this.intentClassifier = intentClassifier;
        this.generalHelpTool = generalHelpTool;
        this.resumeParserTool = resumeParserTool;
        this.jobMatchingTool = jobMatchingTool;
        this.applicationTool = applicationTool;
        this.applicationStatusTool = applicationStatusTool;
        this.geminiService = geminiService;
    }

    public OrchestratorAgent(IntentClassifier intentClassifier,
                             GeneralHelpTool generalHelpTool,
                             ResumeParserTool resumeParserTool,
                             JobMatchingTool jobMatchingTool,
                             ApplicationTool applicationTool,
                             ApplicationStatusTool applicationStatusTool) {
        this(intentClassifier, generalHelpTool, resumeParserTool, jobMatchingTool, applicationTool, applicationStatusTool, null);
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
     * Process an incoming conversational message from a user session (backward compatible).
     */
    public ChatResponse processMessage(String sessionId, String userMessage) {
        return processMessage(sessionId, userMessage, sessionId);
    }

    /**
     * Process an incoming conversational message for a specific conversation ID within a user session.
     * Ensures each conversation maintains its own context, filters, recommendations, and confirmation state,
     * while sharing the candidate's resume profile and application tracking across the user session.
     */
    public ChatResponse processMessage(String conversationId, String userMessage, String userSessionId) {
        String effectiveConvId = conversationId != null && !conversationId.isBlank() ? conversationId.trim() : userSessionId;
        SessionConversationContext sessionContext = getOrCreateSessionContext(effectiveConvId);

        // If conversation context has no resume profile, check if the parent user session has one
        if (sessionContext.getResumeProfile() == null && userSessionId != null && !userSessionId.isBlank()) {
            SessionConversationContext parentContext = sessionRegistry.get(userSessionId);
            if (parentContext != null && parentContext.getResumeProfile() != null) {
                sessionContext.setResumeProfile(parentContext.getResumeProfile());
            }
        }

        Intent intent = null;
        JobMatchFilter filters = null;
        Integer targetJobNum = null;

        // If Gemini LLM is configured, use it for intelligent intent classification and parameter extraction
        if (geminiService != null && geminiService.isAvailable()) {
            try {
                GeminiClassificationResult geminiResult = geminiService.classifyIntentAndExtractFilters(userMessage, sessionContext);
                if (geminiResult != null && geminiResult.getIntent() != null) {
                    intent = geminiResult.getIntent();
                    filters = geminiResult.getFilter();
                    targetJobNum = geminiResult.getTargetJobNumber();
                    log.info("Gemini classified intent: '{}' (conv: {}, session: {})", intent, effectiveConvId, userSessionId);
                }
            } catch (Exception e) {
                log.warn("Gemini intent classification failed, falling back to rule-based engine: {}", e.getMessage());
            }
        }

        // Fallback to deterministic rule-based IntentClassifier
        if (intent == null) {
            intent = intentClassifier.classifyIntent(userMessage, sessionContext);
            log.info("Rule-based classified intent: '{}' for message: '{}' (conv: {}, session: {})", intent, userMessage, effectiveConvId, userSessionId);
        }

        // Pass userSessionId for session-scoped actions (e.g. application recording) while sessionContext holds conversation-scoped state
        String trackingSessionId = (userSessionId != null && !userSessionId.isBlank()) ? userSessionId : effectiveConvId;
        ToolContext toolContext = new ToolContext(trackingSessionId, userMessage, intent, sessionContext);

        // Pre-populate tool context based on intent
        if (intent == Intent.MATCH_JOBS || intent == Intent.REFINE_FILTER) {
            if (filters == null || !filters.hasAnyFilter()) {
                filters = intentClassifier.extractFilters(userMessage);
            }
            toolContext.setExtractedFilter(filters);
        } else if (intent == Intent.APPLY_JOB) {
            if (targetJobNum == null) {
                targetJobNum = intentClassifier.extractJobNumber(userMessage);
            }
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

    /**
     * Removes the conversation context when a conversation is deleted by the user.
     */
    public void removeConversationContext(String conversationId) {
        if (conversationId != null && !conversationId.isBlank()) {
            sessionRegistry.remove(conversationId.trim());
            log.info("Removed conversation context for ID: {}", conversationId);
        }
    }
}

