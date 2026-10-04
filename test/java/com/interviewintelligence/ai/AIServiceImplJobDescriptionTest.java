package com.careerintelligence.ai;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link AIServiceImpl#analyzeJobDescription} local heuristic
 * path (no database, no API key required): required/preferred skill
 * splitting, technology extraction, responsibilities, experience, and soft
 * skills. Run with: mvn test
 */
class AIServiceImplJobDescriptionTest {

    private final AIServiceImpl aiService = new AIServiceImpl();

    private static final String SAMPLE_JD =
            "Backend Developer\n"
                    + "Required:\n"
                    + "- 3+ years of experience with Java and Spring Boot\n"
                    + "- Strong knowledge of MySQL and REST API design\n"
                    + "- Experience with Docker\n"
                    + "Preferred:\n"
                    + "- Familiarity with Kubernetes and AWS\n"
                    + "- Experience with React is a plus\n"
                    + "Responsibilities:\n"
                    + "- Design and build scalable backend services\n"
                    + "- Collaborate with frontend engineers on API contracts\n"
                    + "- Mentor junior engineers\n"
                    + "We're looking for someone with excellent communication and strong teamwork skills who "
                    + "thrives in a collaborative environment.";

    @Test
    void splitsRequiredAndPreferredSkillsIntoSeparateSections() {
        AIService.JobDescriptionExtraction result = aiService.analyzeJobDescription(SAMPLE_JD);

        assertTrue(result.requiredSkills().contains("Java"));
        assertTrue(result.requiredSkills().contains("Spring Boot"));
        assertTrue(result.requiredSkills().contains("MySQL"));
        assertTrue(result.requiredSkills().contains("Docker"));

        assertTrue(result.preferredSkills().contains("Kubernetes"));
        assertTrue(result.preferredSkills().contains("AWS"));
        assertTrue(result.preferredSkills().contains("React"));

        // A skill mentioned only in the required section must not also show up as preferred.
        assertFalse(result.preferredSkills().contains("Java"));
        assertFalse(result.preferredSkills().contains("Docker"));
    }

    @Test
    void extractsTechnologiesAcrossBothSections() {
        AIService.JobDescriptionExtraction result = aiService.analyzeJobDescription(SAMPLE_JD);
        assertTrue(result.technologies().contains("Java"));
        assertTrue(result.technologies().contains("Docker"));
        assertTrue(result.technologies().contains("Kubernetes"));
        assertTrue(result.technologies().contains("MySQL"));
    }

    @Test
    void extractsResponsibilityBulletLines() {
        AIService.JobDescriptionExtraction result = aiService.analyzeJobDescription(SAMPLE_JD);
        assertTrue(result.responsibilities().stream().anyMatch(r -> r.toLowerCase().contains("design and build")));
        assertTrue(result.responsibilities().stream().anyMatch(r -> r.toLowerCase().contains("mentor")));
    }

    @Test
    void extractsExperienceRequiredPattern() {
        AIService.JobDescriptionExtraction result = aiService.analyzeJobDescription(SAMPLE_JD);
        assertNotNull(result.experienceRequired());
        assertTrue(result.experienceRequired().toLowerCase().contains("year"));
        assertTrue(result.experienceRequired().contains("3"));
    }

    @Test
    void extractsSoftSkillKeywords() {
        AIService.JobDescriptionExtraction result = aiService.analyzeJobDescription(SAMPLE_JD);
        assertTrue(result.softSkills().stream().anyMatch(s -> s.equalsIgnoreCase("Communication")));
        assertTrue(result.softSkills().stream().anyMatch(s -> s.equalsIgnoreCase("Teamwork")));
    }

    @Test
    void useFirstLineAsTitleWhenShortEnough() {
        AIService.JobDescriptionExtraction result = aiService.analyzeJobDescription(SAMPLE_JD);
        assertEquals("Backend Developer", result.title());
    }

    @Test
    void blankJobDescriptionReturnsEmptyExtraction() {
        AIService.JobDescriptionExtraction result = aiService.analyzeJobDescription("");
        assertTrue(result.requiredSkills().isEmpty());
        assertTrue(result.preferredSkills().isEmpty());
        assertTrue(result.technologies().isEmpty());
        assertTrue(result.responsibilities().isEmpty());
        assertTrue(result.softSkills().isEmpty());
        assertNull(result.experienceRequired());

        AIService.JobDescriptionExtraction nullResult = aiService.analyzeJobDescription(null);
        assertTrue(nullResult.requiredSkills().isEmpty());
    }

    @Test
    void jdWithNoSectionHeadersStillExtractsRequiredSkillsByDefault() {
        String freeform = "We need a Python developer who knows Django and PostgreSQL. "
                + "You will build APIs and write tests.";
        AIService.JobDescriptionExtraction result = aiService.analyzeJobDescription(freeform);
        assertTrue(result.requiredSkills().contains("Python"));
        assertTrue(result.requiredSkills().contains("Django"));
        assertTrue(result.preferredSkills().isEmpty());
    }
}
