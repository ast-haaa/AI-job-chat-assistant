package com.hidani.hiremate.agent.tool;

import com.hidani.hiremate.agent.Intent;
import org.springframework.stereotype.Component;

@Component
public class GeneralHelpTool implements AgentTool {

    @Override
    public String getName() {
        return "GeneralHelpTool";
    }

    @Override
    public String getDescription() {
        return "Answers general inquiries, greetings, and explains HireMate AI capabilities.";
    }

    @Override
    public ToolResult execute(ToolContext context) {
        String msg = """
                Hello! I'm **HireMate AI**, your intelligent job search & application assistant.

                Here is what I can do for you:
                1. **Upload & Parse Resume**: Upload your PDF or DOCX resume using the paperclip button. I extract your skills, experience, and target roles without inventing details.
                2. **Match Real Jobs**: Say *"Find jobs matching my resume"* to evaluate real listings from our verified database with transparent match percentages.
                3. **Refine with Filters**: Try queries like:
                   - *"Show only remote jobs"*
                   - *"Find Java internships"*
                   - *"Jobs requiring React with at least 70% match"*
                   - *"Find frontend roles in Bengaluru"*
                4. **Record Applications**: Say *"Apply to job 1"* to review and locally track your applications with confirmation.
                5. **Track Application Status**: Ask *"Show my applied jobs"* or *"What is my application history?"*.

                How would you like to start today?
                """;
        return ToolResult.ok(msg.trim(), Intent.GREETING_OR_HELP);
    }
}

