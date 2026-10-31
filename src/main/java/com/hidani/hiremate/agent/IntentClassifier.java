package com.hidani.hiremate.agent;

import com.hidani.hiremate.model.JobMatchFilter;
import com.hidani.hiremate.model.SessionConversationContext;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class IntentClassifier {

    private static final List<String> RECOGNIZED_SKILLS = List.of(
            "Java", "Python", "JavaScript", "TypeScript", "React", "Angular", "Vue", "Spring Boot", "Spring",
            "Node.js", "Node", "SQL", "PostgreSQL", "MySQL", "MongoDB", "Docker", "Kubernetes", "AWS", "Azure",
            "C++", "C#", "Go", "Git", "REST API", "HTML", "CSS"
    );

    public Intent classifyIntent(String message, SessionConversationContext context) {
        if (message == null || message.isBlank()) {
            return Intent.GREETING_OR_HELP;
        }

        String raw = message.trim();
        String lower = raw.toLowerCase(Locale.ROOT);
        String cleanText = lower.replaceAll("[^a-z0-9\\s]", " ").replaceAll("\\s+", " ").trim();

        // If there is a pending confirmation, check for confirmation or cancellation first
        if (context != null && context.getPendingConfirmation() != null) {
            if (isConfirmation(cleanText)) {
                return Intent.CONFIRM_APPLICATION;
            }
            if (isCancellation(cleanText)) {
                return Intent.CANCEL_APPLICATION;
            }
        }

        // Greetings and help (support punctuation and variations like "Hi, what can you do?")
        if (cleanText.equals("hi") || cleanText.equals("hello") || cleanText.equals("hey")
                || cleanText.startsWith("hi ") || cleanText.startsWith("hello ") || cleanText.startsWith("hey ")
                || cleanText.contains("what can you do") || cleanText.contains("who are you")
                || cleanText.contains("help") || cleanText.contains("guide")
                || cleanText.contains("how do i") || cleanText.contains("capabilities")) {
            return Intent.GREETING_OR_HELP;
        }

        // Application History & Status
        if (lower.contains("applied jobs") || lower.contains("my applications")
                || lower.contains("application history") || lower.contains("which jobs have i applied")
                || lower.contains("show applied") || lower.contains("jobs i applied")) {
            return Intent.VIEW_APPLICATIONS;
        }
        if (lower.contains("have i applied to") || lower.contains("status of job")
                || (lower.contains("status") && lower.contains("application"))) {
            return Intent.CHECK_APPLICATION_STATUS;
        }

        // Job Application request: "apply to job 3", "apply job 1", "apply 2"
        if (lower.contains("apply") || lower.contains("submit application")) {
            // Check if there is a job number mentioned
            if (extractJobNumber(raw) != null) {
                return Intent.APPLY_JOB;
            }
            if (lower.equals("apply") || lower.startsWith("apply ")) {
                return Intent.APPLY_JOB;
            }
        }

        // Resume status or info
        if (lower.equals("resume") || lower.equals("my resume") || lower.contains("view resume")
                || lower.contains("show resume") || lower.contains("resume details") || lower.contains("parsed profile")) {
            return Intent.RESUME_STATUS;
        }

        // Conversational Filter or Job Match
        if (lower.contains("job") || lower.contains("internship") || lower.contains("remote")
                || lower.contains("match") || lower.contains("roles") || lower.contains("vacancies")
                || lower.contains("positions") || lower.contains("filter") || containsSkillMention(lower)) {
            if (isRefinement(lower, context)) {
                return Intent.REFINE_FILTER;
            }
            return Intent.MATCH_JOBS;
        }

        // Default fallback
        return Intent.UNKNOWN;
    }

    private boolean isConfirmation(String lower) {
        return lower.equals("yes") || lower.equals("yes, apply") || lower.equals("yes apply")
                || lower.equals("confirm") || lower.equals("yes please") || lower.equals("sure")
                || lower.equals("proceed") || lower.startsWith("yes");
    }

    private boolean isCancellation(String lower) {
        return lower.equals("no") || lower.equals("cancel") || lower.equals("abort")
                || lower.equals("don't apply") || lower.equals("dont apply") || lower.equals("stop")
                || lower.startsWith("no ") || lower.equals("nevermind");
    }

    private boolean isRefinement(String lower, SessionConversationContext context) {
        if (context == null || context.getLastRecommendations() == null || context.getLastRecommendations().isEmpty()) {
            return false;
        }
        return lower.startsWith("only ") || lower.startsWith("show only ") || lower.contains("filter")
                || lower.contains("what about") || lower.contains("requiring") || lower.startsWith("how about");
    }

    public Integer extractJobNumber(String message) {
        if (message == null) return null;
        Matcher m = Pattern.compile("(?i)(?:job|#|number|no\\.?)\\s*(\\d+)").matcher(message);
        if (m.find()) {
            try {
                return Integer.parseInt(m.group(1));
            } catch (NumberFormatException ignored) {}
        }
        // Check for isolated digits after "apply" e.g. "apply 2"
        Matcher m2 = Pattern.compile("(?i)apply\\s+(\\d+)").matcher(message);
        if (m2.find()) {
            try {
                return Integer.parseInt(m2.group(1));
            } catch (NumberFormatException ignored) {}
        }
        return null;
    }

    public JobMatchFilter extractFilters(String message) {
        JobMatchFilter filter = new JobMatchFilter();
        if (message == null) return filter;
        String lower = message.toLowerCase(Locale.ROOT);

        // Reset filter
        if (lower.contains("clear filter") || lower.contains("reset filter") || lower.contains("remove filter")) {
            return new JobMatchFilter();
        }

        // Remote
        if (lower.contains("remote")) {
            filter.setRemoteOnly(true);
        }

        // Employment type
        if (lower.contains("intern") || lower.contains("internship")) {
            filter.setEmploymentType("internship");
        } else if (lower.contains("full-time") || lower.contains("fulltime")) {
            filter.setEmploymentType("full-time");
        }

        // Skills
        List<String> foundSkills = new ArrayList<>();
        for (String skill : RECOGNIZED_SKILLS) {
            String sLower = skill.toLowerCase(Locale.ROOT);
            if (Pattern.compile("\\b" + Pattern.quote(sLower) + "\\b").matcher(lower).find()) {
                foundSkills.add(skill);
            }
        }
        if (!foundSkills.isEmpty()) {
            filter.setRequiredSkills(foundSkills);
        }

        // Min match percentage (e.g. "70% match", "at least 80%")
        Matcher pctMatcher = Pattern.compile("(\\d{2})\\s*%").matcher(message);
        if (pctMatcher.find()) {
            try {
                filter.setMinMatchPercentage(Integer.parseInt(pctMatcher.group(1)));
            } catch (NumberFormatException ignored) {}
        }

        // Location heuristic: "in [city]"
        Matcher locMatcher = Pattern.compile("(?i)\\bin\\s+([A-Za-z]+(?:\\s+[A-Za-z]+)?)(?:\\b|$)").matcher(message);
        if (locMatcher.find()) {
            String loc = locMatcher.group(1).trim();
            // Avoid capturing common non-location words
            if (!loc.equalsIgnoreCase("remote") && !loc.equalsIgnoreCase("germany")
                    && !loc.equalsIgnoreCase("india") && !loc.equalsIgnoreCase("usa")
                    && !loc.equalsIgnoreCase("english") && !loc.equalsIgnoreCase("detail")) {
                filter.setLocation(loc);
            } else if (loc.equalsIgnoreCase("remote")) {
                filter.setRemoteOnly(true);
            } else {
                filter.setLocation(loc);
            }
        }

        // Title keyword heuristic
        if (lower.contains("frontend") || lower.contains("front-end")) {
            filter.setTitleKeyword("Frontend");
        } else if (lower.contains("backend") || lower.contains("back-end")) {
            filter.setTitleKeyword("Backend");
        } else if (lower.contains("full stack") || lower.contains("fullstack")) {
            filter.setTitleKeyword("Full Stack");
        } else if (lower.contains("devops")) {
            filter.setTitleKeyword("DevOps");
        } else if (lower.contains("data engineer")) {
            filter.setTitleKeyword("Data Engineer");
        }

        return filter;
    }

    private boolean matchesAny(String text, String... candidates) {
        String trimmed = text.trim();
        for (String c : candidates) {
            if (trimmed.equalsIgnoreCase(c) || trimmed.startsWith(c + " ") || trimmed.endsWith(" " + c)) {
                return true;
            }
        }
        return false;
    }

    private boolean containsSkillMention(String lower) {
        for (String s : RECOGNIZED_SKILLS) {
            if (lower.contains(s.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }
}
