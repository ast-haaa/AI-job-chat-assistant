package com.hidani.hiremate.agent.tool;

public interface AgentTool {
    String getName();
    String getDescription();
    ToolResult execute(ToolContext context);
}

