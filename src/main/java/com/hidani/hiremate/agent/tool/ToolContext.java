package com.hidani.hiremate.agent.tool;

import com.hidani.hiremate.agent.Intent;
import com.hidani.hiremate.model.JobMatchFilter;
import com.hidani.hiremate.model.SessionConversationContext;

public class ToolContext {
    private final String sessionId;
    private final String userMessage;
    private final Intent intent;
    private final SessionConversationContext sessionContext;
    private JobMatchFilter extractedFilter;
    private Integer targetJobNumber;
    private String targetJobId;

    public ToolContext(String sessionId, String userMessage, Intent intent, SessionConversationContext sessionContext) {
        this.sessionId = sessionId;
        this.userMessage = userMessage;
        this.intent = intent;
        this.sessionContext = sessionContext;
    }

    public String getSessionId() {
        return sessionId;
    }

    public String getUserMessage() {
        return userMessage;
    }

    public Intent getIntent() {
        return intent;
    }

    public SessionConversationContext getSessionContext() {
        return sessionContext;
    }

    public JobMatchFilter getExtractedFilter() {
        return extractedFilter;
    }

    public void setExtractedFilter(JobMatchFilter extractedFilter) {
        this.extractedFilter = extractedFilter;
    }

    public Integer getTargetJobNumber() {
        return targetJobNumber;
    }

    public void setTargetJobNumber(Integer targetJobNumber) {
        this.targetJobNumber = targetJobNumber;
    }

    public String getTargetJobId() {
        return targetJobId;
    }

    public void setTargetJobId(String targetJobId) {
        this.targetJobId = targetJobId;
    }
}

