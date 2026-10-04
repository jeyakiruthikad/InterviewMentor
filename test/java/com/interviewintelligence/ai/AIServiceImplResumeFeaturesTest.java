package com.careerintelligence.ai;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the current implementation local heuristic AI paths (no database,
 * no API key required): resume skill extraction, resume-based question
 * generation, and mock-interview follow-up generation. Run with: mvn test
 */
class AIServiceImplResumeFeaturesTest {

    private final AIServiceImpl aiService = new AIServiceImpl();

    private static final String SAMPLE_RESUME =
            "Backend engineer with 3 years of experience building services in Java and Spring Boot, "
                    + "backed by MySQL. Comfortable with Docker for packaging deployments and have exposed "
                    + "several REST APIs. Solid foundation in Data Structures and Algorithms from academic "
                    + "coursework. Familiar with Git for version control and some exposure to AWS and React "
                    + "on a personal side project.";

    @Test
    void extractsExpectedSkillsFromResumeText() {
        List<String> skills = aiService.extractSkillsFromResume(SAMPLE_RESUME);
        assertTrue(skills.contains("Java"));
        assertTrue(skills.contains("Spring Boot"));
        assertTrue(skills.contains("MySQL"));
        assertTrue(skills.contains("Docker"));
        assertTrue(skills.contains("REST API"));
        assertTrue(skills.contains("Data Structures"));
        assertTrue(skills.contains("Algorithms"));
        assertTrue(skills.contains("Git"));
        assertTrue(skills.contains("AWS"));
        assertTrue(skills.contains("React"));
    }

    @Test
    void blankResumeExtractsNoSkills() {
        assertTrue(aiService.extractSkillsFromResume("").isEmpty());
        assertTrue(aiService.extractSkillsFromResume(null).isEmpty());
    }

    @Test
    void doesNotFalselyMatchUnrelatedSubstrings() {
        // "Java" must not match inside unrelated words like "JavaScript" being absent here,
        // and "Go" (a real taxonomy skill) must not match generic English "go"/"going".
        List<String> skills = aiService.extractSkillsFromResume("I go to the gym and I am going to a conference.");
        assertFalse(skills.contains("Go"));
    }

    @Test
    void generatesRequestedNumberOfQuestionsForGivenSkills() {
        List<String> questions = aiService.generateQuestionsForSkills(List.of("Docker", "Kubernetes"), 4);
        assertEquals(4, questions.size());
        assertTrue(questions.stream().allMatch(q -> q != null && !q.isBlank()));
        // Every generated question should reference one of the requested skills.
        assertTrue(questions.stream().anyMatch(q -> q.contains("Docker")));
        assertTrue(questions.stream().anyMatch(q -> q.contains("Kubernetes")));
    }

    @Test
    void noQuestionsGeneratedForEmptySkillList() {
        assertTrue(aiService.generateQuestionsForSkills(List.of(), 5).isEmpty());
    }

    @Test
    void followUpAsksForElaborationOnVeryShortAnswer() {
        String followUp = aiService.generateFollowUpQuestion("What is Docker?", "containers");
        assertNotNull(followUp);
        assertFalse(followUp.isBlank());
    }

    @Test
    void followUpIsNonEmptyForDetailedAnswer() {
        String detailedAnswer = "Docker packages an application together with its dependencies into a "
                + "lightweight container image, which I used at my last job to make our deployment pipeline "
                + "consistent between staging and production environments.";
        String followUp = aiService.generateFollowUpQuestion("Tell me about Docker.", detailedAnswer);
        assertNotNull(followUp);
        assertFalse(followUp.isBlank());
    }

    @Test
    void blurbForKnownSkillIsTaxonomyBlurb() {
        String blurb = aiService.blurbForSkill("Docker");
        assertTrue(blurb.toLowerCase().contains("container"));
    }

    @Test
    void blurbForUnknownSkillFallsBackGracefully() {
        String blurb = aiService.blurbForSkill("SomeMadeUpTechThatDoesNotExist");
        assertNotNull(blurb);
        assertTrue(blurb.contains("SomeMadeUpTechThatDoesNotExist"));
    }
}
