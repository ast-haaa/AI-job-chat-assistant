package com.hidani.hiremate.service;

import com.hidani.hiremate.model.ResumeProfile;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

public class ResumeParserServiceTest {

    private ResumeParserService resumeParserService;

    @BeforeEach
    void setUp() {
        resumeParserService = new ResumeParserService();
    }

    @Test
    void testParseResumeText_ExtractsFieldsCorrectly() {
        String sampleResumeText = """
                Alex Johnson
                alex.johnson@example.com
                +1-555-019-2834
                Target Role: Full Stack Developer
                
                Skills:
                Java, Spring Boot, React, TypeScript, Docker, SQL, Git
                
                Work Experience:
                Software Engineering Intern at Acme Corp (6 months)
                Built REST APIs and modernized microservices using Spring Boot and PostgreSQL.
                
                Education:
                B.S. in Computer Science, State University, 2024
                
                Projects:
                HireMate AI Agent: Built an automated job matching platform.
                """;

        ResumeProfile profile = resumeParserService.parseResumeText("alex_resume.pdf", sampleResumeText);

        assertNotNull(profile);
        assertEquals("Alex Johnson", profile.getName());
        assertEquals("alex.johnson@example.com", profile.getEmail());
        assertNotNull(profile.getPhone());
        assertTrue(profile.getSkills().contains("Java"));
        assertTrue(profile.getSkills().contains("Spring Boot"));
        assertTrue(profile.getSkills().contains("React"));
        assertFalse(profile.getExperience().isEmpty());
        assertFalse(profile.getEducation().isEmpty());
        assertFalse(profile.getProjects().isEmpty());
    }

    @Test
    void testParseValidDocxFile() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (XWPFDocument doc = new XWPFDocument()) {
            XWPFParagraph p = doc.createParagraph();
            p.createRun().setText("Samantha Ray\n" +
                    "samantha.ray@test.org\n" +
                    "Frontend Developer\n" +
                    "Skills: React, JavaScript, HTML, CSS, Tailwind\n" +
                    "Experience:\nFrontend Intern at TechLabs\n" +
                    "Education:\nBachelor of Technology in IT");
            doc.write(out);
        }

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "samantha_resume.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                out.toByteArray()
        );

        ResumeProfile profile = resumeParserService.parseResume(file);

        assertNotNull(profile);
        assertEquals("Samantha Ray", profile.getName());
        assertEquals("samantha.ray@test.org", profile.getEmail());
        assertTrue(profile.getSkills().contains("React"));
        assertTrue(profile.getSkills().contains("JavaScript"));
    }

    @Test
    void testParseValidPdfFile() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage();
            doc.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.beginText();
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                cs.newLineAtOffset(50, 700);
                cs.showText("Devin Vance devin.vance@testmail.com");
                cs.endText();
            }
            doc.save(out);
        }

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "devin_resume.pdf",
                "application/pdf",
                out.toByteArray()
        );

        ResumeProfile profile = resumeParserService.parseResume(file);
        assertNotNull(profile);
        assertEquals("devin.vance@testmail.com", profile.getEmail());
    }

    @Test
    void testRejectEmptyFile() {
        MockMultipartFile emptyFile = new MockMultipartFile("file", "empty.pdf", "application/pdf", new byte[0]);
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                resumeParserService.parseResume(emptyFile)
        );
        assertTrue(ex.getMessage().contains("empty"));
    }

    @Test
    void testRejectUnsupportedFormat() {
        MockMultipartFile txtFile = new MockMultipartFile("file", "resume.txt", "text/plain", "Some resume content".getBytes());
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                resumeParserService.parseResume(txtFile)
        );
        assertTrue(ex.getMessage().contains("Invalid file format") || ex.getMessage().contains("Only PDF and DOCX"));
    }

    @Test
    void testRejectCorruptedDocument() {
        MockMultipartFile corruptedPdf = new MockMultipartFile("file", "corrupt.pdf", "application/pdf", "not a real pdf content".getBytes());
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                resumeParserService.parseResume(corruptedPdf)
        );
        assertTrue(ex.getMessage().contains("Could not read document") || ex.getMessage().contains("corrupted"));
    }

    @Test
    void testMissingFields_DoNotInventInformation() {
        String bareResume = """
                Random Candidate
                Skills: Python
                """;
        ResumeProfile profile = resumeParserService.parseResumeText("bare.pdf", bareResume);

        assertNotNull(profile);
        assertNull(profile.getEmail());
        assertNull(profile.getPhone());
        assertTrue(profile.getExperience().isEmpty());
        assertTrue(profile.getEducation().isEmpty());
        assertTrue(profile.getProjects().isEmpty());
    }
}

