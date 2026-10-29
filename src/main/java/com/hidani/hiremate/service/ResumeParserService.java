package com.hidani.hiremate.service;

import com.hidani.hiremate.model.ResumeProfile;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class ResumeParserService {

    private static final Logger log = LoggerFactory.getLogger(ResumeParserService.class);
    private static final long MAX_FILE_SIZE = 5 * 1024 * 1024; // 5 MB

    // Comprehensive catalog of recognized technical and software skills
    private static final List<String> KNOWN_SKILLS = List.of(
            "Java", "Python", "JavaScript", "TypeScript", "C++", "C#", "Go", "Rust", "Ruby", "PHP", "Kotlin", "Swift",
            "Spring", "Spring Boot", "Hibernate", "React", "Angular", "Vue", "Node.js", "Express", "Next.js",
            "HTML", "CSS", "SQL", "MySQL", "PostgreSQL", "MongoDB", "Redis", "Elasticsearch", "Oracle",
            "Docker", "Kubernetes", "AWS", "Azure", "GCP", "Google Cloud", "Git", "GitHub", "GitLab",
            "CI/CD", "Jenkins", "REST", "REST API", "GraphQL", "Microservices", "Kafka", "RabbitMQ",
            "Linux", "Unix", "Bash", "Agile", "Scrum", "Jira", "Maven", "Gradle", "JUnit", "Mockito",
            "Tailwind", "Bootstrap", "FastAPI", "Django", "Flask", "Pandas", "NumPy", "TensorFlow", "PyTorch"
    );

    private static final List<String> COMMON_ROLES = List.of(
            "Software Development Engineer", "Software Engineer", "SDE", "SDE Intern", "Software Engineering Intern",
            "Full Stack Developer", "Backend Developer", "Frontend Developer", "Java Developer", "Python Developer",
            "Web Developer", "DevOps Engineer", "Cloud Engineer", "Data Engineer", "Data Scientist", "Machine Learning Engineer",
            "QA Engineer", "Mobile App Developer", "Android Developer", "iOS Developer", "System Engineer"
    );

    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "\\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}\\b"
    );

    private static final Pattern PHONE_PATTERN = Pattern.compile(
            "(?:\\+?\\d{1,3}[-.\\s]?)?\\(?\\d{3}\\)?[-.\\s]?\\d{3}[-.\\s]?\\d{4}"
    );

    /**
     * Validate and parse a resume file (PDF or DOCX).
     */
    public ResumeProfile parseResume(MultipartFile file) {
        validateFile(file);

        String originalFilename = file.getOriginalFilename();
        String extension = getFileExtension(originalFilename).toLowerCase(Locale.ROOT);

        String extractedText;
        try {
            if ("pdf".equals(extension)) {
                extractedText = extractTextFromPdf(file.getBytes());
            } else if ("docx".equals(extension)) {
                extractedText = extractTextFromDocx(file.getInputStream());
            } else {
                throw new IllegalArgumentException("Unsupported file type: only .pdf and .docx are supported.");
            }
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to extract text from file: {}", originalFilename, e);
            throw new IllegalArgumentException("Could not read document contents. The file may be corrupted, encrypted, or scanned.");
        }

        if (extractedText == null || extractedText.trim().isEmpty()) {
            throw new IllegalArgumentException("The uploaded document contains no readable text. Scanned images or empty documents are not supported.");
        }

        return parseResumeText(originalFilename, extractedText);
    }

    public void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty() || file.getSize() == 0) {
            throw new IllegalArgumentException("Uploaded file cannot be empty.");
        }

        if (file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException("File size exceeds 5MB limit. Please upload a smaller resume.");
        }

        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || originalFilename.isBlank()) {
            throw new IllegalArgumentException("File name cannot be empty.");
        }

        String extension = getFileExtension(originalFilename).toLowerCase(Locale.ROOT);
        if (!extension.equals("pdf") && !extension.equals("docx")) {
            throw new IllegalArgumentException("Invalid file format: '" + extension + "'. Only PDF and DOCX files are allowed.");
        }
    }

    private String extractTextFromPdf(byte[] bytes) throws Exception {
        try (PDDocument document = Loader.loadPDF(bytes)) {
            if (document.isEncrypted()) {
                throw new IllegalArgumentException("The PDF file is password protected or encrypted.");
            }
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            return stripper.getText(document);
        }
    }

    private String extractTextFromDocx(InputStream inputStream) throws Exception {
        try (XWPFDocument document = new XWPFDocument(inputStream);
             XWPFWordExtractor extractor = new XWPFWordExtractor(document)) {
            return extractor.getText();
        }
    }

    /**
     * Parses raw extracted resume text into structured fields.
     */
    public ResumeProfile parseResumeText(String fileName, String rawText) {
        ResumeProfile profile = new ResumeProfile();
        profile.setFileName(fileName);

        String[] lines = rawText.split("\\r?\\n");
        List<String> cleanedLines = new ArrayList<>();
        for (String line : lines) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty()) {
                cleanedLines.add(trimmed);
            }
        }

        profile.setEmail(extractEmail(rawText));
        profile.setPhone(extractPhone(rawText));
        profile.setName(extractCandidateName(cleanedLines));
        profile.setTargetDesignation(extractTargetDesignation(rawText, cleanedLines));
        profile.setSkills(extractSkills(rawText));

        Map<String, List<String>> sections = extractSections(cleanedLines);
        profile.setExperience(sections.getOrDefault("EXPERIENCE", Collections.emptyList()));
        profile.setEducation(sections.getOrDefault("EDUCATION", Collections.emptyList()));
        profile.setProjects(sections.getOrDefault("PROJECTS", Collections.emptyList()));

        return profile;
    }

    private String extractEmail(String text) {
        Matcher matcher = EMAIL_PATTERN.matcher(text);
        if (matcher.find()) {
            return matcher.group(0).trim();
        }
        return null;
    }

    private String extractPhone(String text) {
        Matcher matcher = PHONE_PATTERN.matcher(text);
        if (matcher.find()) {
            return matcher.group(0).trim();
        }
        return null;
    }

    private String extractCandidateName(List<String> lines) {
        // Look at top 5 lines for candidate's name
        for (int i = 0; i < Math.min(5, lines.size()); i++) {
            String line = lines.get(i).trim();
            if (line.equalsIgnoreCase("resume") || line.equalsIgnoreCase("curriculum vitae")
                    || line.toLowerCase().startsWith("cv") || line.contains("@")
                    || line.matches(".*\\d{5,}.*") || line.startsWith("http")) {
                continue;
            }
            // Candidate names are typically 2-4 words, letters only with spaces or periods
            if (line.matches("^[A-Z][a-zA-Z.'\\- ]{1,40}$") && line.split("\\s+").length >= 2 && line.split("\\s+").length <= 5) {
                return line;
            }
        }
        // Fallback: check first non-empty line
        if (!lines.isEmpty()) {
            String first = lines.get(0).trim();
            if (!first.contains("@") && !first.matches(".*\\d{5,}.*") && first.length() < 40) {
                return first;
            }
        }
        return null;
    }

    private String extractTargetDesignation(String rawText, List<String> lines) {
        String lowerText = rawText.toLowerCase();
        for (String role : COMMON_ROLES) {
            if (lowerText.contains(role.toLowerCase())) {
                return role;
            }
        }
        // Look near the top for a subtitle line under name
        for (int i = 1; i < Math.min(6, lines.size()); i++) {
            String line = lines.get(i).trim();
            if (line.toLowerCase().contains("engineer") || line.toLowerCase().contains("developer")
                    || line.toLowerCase().contains("intern") || line.toLowerCase().contains("analyst")) {
                if (line.length() < 50 && !line.contains("@")) {
                    return line;
                }
            }
        }
        return null;
    }

    private List<String> extractSkills(String rawText) {
        Set<String> detected = new LinkedHashSet<>();

        // 1. Check known skills in full text using word boundaries
        for (String skill : KNOWN_SKILLS) {
            String escaped = Pattern.quote(skill);
            // Handle special chars like C++, C#, .NET
            String regex = "(?i)(?<=^|[^a-zA-Z0-9+#.])" + escaped + "(?=[^a-zA-Z0-9+#.]|$)";
            if (Pattern.compile(regex).matcher(rawText).find()) {
                detected.add(skill);
            }
        }

        // 2. Also check if there is an explicit Skills section
        String[] lines = rawText.split("\\r?\\n");
        boolean inSkillsSection = false;
        for (String line : lines) {
            String trimmed = line.trim();
            if (isSectionHeader(trimmed, "skills", "technical skills", "skills & tools", "technologies")) {
                inSkillsSection = true;
                continue;
            } else if (inSkillsSection && isAnyOtherSectionHeader(trimmed)) {
                break;
            }

            if (inSkillsSection && !trimmed.isEmpty()) {
                String[] tokens = trimmed.split("[,;•|/·]");
                for (String token : tokens) {
                    String clean = token.replaceAll("^[-*•>\\s]+", "").trim();
                    if (!clean.isEmpty() && clean.length() < 30 && !clean.contains(":")) {
                        // Normalize capitalization if already in known skills
                        String matchedKnown = findKnownSkillMatch(clean);
                        detected.add(matchedKnown != null ? matchedKnown : clean);
                    }
                }
            }
        }

        return new ArrayList<>(detected);
    }

    private String findKnownSkillMatch(String token) {
        for (String skill : KNOWN_SKILLS) {
            if (skill.equalsIgnoreCase(token.trim())) {
                return skill;
            }
        }
        return null;
    }

    private Map<String, List<String>> extractSections(List<String> lines) {
        Map<String, List<String>> result = new HashMap<>();
        String currentSection = null;

        for (String line : lines) {
            String lower = line.toLowerCase().trim();
            if (isSectionHeader(lower, "experience", "work experience", "internships", "professional experience", "employment")) {
                currentSection = "EXPERIENCE";
                continue;
            } else if (isSectionHeader(lower, "education", "academic background", "academics", "qualifications")) {
                currentSection = "EDUCATION";
                continue;
            } else if (isSectionHeader(lower, "projects", "academic projects", "personal projects", "key projects")) {
                currentSection = "PROJECTS";
                continue;
            } else if (isAnyOtherSectionHeader(lower)) {
                currentSection = null;
                continue;
            }

            if (currentSection != null && !line.trim().isEmpty()) {
                result.computeIfAbsent(currentSection, k -> new ArrayList<>()).add(line.trim());
            }
        }

        return result;
    }

    private boolean isSectionHeader(String line, String... variants) {
        String clean = line.replaceAll("[:\\-_#*]", "").trim();
        for (String v : variants) {
            if (clean.equalsIgnoreCase(v)) {
                return true;
            }
        }
        return false;
    }

    private boolean isAnyOtherSectionHeader(String line) {
        String clean = line.replaceAll("[:\\-_#*]", "").trim().toLowerCase();
        return clean.equals("skills") || clean.equals("technical skills") || clean.equals("technologies")
                || clean.equals("certifications") || clean.equals("achievements") || clean.equals("summary")
                || clean.equals("objective") || clean.equals("hobbies") || clean.equals("languages")
                || clean.equals("interests") || clean.equals("references") || clean.equals("publications");
    }

    private String getFileExtension(String filename) {
        if (filename == null || !filename.contains(".")) {
            return "";
        }
        return filename.substring(filename.lastIndexOf('.') + 1);
    }
}

