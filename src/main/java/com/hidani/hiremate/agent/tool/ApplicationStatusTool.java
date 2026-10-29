package com.hidani.hiremate.agent.tool;

import com.hidani.hiremate.agent.Intent;
import com.hidani.hiremate.model.Application;
import com.hidani.hiremate.service.ApplicationService;
import org.springframework.stereotype.Component;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Component
public class ApplicationStatusTool implements AgentTool {

    private final ApplicationService applicationService;
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("MMM dd, yyyy")
            .withZone(ZoneId.systemDefault());

    public ApplicationStatusTool(ApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    @Override
    public String getName() {
        return "ApplicationStatusTool";
    }

    @Override
    public String getDescription() {
        return "Retrieves saved application history and status updates for the current session.";
    }

    @Override
    public ToolResult execute(ToolContext context) {
        String sessionId = context.getSessionId();
        List<Application> apps = applicationService.getApplicationsForSession(sessionId);

        if (apps.isEmpty()) {
            String msg = "You haven't recorded any job applications yet in this session.\n\n"
                    + "Once you find matching positions with *\"Find jobs matching my resume\"*, you can simply say *\"Apply to job 1\"* to track your applications.";
            return ToolResult.ok(msg, Intent.VIEW_APPLICATIONS);
        }

        // Check if user asked about a specific company
        String lowerMsg = context.getUserMessage().toLowerCase();
        if (lowerMsg.contains("applied to") || lowerMsg.contains("company")) {
            for (Application app : apps) {
                if (lowerMsg.contains(app.getCompany().toLowerCase())) {
                    String formattedDate = DATE_FORMATTER.format(app.getAppliedDate());
                    String specificMsg = "Yes! You recorded an application for **" + app.getJobTitle()
                            + "** at **" + app.getCompany() + "** on " + formattedDate
                            + " with status **" + app.getStatus() + "**.";
                    return ToolResult.ok(specificMsg, Intent.CHECK_APPLICATION_STATUS)
                            .setApplications(List.of(app));
                }
            }
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Here is your recorded application history (").append(apps.size()).append(" total):\n\n");

        for (int i = 0; i < apps.size(); i++) {
            Application a = apps.get(i);
            String date = DATE_FORMATTER.format(a.getAppliedDate());
            sb.append(i + 1).append(". **").append(a.getJobTitle()).append("** at **").append(a.getCompany()).append("**\n")
              .append("   • Status: `").append(a.getStatus()).append("` | Date: ").append(date)
              .append(" | ID: `").append(a.getApplicationId().substring(0, 8)).append("...`\n");
        }

        return ToolResult.ok(sb.toString().trim(), Intent.VIEW_APPLICATIONS)
                .setApplications(apps);
    }
}

