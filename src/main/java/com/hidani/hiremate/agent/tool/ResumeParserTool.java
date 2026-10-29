package com.hidani.hiremate.agent.tool;

import com.hidani.hiremate.agent.Intent;
import com.hidani.hiremate.model.ResumeProfile;
import org.springframework.stereotype.Component;

@Component
public class ResumeParserTool implements AgentTool {

    @Override
    public String getName() {
        return "ResumeParserTool";
    }

    @Override
    public String getDescription() {
        return "Inspects the status of the uploaded resume in the current session.";
    }

    @Override
    public ToolResult execute(ToolContext context) {
        ResumeProfile profile = context.getSessionContext().getResumeProfile();
        if (profile == null) {
            String msg = "No resume has been uploaded in this session yet. Please click the **paperclip** icon or drag & drop a **PDF** or **DOCX** file to begin!";
            return ToolResult.ok(msg, Intent.RESUME_STATUS);
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Here is your active resume profile for **")
                .append(profile.getName() != null ? profile.getName() : profile.getFileName())
                .append("**:\n\n");

        if (profile.getTargetDesignation() != null) {
            sb.append("• **Target Role**: ").append(profile.getTargetDesignation()).append("\n");
        }
        if (profile.getEmail() != null) {
            sb.append("• **Email**: ").append(profile.getEmail()).append("\n");
        }
        if (profile.getPhone() != null) {
            sb.append("• **Phone**: ").append(profile.getPhone()).append("\n");
        }
        if (!profile.getSkills().isEmpty()) {
            sb.append("• **Detected Skills (").append(profile.getSkills().size()).append(")**: ")
                    .append(String.join(", ", profile.getSkills())).append("\n");
        }
        sb.append("\nYou can now say *\"Find jobs matching my resume\"* or ask for specific roles like *\"Show remote Java internships\"*!");

        return ToolResult.ok(sb.toString().trim(), Intent.RESUME_STATUS).setResumeProfile(profile);
    }
}

