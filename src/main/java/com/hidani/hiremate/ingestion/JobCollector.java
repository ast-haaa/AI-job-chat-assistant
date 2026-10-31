package com.hidani.hiremate.ingestion;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.hidani.hiremate.model.Job;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Standalone Job Ingestion Utility.
 * Collects real job listings from permitted public job APIs (Arbeitnow, Remotive).
 * Features:
 * - Resumable: Loads existing data/jobs.json and prevents duplicates.
 * - Multi-source & Paginated.
 * - Strips HTML, normalizes skills, and extracts experience requirements.
 * - Never fabricates fake sample data.
 */
public class JobCollector {

    private static final String DEFAULT_OUTPUT_PATH = "data/jobs.json";
    private static final int DEFAULT_TARGET_COUNT = 2000;
    private static final ObjectMapper objectMapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    private static final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    public static void main(String[] args) {
        String outputPath = args.length > 0 ? args[0] : DEFAULT_OUTPUT_PATH;
        int targetCount = args.length > 1 ? Integer.parseInt(args[1]) : DEFAULT_TARGET_COUNT;

        System.out.println("==================================================");
        System.out.println("  HireMate AI - Real Job Ingestion Utility");
        System.out.println("  Target count: " + targetCount + " jobs");
        System.out.println("  Output file : " + outputPath);
        System.out.println("==================================================");

        JobCollector collector = new JobCollector();
        int totalSaved = collector.collectJobs(outputPath, targetCount);
        System.out.println("Completed! Total unique real jobs stored in " + outputPath + ": " + totalSaved);
    }

    public int collectJobs(String filePath, int targetCount) {
        Map<String, Job> jobMap = new LinkedHashMap<>();
        Set<String> seenUrls = new HashSet<>();

        // Load existing jobs if file exists (resumable)
        Path path = Paths.get(filePath);
        if (Files.exists(path)) {
            try {
                byte[] bytes = Files.readAllBytes(path);
                if (bytes.length > 0) {
                    List<Job> existing = objectMapper.readValue(bytes, new TypeReference<List<Job>>() {});
                    if (existing != null) {
                        for (Job j : existing) {
                            if (j.getJobId() != null && !j.getJobId().isBlank()) {
                                jobMap.put(j.getJobId(), j);
                                if (j.getSourceLink() != null) {
                                    seenUrls.add(j.getSourceLink().toLowerCase().trim());
                                }
                            }
                        }
                    }
                    System.out.println("Loaded " + jobMap.size() + " existing jobs from " + filePath);
                }
            } catch (Exception e) {
                System.err.println("Warning: Could not read existing jobs file: " + e.getMessage());
            }
        }

        if (jobMap.size() >= targetCount) {
            System.out.println("Target of " + targetCount + " jobs already satisfied. (Current: " + jobMap.size() + ")");
            return jobMap.size();
        }

        // Ingest from Source: Arbeitnow API (Paginated)
        System.out.println("\n[Source 1] Ingesting from Arbeitnow Public Job API (https://www.arbeitnow.com)...");
        collectFromArbeitnow(jobMap, seenUrls, targetCount);

        // Ingest from Source: Remotive API (if needed to reach target)
        if (jobMap.size() < targetCount) {
            System.out.println("\n[Source 2] Ingesting from Remotive Public API (https://remotive.com)... (Current: " + jobMap.size() + ")");
            collectFromRemotive(jobMap, seenUrls, targetCount);
        }

        // Save to JSON file
        try {
            if (path.getParent() != null && !Files.exists(path.getParent())) {
                Files.createDirectories(path.getParent());
            }
            List<Job> finalJobs = new ArrayList<>(jobMap.values());
            objectMapper.writeValue(path.toFile(), finalJobs);
            System.out.println("\nSuccessfully saved " + finalJobs.size() + " validated real jobs to " + path.toAbsolutePath());
        } catch (IOException e) {
            System.err.println("Error saving jobs to " + filePath + ": " + e.getMessage());
        }

        return jobMap.size();
    }

    private void collectFromArbeitnow(Map<String, Job> jobMap, Set<String> seenUrls, int targetCount) {
        int page = 1;
        boolean hasMore = true;

        while (hasMore && jobMap.size() < targetCount) {
            String url = "https://www.arbeitnow.com/api/job-board-api?page=" + page;
            try {
                System.out.print("Fetching Arbeitnow page " + page + "... ");
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .header("Accept", "application/json")
                        .header("User-Agent", "HireMateAI-JobCollector/1.0 (Educational Assessment)")
                        .GET()
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() != 200) {
                    System.out.println("HTTP " + response.statusCode() + " received. Stopping Arbeitnow pagination.");
                    break;
                }

                JsonNode root = objectMapper.readTree(response.body());
                JsonNode data = root.get("data");

                if (data == null || !data.isArray() || data.isEmpty()) {
                    System.out.println("No more jobs returned.");
                    hasMore = false;
                    break;
                }

                int addedOnPage = 0;
                for (JsonNode item : data) {
                    String slug = item.path("slug").asText(null);
                    String sourceUrl = item.path("url").asText(null);

                    if (sourceUrl != null && seenUrls.contains(sourceUrl.toLowerCase().trim())) {
                        continue;
                    }

                    String jobId = "abn-" + (slug != null ? slug : UUID.randomUUID().toString().substring(0, 8));
                    if (jobMap.containsKey(jobId)) {
                        continue;
                    }

                    String title = item.path("title").asText("Untitled Role");
                    String company = item.path("company_name").asText("Confidential Company");
                    String location = item.path("location").asText("Flexible");
                    boolean remote = item.path("remote").asBoolean(false);
                    String rawDesc = item.path("description").asText("");
                    String cleanDesc = cleanHtml(rawDesc);

                    List<String> skills = new ArrayList<>();
                    JsonNode tags = item.path("tags");
                    if (tags.isArray()) {
                        for (JsonNode tag : tags) {
                            String t = tag.asText("").trim();
                            if (!t.isEmpty()) skills.add(t);
                        }
                    }

                    List<String> jobTypes = new ArrayList<>();
                    JsonNode types = item.path("job_types");
                    if (types.isArray()) {
                        for (JsonNode jt : types) {
                            String t = jt.asText("").trim();
                            if (!t.isEmpty()) jobTypes.add(t);
                        }
                    }
                    String empType = jobTypes.isEmpty() ? (title.toLowerCase().contains("intern") ? "Internship" : "Full Time") : String.join(", ", jobTypes);
                    String exp = deduceExperience(title, cleanDesc);

                    Job job = new Job(jobId, title, company, location, exp, skills, cleanDesc, sourceUrl, empType, remote);
                    jobMap.put(jobId, job);
                    if (sourceUrl != null) seenUrls.add(sourceUrl.toLowerCase().trim());
                    addedOnPage++;

                    if (jobMap.size() >= targetCount) {
                        break;
                    }
                }

                System.out.println("Parsed " + addedOnPage + " new jobs (Total accumulated: " + jobMap.size() + ")");

                // Check next link
                JsonNode nextLink = root.path("links").path("next");
                if (nextLink.isMissingNode() || nextLink.isNull() || nextLink.asText("").isBlank()) {
                    hasMore = false;
                } else {
                    page++;
                    // Respectful polite pause between pagination requests
                    Thread.sleep(150);
                }

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                System.err.println("Error fetching Arbeitnow page " + page + ": " + e.getMessage());
                break;
            }
        }
    }

    private void collectFromRemotive(Map<String, Job> jobMap, Set<String> seenUrls, int targetCount) {
        String url = "https://remotive.com/api/remote-jobs?category=software-dev";
        try {
            System.out.print("Fetching Remotive Software Dev jobs... ");
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Accept", "application/json")
                    .header("User-Agent", "HireMateAI-JobCollector/1.0 (Educational Assessment)")
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                System.out.println("HTTP " + response.statusCode() + " received from Remotive.");
                return;
            }

            JsonNode root = objectMapper.readTree(response.body());
            JsonNode jobsNode = root.get("jobs");

            if (jobsNode == null || !jobsNode.isArray()) {
                System.out.println("No jobs array returned from Remotive.");
                return;
            }

            int addedCount = 0;
            for (JsonNode item : jobsNode) {
                long remotiveId = item.path("id").asLong(0);
                String sourceUrl = item.path("url").asText(null);

                if (sourceUrl != null && seenUrls.contains(sourceUrl.toLowerCase().trim())) {
                    continue;
                }

                String jobId = "rem-" + (remotiveId > 0 ? remotiveId : UUID.randomUUID().toString().substring(0, 8));
                if (jobMap.containsKey(jobId)) {
                    continue;
                }

                String title = item.path("title").asText("Untitled Role");
                String company = item.path("company_name").asText("Tech Company");
                String location = item.path("candidate_required_location").asText("Remote");
                String cleanDesc = cleanHtml(item.path("description").asText(""));
                String empType = item.path("job_type").asText("Full Time");

                List<String> skills = new ArrayList<>();
                JsonNode tags = item.path("tags");
                if (tags.isArray()) {
                    for (JsonNode t : tags) {
                        String tagStr = t.asText("").trim();
                        if (!tagStr.isEmpty()) skills.add(tagStr);
                    }
                }

                String exp = deduceExperience(title, cleanDesc);

                Job job = new Job(jobId, title, company, location, exp, skills, cleanDesc, sourceUrl, empType, true);
                jobMap.put(jobId, job);
                if (sourceUrl != null) seenUrls.add(sourceUrl.toLowerCase().trim());
                addedCount++;

                if (jobMap.size() >= targetCount) {
                    break;
                }
            }

            System.out.println("Parsed " + addedCount + " new jobs from Remotive (Total accumulated: " + jobMap.size() + ")");

        } catch (Exception e) {
            System.err.println("Error fetching Remotive jobs: " + e.getMessage());
        }
    }

    private static String cleanHtml(String html) {
        if (html == null) return "";
        return html.replaceAll("<[^>]+>", " ")
                .replaceAll("&nbsp;", " ")
                .replaceAll("&amp;", "&")
                .replaceAll("&lt;", "<")
                .replaceAll("&gt;", ">")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private static String deduceExperience(String title, String desc) {
        String lowerTitle = title.toLowerCase();
        if (lowerTitle.contains("intern") || lowerTitle.contains("student")) {
            return "Internship / Fresher (0 years)";
        }
        if (lowerTitle.contains("junior") || lowerTitle.contains("entry") || lowerTitle.contains("associate")) {
            return "Junior / 0-2 years";
        }
        if (lowerTitle.contains("senior") || lowerTitle.contains("sr") || lowerTitle.contains("lead") || lowerTitle.contains("principal")) {
            return "Senior (5+ years)";
        }

        Matcher m = Pattern.compile("(\\d+)\\+?\\s*(?:years?|yrs?)\\s*(?:of\\s*)?(?:experience)?", Pattern.CASE_INSENSITIVE).matcher(desc);
        if (m.find()) {
            return m.group(1) + "+ years";
        }

        return "1-3 years / Relevant technical experience";
    }
}

