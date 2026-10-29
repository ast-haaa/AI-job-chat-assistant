package com.hidani.hiremate.agent.tool;

import com.hidani.hiremate.agent.Intent;
import com.hidani.hiremate.model.JobMatchFilter;
import com.hidani.hiremate.model.JobMatchResult;
import com.hidani.hiremate.model.ResumeProfile;
import com.hidani.hiremate.model.SessionConversationContext;
import com.hidani.hiremate.service.JobMatchingService;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class JobMatchingTool implements AgentTool {

    private final JobMatchingService jobMatchingService;

    public JobMatchingTool(JobMatchingService jobMatchingService) {
        this.jobMatchingService = jobMatchingService;
    }

    @Override
    public String getName() {
        return "JobMatchingTool";
    }

    @Override
    public String getDescription() {
        return "Matches real job listings against candidate resume and conversational filters.";
    }

    @Override
    public ToolResult execute(ToolContext context) {
        SessionConversationContext sessionContext = context.getSessionContext();
        ResumeProfile profile = sessionContext.getResumeProfile();

        if (profile == null) {
            String msg = "I'd love to find matching jobs for you, but **no resume has been uploaded** yet for this session.\n\n"
                    + "Please upload your **PDF** or **DOCX** resume using the attachment button below, and I will analyze your skills and find the best matches!";
            return ToolResult.error(msg, Intent.MATCH_JOBS);
        }

        // Merge incoming filters with session's active filters
        JobMatchFilter activeFilters = sessionContext.getActiveFilters();
        JobMatchFilter newFilters = context.getExtractedFilter();

        if (newFilters != null) {
            if (newFilters.getRemoteOnly() != null) {
                activeFilters.setRemoteOnly(newFilters.getRemoteOnly());
            }
            if (newFilters.getEmploymentType() != null) {
                activeFilters.setEmploymentType(newFilters.getEmploymentType());
            }
            if (newFilters.getTitleKeyword() != null) {
                activeFilters.setTitleKeyword(newFilters.getTitleKeyword());
            }
            if (newFilters.getLocation() != null) {
                activeFilters.setLocation(newFilters.getLocation());
            }
            if (newFilters.getMinMatchPercentage() != null) {
                activeFilters.setMinMatchPercentage(newFilters.getMinMatchPercentage());
            }
            if (newFilters.getRequiredSkills() != null && !newFilters.getRequiredSkills().isEmpty()) {
                List<String> combined = new ArrayList<>(activeFilters.getRequiredSkills());
                for (String sk : newFilters.getRequiredSkills()) {
                    if (!combined.contains(sk)) {
                        combined.add(sk);
                    }
                }
                activeFilters.setRequiredSkills(combined);
            }
        }

        List<JobMatchResult> results = jobMatchingService.matchJobs(profile, activeFilters);

        // Store in session for conversational index resolution (e.g. "Apply to job 2")
        sessionContext.setLastRecommendations(results);

        if (results.isEmpty()) {
            StringBuilder emptyMsg = new StringBuilder();
            emptyMsg.append("No jobs matched your current criteria");
            if (activeFilters.hasAnyFilter()) {
                emptyMsg.append(" with active filters (");
                List<String> activeList = new ArrayList<>();
                if (Boolean.TRUE.equals(activeFilters.getRemoteOnly())) activeList.add("Remote only");
                if (activeFilters.getEmploymentType() != null) activeList.add("Type: " + activeFilters.getEmploymentType());
                if (!activeFilters.getRequiredSkills().isEmpty()) activeList.add("Skills: " + String.join(", ", activeFilters.getRequiredSkills()));
                if (activeFilters.getTitleKeyword() != null) activeList.add("Role: " + activeFilters.getTitleKeyword());
                emptyMsg.append(String.join("; ", activeList)).append(")");
            }
            emptyMsg.append(".\n\nTry relaxing some filters, such as saying *\"Clear filters\"* or *\"Find any software engineering roles\"*.");
            return ToolResult.ok(emptyMsg.toString(), Intent.MATCH_JOBS).setJobMatches(results);
        }

        StringBuilder response = new StringBuilder();
        response.append("I found **").append(results.size()).append(" matching positions** based on your resume");
        if (activeFilters.hasAnyFilter()) {
            response.append(" and requested filters");
        }
        response.append(":\n\n");
        response.append("To apply or inspect further, you can say **\"Apply to job 1\"** or ask questions like **\"Why is job 2 a good match?\"**");

        return ToolResult.ok(response.toString(), Intent.MATCH_JOBS).setJobMatches(results);
    }
}

