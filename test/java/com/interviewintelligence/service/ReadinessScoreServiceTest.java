package com.careerintelligence.service;

import com.careerintelligence.model.ReadinessScore;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the pure, database-free Next Best Action logic in
 * {@link ReadinessScoreService#determineNextBestAction}. No database or
 * MySQL connection is required - run with: mvn test
 */
class ReadinessScoreServiceTest {

    @Test
    void unresolvedMistakesTakeTopPriority() {
        String action = ReadinessScoreService.determineNextBestAction(
                3, List.of("Docker"), "role_alignment",
                new ReadinessScore.Component("Role Alignment", 40.0, 15, "note"), true, 30.0);

        assertTrue(action.contains("3 unresolved mistake"));
    }

    @Test
    void missingSkillsAreNextPriorityWhenNoMistakesOutstanding() {
        String action = ReadinessScoreService.determineNextBestAction(
                0, List.of("Docker", "Kubernetes"), "technical",
                new ReadinessScore.Component("Technical", 40.0, 30, "note"), true, 30.0);

        assertTrue(action.contains("Docker"));
        assertTrue(action.contains("Role Alignment") || action.contains("missing skill"));
    }

    @Test
    void weakestDimensionIsFlaggedWhenBelowSixty() {
        String action = ReadinessScoreService.determineNextBestAction(
                0, List.of(), "communication",
                new ReadinessScore.Component("Communication", 45.0, 20, "note"), false, 70.0);

        assertTrue(action.contains("Communication"));
        assertTrue(action.contains("45.0"));
    }

    @Test
    void noMockInterviewIsSuggestedWhenDimensionsAreOtherwiseFine() {
        String action = ReadinessScoreService.determineNextBestAction(
                0, List.of(), "behavioral",
                new ReadinessScore.Component("Behavioral", 75.0, 15, "note"), true, 78.0);

        assertTrue(action.toLowerCase().contains("mock interview"));
    }

    @Test
    void suggestsRetakingAssessmentWhenScoreBelowEightyButEverythingElseIsFine() {
        String action = ReadinessScoreService.determineNextBestAction(
                0, List.of(), "technical",
                new ReadinessScore.Component("Technical", 75.0, 30, "note"), false, 75.0);

        assertTrue(action.toLowerCase().contains("retake"));
    }

    @Test
    void suggestsMaintainingMomentumWhenFullyReady() {
        String action = ReadinessScoreService.determineNextBestAction(
                0, List.of(), "technical",
                new ReadinessScore.Component("Technical", 90.0, 30, "note"), false, 88.0);

        assertTrue(action.toLowerCase().contains("momentum"));
    }

    @Test
    void suggestsBehavioralQuestionWhenNoRealStarAnswerYetAndEverythingElseIsFine() {
        String action = ReadinessScoreService.determineNextBestAction(
                0, List.of(), "technical",
                new ReadinessScore.Component("Technical", 90.0, 30, "note"), false, true, 88.0);

        assertTrue(action.toLowerCase().contains("behavioral"));
        assertTrue(action.toLowerCase().contains("mock interview"));
    }

    @Test
    void noBehavioralAnswerNudgeYieldsToHigherPriorityIssuesFirst() {
        // Unresolved mistakes still win even when the user also has no real behavioral/STAR answer yet.
        String action = ReadinessScoreService.determineNextBestAction(
                2, List.of(), "technical",
                new ReadinessScore.Component("Technical", 90.0, 30, "note"), false, true, 88.0);

        assertTrue(action.contains("2 unresolved mistake"));
    }

    @Test
    void sixArgOverloadStillCompilesAndDefaultsToNoBehavioralNudge() {
        // Legacy 6-arg call site (noBehavioralAnswer not modelled) should behave exactly as before:
        // once every other check passes, it goes straight to the score-based nudge, never the
        // behavioral/STAR one, since it always passes noBehavioralAnswered=false under the hood.
        String action = ReadinessScoreService.determineNextBestAction(
                0, List.of(), "technical",
                new ReadinessScore.Component("Technical", 90.0, 30, "note"), false, 88.0);

        assertFalse(action.toLowerCase().contains("star"));
    }
}
