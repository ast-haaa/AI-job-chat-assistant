package com.hidani.hiremate.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hidani.hiremate.model.GeminiClassificationResult;
import com.hidani.hiremate.model.SessionConversationContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;

public class GeminiServiceTest {

    private GeminiService geminiService;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        geminiService = new GeminiService(objectMapper);
    }

    @Test
    void testIsAvailable_DefaultUnset_ReturnsFalse() {
        assertFalse(geminiService.isAvailable());
    }

    @Test
    void testIsAvailable_WithApiKeySet_ReturnsTrue() {
        ReflectionTestUtils.setField(geminiService, "apiKey", "test-api-key-12345");
        assertTrue(geminiService.isAvailable());
    }

    @Test
    void testClassifyIntent_WhenNotAvailable_ReturnsNullSafely() {
        SessionConversationContext ctx = new SessionConversationContext();
        GeminiClassificationResult result = geminiService.classifyIntentAndExtractFilters("Find remote Java jobs", ctx);
        assertNull(result);
    }

    @Test
    void testClassifyIntent_WhenMessageBlank_ReturnsNullSafely() {
        ReflectionTestUtils.setField(geminiService, "apiKey", "test-api-key-12345");
        SessionConversationContext ctx = new SessionConversationContext();
        GeminiClassificationResult result = geminiService.classifyIntentAndExtractFilters("   ", ctx);
        assertNull(result);
    }

    @Test
    void testClassifyIntent_WithInvalidKeyOrNetworkError_DegradesGracefullyWithoutThrowing() {
        // Points to fake key which will fail HTTP call or throw; must return null and never throw
        ReflectionTestUtils.setField(geminiService, "apiKey", "invalid-fake-key");
        SessionConversationContext ctx = new SessionConversationContext();
        assertDoesNotThrow(() -> {
            GeminiClassificationResult result = geminiService.classifyIntentAndExtractFilters("Find jobs", ctx);
            assertNull(result);
        });
    }
}

