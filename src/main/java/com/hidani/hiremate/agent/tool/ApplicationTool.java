package com.hidani.hiremate.agent.tool;

import com.hidani.hiremate.agent.Intent;
import com.hidani.hiremate.model.Application;
import com.hidani.hiremate.model.JobMatchResult;
import com.hidani.hiremate.model.SessionConversationContext;
import com.hidani.hiremate.service.ApplicationService;
import org.springframework.stereotype.Component;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Component
public class ApplicationTool implements AgentTool {

    private final ApplicationService applicationService;
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("MMM dd, yyyy HH:mm")
            .withZone(ZoneId.systemDefault());

    public ApplicationTool(ApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    @Override
    public String getName() {
        return "ApplicationTool";
    }

    @Override
    public String getDescription() {
        return "Handles job application selection, confirmation prompts, and recording.";
    }

    @Override
    public ToolResult execute(ToolContext context) {
        SessionConversationContext sessionContext = context.getSessionContext();
        Intent intent = context.getIntent();

        // Handle confirmation of an already pending application
        if (intent == Intent.CONFIRM_APPLICATION) {
            SessionConversationContext.PendingConfirmation pending = sessionContext.getPendingConfirmation();
            if (pending == null) {
                return ToolResult.ok("There are no pending applications waiting for your confirmation. To apply, select a job number like *\"Apply to job 1\"*.", Intent.CONFIRM_APPLICATION);
            }

            try {
                Application saved = applicationService.applyToJob(context.getSessionId(), pending.getJobId(), "Application confirmed via chat");
                sessionContext.clearPendingConfirmation();

                String dateStr = DATE_FORMATTER.format(saved.getAppliedDate());
                String response = "Application successfully recorded!\n\n"
                        + "• **Role**: " + saved.getJobTitle() + "\n"
                        + "• **Company**: " + saved.getCompany() + "\n"
                        + "• **Status**: " + saved.getStatus() + "\n"
                        + "• **Date**: " + dateStr + "\n"
                        + "• **Application ID**: `" + saved.getApplicationId() + "`\n\n"
                        + "*Transparency Notice*: This application is tracked locally in your HireMate dashboard. To complete external submission directly with the employer, use the **View Original Post ↗** link on the job card.";

                return ToolResult.ok(response, Intent.CONFIRM_APPLICATION)
                        .setApplications(List.of(saved));

            } catch (IllegalStateException e) {
                sessionContext.clearPendingConfirmation();
                return ToolResult.error("Notice: " + e.getMessage(), Intent.CONFIRM_APPLICATION);
            } catch (Exception e) {
                sessionContext.clearPendingConfirmation();
                return ToolResult.error("Unable to record application: " + e.getMessage(), Intent.CONFIRM_APPLICATION);
            }
        }

        // Handle cancellation of pending application
        if (intent == Intent.CANCEL_APPLICATION) {
            SessionConversationContext.PendingConfirmation pending = sessionContext.getPendingConfirmation();
            sessionContext.clearPendingConfirmation();
            if (pending != null) {
                return ToolResult.ok("Application to **" + pending.getJobTitle() + "** at **" + pending.getCompany() + "** has been cancelled.", Intent.CANCEL_APPLICATION);
            } else {
                return ToolResult.ok("No pending application to cancel.", Intent.CANCEL_APPLICATION);
            }
        }

        // Initiate new application: Resolve job number from recommendations
        Integer targetNumber = context.getTargetJobNumber();
        List<JobMatchResult> recentRecommendations = sessionContext.getLastRecommendations();

        if (recentRecommendations == null || recentRecommendations.isEmpty()) {
            return ToolResult.error("You don't have any active job recommendations displayed yet. Please say *\"Find jobs matching my resume\"* first!", Intent.APPLY_JOB);
        }

        if (targetNumber == null || targetNumber <= 0 || targetNumber > recentRecommendations.size()) {
            return ToolResult.error("Please specify a valid job number from 1 to " + recentRecommendations.size() + " from your latest recommendations (e.g., *\"Apply to job 1\"*).", Intent.APPLY_JOB);
        }

        JobMatchResult selected = recentRecommendations.get(targetNumber - 1);
        String jobId = selected.getJob().getJobId();
        String title = selected.getJob().getTitle();
        String company = selected.getJob().getCompany();

        // Check if already applied in this session
        List<Application> existing = applicationService.getApplicationsForSession(context.getSessionId());
        boolean alreadyApplied = existing.stream().anyMatch(a -> a.getJobId().equalsIgnoreCase(jobId));
        if (alreadyApplied) {
            return ToolResult.ok("You have already recorded an application for **" + title + "** at **" + company + "**! You can ask *\"Show my applied jobs\"* to see your full history.", Intent.APPLY_JOB);
        }

        // Set pending confirmation in session
        SessionConversationContext.PendingConfirmation pending = new SessionConversationContext.PendingConfirmation(
                "APPLY_JOB", jobId, title, company, targetNumber
        );
        sessionContext.setPendingConfirmation(pending);

        String prompt = "You selected **" + title + "** at **" + company + "**.\n\n"
                + "Would you like me to record this application? (Note: Applications are tracked locally in your HireMate dashboard; external submission requires visiting the employer's official job portal). Reply **\"Yes, apply\"** to confirm or **\"Cancel\"** to decline.";

        return ToolResult.ok(prompt, Intent.APPLY_JOB)
                .setConfirmationRequired(true)
                .setPendingConfirmation(pending);
    }
}

