package com.hidani.hiremate.service;

import com.hidani.hiremate.model.Job;
import com.hidani.hiremate.model.JobMatchFilter;
import com.hidani.hiremate.model.JobMatchResult;
import com.hidani.hiremate.model.ResumeProfile;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class JobMatchingService {

    private final JobDataService jobDataService;

    // Normalization mapping for tech skills aliases
    private static final Map<String, String> SKILL_ALIASES = Map.ofEntries(
            Map.entry("reactjs", "react"),
            Map.entry("react.js", "react"),
            Map.entry("nodejs", "node.js"),
            Map.entry("node", "node.js"),
            Map.entry("springboot", "spring boot"),
            Map.entry("spring", "spring boot"),
            Map.entry("postgres", "postgresql"),
            Map.entry("mongo", "mongodb"),
            Map.entry("k8s", "kubernetes"),
            Map.entry("aws cloud", "aws"),
            Map.entry("gcp", "google cloud"),
            Map.entry("js", "javascript"),
            Map.entry("ts", "typescript"),
            Map.entry("restful", "rest api"),
            Map.entry("rest", "rest api"),
            Map.entry("golang", "go")
    );

    public JobMatchingService(JobDataService jobDataService) {
        this.jobDataService = jobDataService;
    }

    /**
     * Match jobs from JobDataService against the given ResumeProfile and filters.
     */
    public List<JobMatchResult> matchJobs(ResumeProfile profile, JobMatchFilter filter) {
        if (profile == null) {
            throw new IllegalArgumentException("No resume profile provided for matching. Please upload a resume first.");
        }

        List<Job> allJobs = jobDataService.getAllJobs();
        if (allJobs.isEmpty()) {
            return Collections.emptyList();
        }

        JobMatchFilter activeFilter = filter != null ? filter : new JobMatchFilter();
        Set<String> normalizedResumeSkills = normalizeSkillSet(profile.getSkills());
        String targetDesignation = profile.getTargetDesignation() != null ? profile.getTargetDesignation().toLowerCase() : "";
        int estimatedCandidateExpYears = estimateCandidateExperienceYears(profile);

        List<JobMatchResult> matchedResults = new ArrayList<>();

        for (Job job : allJobs) {
            // Apply hard filters first
            if (!passesFilter(job, activeFilter)) {
                continue;
            }

            JobMatchResult result = calculateMatch(job, profile, normalizedResumeSkills, targetDesignation, estimatedCandidateExpYears);

            // Filter by min match percentage if specified
            if (activeFilter.getMinMatchPercentage() != null && result.getMatchPercentage() < activeFilter.getMinMatchPercentage()) {
                continue;
            }

            matchedResults.add(result);
        }

        // Sort by match percentage descending, then by title
        matchedResults.sort((a, b) -> {
            int cmp = Integer.compare(b.getMatchPercentage(), a.getMatchPercentage());
            if (cmp != 0) return cmp;
            return a.getJob().getTitle().compareToIgnoreCase(b.getJob().getTitle());
        });

        // Limit results to requested limit (default 5, maximum 25)
        int limit = activeFilter.getLimit() > 0 ? Math.min(activeFilter.getLimit(), 25) : 5;
        List<JobMatchResult> limitedResults = matchedResults.stream().limit(limit).collect(Collectors.toList());

        // Assign 1-based displayIndex for conversational referencing (e.g. "Apply to Job 1")
        for (int i = 0; i < limitedResults.size(); i++) {
            limitedResults.get(i).setDisplayIndex(i + 1);
        }

        return limitedResults;
    }

    private boolean passesFilter(Job job, JobMatchFilter filter) {
        // Remote only filter
        if (Boolean.TRUE.equals(filter.getRemoteOnly()) && !job.isRemote()) {
            return false;
        }

        // Employment type filter (e.g., "internship", "full-time")
        if (filter.getEmploymentType() != null && !filter.getEmploymentType().isBlank()) {
            String filterType = filter.getEmploymentType().toLowerCase().trim();
            String jobType = (job.getEmploymentType() != null ? job.getEmploymentType() : "").toLowerCase();
            String jobTitle = job.getTitle().toLowerCase();

            if (filterType.contains("intern")) {
                if (!jobType.contains("intern") && !jobTitle.contains("intern")) {
                    return false;
                }
            } else if (filterType.contains("full") || filterType.contains("full-time")) {
                if (!jobType.contains("full") && !jobType.isBlank() && jobType.contains("intern")) {
                    return false;
                }
            }
        }

        // Location filter
        if (filter.getLocation() != null && !filter.getLocation().isBlank()) {
            String queryLoc = filter.getLocation().toLowerCase().trim();
            String jobLoc = (job.getLocation() != null ? job.getLocation() : "").toLowerCase();
            if (!jobLoc.contains(queryLoc)) {
                return false;
            }
        }

        // Title keyword filter
        if (filter.getTitleKeyword() != null && !filter.getTitleKeyword().isBlank()) {
            String keyword = filter.getTitleKeyword().toLowerCase().trim();
            String jobTitle = job.getTitle().toLowerCase();
            if (!jobTitle.contains(keyword)) {
                return false;
            }
        }

        // Required skills filter (all specified skills must be present in job skills or description)
        if (filter.getRequiredSkills() != null && !filter.getRequiredSkills().isEmpty()) {
            Set<String> normalizedJobSkills = normalizeSkillSet(job.getSkills());
            String jobDescLower = (job.getDescription() != null ? job.getDescription() : "").toLowerCase();

            for (String req : filter.getRequiredSkills()) {
                String normReq = normalizeSkill(req);
                boolean inSkills = normalizedJobSkills.contains(normReq);
                boolean inDesc = jobDescLower.contains(normReq.toLowerCase());
                if (!inSkills && !inDesc) {
                    return false;
                }
            }
        }

        return true;
    }

    private JobMatchResult calculateMatch(Job job, ResumeProfile profile, Set<String> candidateSkills,
                                          String targetRole, int candidateExpYears) {
        Set<String> jobSkills = normalizeSkillSet(job.getSkills());

        // Extract skills from description if job skills list is empty
        if (jobSkills.isEmpty() && job.getDescription() != null) {
            jobSkills = extractSkillsFromDescription(job.getDescription());
        }

        // 1. Skill overlap calculation (Weight: 50%)
        List<String> matchingSkills = new ArrayList<>();
        List<String> missingSkills = new ArrayList<>();

        for (String js : jobSkills) {
            if (candidateSkills.contains(js)) {
                matchingSkills.add(js);
            } else {
                missingSkills.add(js);
            }
        }

        double skillScore = 0.0;
        if (!jobSkills.isEmpty()) {
            skillScore = (double) matchingSkills.size() / jobSkills.size();
        } else if (!candidateSkills.isEmpty()) {
            // If job doesn't specify required skills, give moderate neutral skill score
            skillScore = 0.5;
        }

        // 2. Title / Role similarity calculation (Weight: 30%)
        double titleScore = calculateTitleSimilarity(targetRole, job.getTitle());

        // 3. Experience alignment (Weight: 10%)
        int requiredExpYears = parseExperienceYears(job.getExperienceRequired(), job.getTitle());
        double expScore = 1.0;
        String eligibilityMismatch = null;

        if (requiredExpYears > 0) {
            if (candidateExpYears >= requiredExpYears) {
                expScore = 1.0;
            } else {
                int diff = requiredExpYears - candidateExpYears;
                expScore = Math.max(0.2, 1.0 - (diff * 0.3));
                eligibilityMismatch = "Requires ~" + requiredExpYears + " yrs experience; resume indicates "
                        + (candidateExpYears == 0 ? "entry-level/intern" : candidateExpYears + " yrs") + ".";
            }
        }

        // 4. Remote / Format alignment (Weight: 10%)
        double remoteScore = job.isRemote() ? 1.0 : 0.7;

        // Total weighted score (0-100)
        double totalWeighted = (skillScore * 50.0) + (titleScore * 30.0) + (expScore * 10.0) + (remoteScore * 10.0);
        int finalPercentage = (int) Math.round(Math.min(100.0, Math.max(10.0, totalWeighted)));

        // Generate concise, honest explanation
        StringBuilder explanation = new StringBuilder();
        if (!matchingSkills.isEmpty()) {
            explanation.append("Matches ").append(matchingSkills.size()).append(" skills: ")
                    .append(String.join(", ", matchingSkills.stream().limit(4).toList()));
            if (matchingSkills.size() > 4) {
                explanation.append(" (+").append(matchingSkills.size() - 4).append(" more)");
            }
            explanation.append(". ");
        } else {
            explanation.append("General alignment based on role and background. ");
        }

        if (titleScore > 0.6) {
            explanation.append("Strong title match for '").append(job.getTitle()).append("'. ");
        }
        if (job.isRemote()) {
            explanation.append("Remote eligible.");
        }

        return new JobMatchResult(0, job, finalPercentage, matchingSkills, missingSkills,
                explanation.toString().trim(), eligibilityMismatch);
    }

    private double calculateTitleSimilarity(String targetRole, String jobTitle) {
        if (targetRole == null || targetRole.isBlank() || jobTitle == null || jobTitle.isBlank()) {
            return 0.3; // Default baseline
        }
        String t = targetRole.toLowerCase();
        String j = jobTitle.toLowerCase();

        if (j.contains(t) || t.contains(j)) {
            return 1.0;
        }

        Set<String> targetTokens = new HashSet<>(Arrays.asList(t.split("[\\s/\\-,]+")));
        Set<String> jobTokens = new HashSet<>(Arrays.asList(j.split("[\\s/\\-,]+")));

        targetTokens.removeIf(String::isBlank);
        jobTokens.removeIf(String::isBlank);

        int overlap = 0;
        for (String token : targetTokens) {
            if (jobTokens.contains(token)) {
                overlap++;
            }
        }

        if (targetTokens.isEmpty()) return 0.3;
        return (double) overlap / targetTokens.size();
    }

    private int parseExperienceYears(String expRequired, String title) {
        if (title != null && (title.toLowerCase().contains("intern") || title.toLowerCase().contains("graduate"))) {
            return 0;
        }
        if (expRequired == null || expRequired.isBlank()) {
            return 0;
        }
        String lower = expRequired.toLowerCase();
        if (lower.contains("intern") || lower.contains("entry") || lower.contains("fresher") || lower.contains("0 years")) {
            return 0;
        }
        Matcher matcher = Pattern.compile("(\\d+)\\s*(?:\\+|-\\s*\\d+)?\\s*(?:years?|yrs?)").matcher(lower);
        if (matcher.find()) {
            try {
                return Integer.parseInt(matcher.group(1));
            } catch (NumberFormatException ignored) {
            }
        }
        return 0;
    }

    private int estimateCandidateExperienceYears(ResumeProfile profile) {
        if (profile == null || profile.getExperience() == null || profile.getExperience().isEmpty()) {
            return 0;
        }
        // Count distinct work experience entries or mention of years
        int years = 0;
        for (String entry : profile.getExperience()) {
            Matcher matcher = Pattern.compile("(\\d+)\\+?\\s*(?:years?|yrs?)", Pattern.CASE_INSENSITIVE).matcher(entry);
            if (matcher.find()) {
                try {
                    int found = Integer.parseInt(matcher.group(1));
                    years = Math.max(years, found);
                } catch (Exception ignored) {}
            }
        }
        if (years == 0) {
            // Count entries as ~1 year each if professional experience
            years = Math.min(profile.getExperience().size(), 3);
        }
        return years;
    }

    private Set<String> normalizeSkillSet(List<String> skills) {
        if (skills == null) return Collections.emptySet();
        Set<String> set = new LinkedHashSet<>();
        for (String s : skills) {
            set.add(normalizeSkill(s));
        }
        return set;
    }

    private String normalizeSkill(String skill) {
        if (skill == null) return "";
        String trimmed = skill.trim().toLowerCase();
        return SKILL_ALIASES.getOrDefault(trimmed, trimmed);
    }

    private Set<String> extractSkillsFromDescription(String desc) {
        Set<String> found = new HashSet<>();
        for (String alias : SKILL_ALIASES.keySet()) {
            if (desc.toLowerCase().contains(alias)) {
                found.add(SKILL_ALIASES.get(alias));
            }
        }
        return found;
    }
}

