package com.hidani.hiremate.model;

import com.hidani.hiremate.agent.Intent;

/**
 * Result of Gemini LLM intent classification and parameter extraction.
 */
public class GeminiClassificationResult {

    private Intent intent;
    private Integer targetJobNumber;
    private JobMatchFilter filter;
    private String reasoning;

    public GeminiClassificationResult() {
    }

    public GeminiClassificationResult(Intent intent, Integer targetJobNumber, JobMatchFilter filter, String reasoning) {
        this.intent = intent;
        this.targetJobNumber = targetJobNumber;
        this.filter = filter;
        this.reasoning = reasoning;
    }

    public Intent getIntent() {
        return intent;
    }

    public void setIntent(Intent intent) {
        this.intent = intent;
    }

    public Integer getTargetJobNumber() {
        return targetJobNumber;
    }

    public void setTargetJobNumber(Integer targetJobNumber) {
        this.targetJobNumber = targetJobNumber;
    }

    public JobMatchFilter getFilter() {
        return filter;
    }

    public void setFilter(JobMatchFilter filter) {
        this.filter = filter;
    }

    public String getReasoning() {
        return reasoning;
    }

    public void setReasoning(String reasoning) {
        this.reasoning = reasoning;
    }
}

