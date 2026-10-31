package com.hidani.hiremate.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hidani.hiremate.agent.Intent;
import com.hidani.hiremate.model.GeminiClassificationResult;
import com.hidani.hiremate.model.JobMatchFilter;
import com.hidani.hiremate.model.SessionConversationContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Service for optional Google Gemini LLM API integration.
 * Uses native Java 21 HttpClient with zero external SDK overhead.
 * Gracefully degrades to rule-based NLU when no key is provided or on network errors.
 */
@Service
public class GeminiService {

    private static final Logger log = LoggerFactory.getLogger(GeminiService.class);
    private static final String GEMINI_API_ENDPOINT = "https://generativelanguage.googleapis.com/v1beta/models/";

    @Value("${gemini.api.key:}")
    private String apiKey;

    @Value("${gemini.model:gemini-1.5-flash}")
    private String modelName;

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public GeminiService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(6))
                .build();
    }

    /**
     * Checks if Gemini API key is configured and active.
     */
    public boolean isAvailable() {
        return apiKey != null && !apiKey.trim().isEmpty();
    }

    /**
     * Classifies intent and extracts search/filter parameters using Gemini LLM.
     * Returns null if service is unavailable, response is invalid, or an error occurs.
     */
    public GeminiClassificationResult classifyIntentAndExtractFilters(String userMessage, SessionConversationContext context) {
        if (!isAvailable() || userMessage == null || userMessage.isBlank()) {
            return null;
        }

        try {
            boolean hasResume = context != null && context.getResumeProfile() != null;
            boolean hasPendingConfirmation = context != null && context.getPendingConfirmation() != null;

            String systemPrompt = """
                    You are the NLU parser for HireMate AI, a career assistant.
                    Analyze the user message and output a JSON object with:
                    - "intent": One of [GREETING_OR_HELP, MATCH_JOBS, REFINE_FILTER, APPLY_JOB, CONFIRM_APPLICATION, CANCEL_APPLICATION, VIEW_APPLICATIONS, CHECK_APPLICATION_STATUS, RESUME_STATUS, UNKNOWN]
                    - "targetJobNumber": Integer (if user mentions job number like 'apply to job 2', or null)
                    - "remoteOnly": Boolean or null (true if user specifies remote)
                    - "employmentType": String or null ('internship', 'full-time', etc.)
                    - "requiredSkills": List of skill strings mentioned (e.g. ['React', 'Java'])
                    - "titleKeyword": String or null (e.g. 'Backend', 'Frontend', 'DevOps')
                    - "location": String or null (city/country, or null)
                    - "minMatchPercentage": Integer or null (e.g. 70)
                    - "reasoning": Short explanation of intent choice

                    Context info:
                    - User has resume loaded: %s
                    - User has pending application confirmation: %s
                    """.formatted(hasResume, hasPendingConfirmation);

            String requestBody = objectMapper.writeValueAsString(Map.of(
                    "contents", List.of(
                            Map.of("parts", List.of(
                                    Map.of("text", systemPrompt + "\nUser message: " + userMessage)
                            ))
                    ),
                    "generationConfig", Map.of(
                            "temperature", 0.1,
                            "responseMimeType", "application/json"
                    )
            ));

            String cleanModel = modelName != null && !modelName.isBlank() ? modelName.trim() : "gemini-1.5-flash";
            String url = GEMINI_API_ENDPOINT + cleanModel + ":generateContent?key=" + apiKey.trim();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(6))
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                log.warn("Gemini API call failed with status code: {}", response.statusCode());
                return null;
            }

            JsonNode root = objectMapper.readTree(response.body());
            JsonNode textNode = root.at("/candidates/0/content/parts/0/text");
            if (textNode.isMissingNode()) {
                return null;
            }

            String geminiJson = textNode.asText();
            JsonNode parsed = objectMapper.readTree(geminiJson);

            String intentStr = parsed.path("intent").asText(null);
            if (intentStr == null) return null;

            Intent intent;
            try {
                intent = Intent.valueOf(intentStr.toUpperCase().trim());
            } catch (IllegalArgumentException e) {
                return null;
            }

            Integer targetJobNumber = parsed.has("targetJobNumber") && !parsed.path("targetJobNumber").isNull()
                    ? parsed.path("targetJobNumber").asInt()
                    : null;

            JobMatchFilter filter = new JobMatchFilter();
            if (parsed.has("remoteOnly") && !parsed.path("remoteOnly").isNull()) {
                filter.setRemoteOnly(parsed.path("remoteOnly").asBoolean());
            }
            if (parsed.has("employmentType") && !parsed.path("employmentType").isNull()) {
                filter.setEmploymentType(parsed.path("employmentType").asText());
            }
            if (parsed.has("titleKeyword") && !parsed.path("titleKeyword").isNull()) {
                filter.setTitleKeyword(parsed.path("titleKeyword").asText());
            }
            if (parsed.has("location") && !parsed.path("location").isNull()) {
                filter.setLocation(parsed.path("location").asText());
            }
            if (parsed.has("minMatchPercentage") && !parsed.path("minMatchPercentage").isNull()) {
                filter.setMinMatchPercentage(parsed.path("minMatchPercentage").asInt());
            }
            if (parsed.has("requiredSkills") && parsed.path("requiredSkills").isArray()) {
                List<String> skills = new ArrayList<>();
                for (JsonNode sk : parsed.path("requiredSkills")) {
                    skills.add(sk.asText());
                }
                filter.setRequiredSkills(skills);
            }

            String reasoning = parsed.path("reasoning").asText("");

            return new GeminiClassificationResult(intent, targetJobNumber, filter, reasoning);

        } catch (Exception e) {
            log.warn("Gemini intent classification encountered an error, falling back to rule-based engine: {}", e.getMessage());
            return null;
        }
    }
}

