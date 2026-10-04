package com.careerintelligence.service;

import com.careerintelligence.dao.ReadinessScoreDAO;
import com.careerintelligence.model.CareerRefreshTrigger;
import com.careerintelligence.model.LearningRoadmap;
import com.careerintelligence.model.RoadmapItem;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the pure, database-free logic in
 * {@link CareerIntelligenceService}: the order-preserving, duplicate-free
 * merge used by {@link CareerIntelligenceService#recommendFocusTopics} to
 * combine "weak topic" signals (from Mistakes/Practice) with "resume
 * skill match" signals (from Resume/Skills) into one recommended focus
 * list. No database or MySQL connection is required - run with: mvn test
 */
class CareerIntelligenceServiceTest {

    @Test
    void mergeUniquePreservesOrderOfFirstOccurrence() {
        List<Integer> weakTopics = List.of(3, 1);
        List<Integer> resumeMatched = List.of(1, 2, 5);

        List<Integer> merged = CareerIntelligenceService.mergeUnique(weakTopics, resumeMatched);

        assertEquals(List.of(3, 1, 2, 5), merged);
    }

    @Test
    void mergeUniqueRemovesDuplicatesAcrossBothLists() {
        List<Integer> weakTopics = List.of(4, 4, 2);
        List<Integer> resumeMatched = List.of(2, 4, 7);

        List<Integer> merged = CareerIntelligenceService.mergeUnique(weakTopics, resumeMatched);

        assertEquals(List.of(4, 2, 7), merged);
    }

    @Test
    void mergeUniqueHandlesNullLists() {
        assertTrue(CareerIntelligenceService.mergeUnique(null, null).isEmpty());
        assertEquals(List.of(9), CareerIntelligenceService.mergeUnique(null, List.of(9)));
        assertEquals(List.of(9), CareerIntelligenceService.mergeUnique(List.of(9), null));
    }

    @Test
    void mergeUniqueHandlesEmptyLists() {
        List<Integer> merged = CareerIntelligenceService.mergeUnique(List.of(), List.of());
        assertTrue(merged.isEmpty());
    }

    @Test
    void recommendedFocusIsEmptyWhenNoTopicsMerged() {
        CareerIntelligenceService.RecommendedFocus focus =
                new CareerIntelligenceService.RecommendedFocus(List.of(), List.of());
        assertTrue(focus.isEmpty());
    }

    @Test
    void recommendedFocusIsNotEmptyWhenTopicsPresent() {
        CareerIntelligenceService.RecommendedFocus focus =
                new CareerIntelligenceService.RecommendedFocus(List.of(1, 2), List.of("Core Java", "SQL"));
        assertFalse(focus.isEmpty());
    }

    // -----------------------------------------------------------------
    // Career Intelligence Updates: buildChangeExplanation / biggestDimensionMover
    // -----------------------------------------------------------------

    private static ReadinessScoreDAO.Snapshot snapshot(double overall, double technical, double problemSolving,
                                                         double communication, double behavioral, double roleAlignment) {
        return new ReadinessScoreDAO.Snapshot(overall, 0, 0, 0, 0, 0, technical, problemSolving, communication,
                behavioral, roleAlignment, null, null, null, null);
    }

    private static LearningRoadmap roadmapWithTopItem(String title, int itemCount) {
        LearningRoadmap roadmap = new LearningRoadmap();
        RoadmapItem item = new RoadmapItem();
        item.setItemOrder(1);
        item.setTitle(title);
        java.util.List<RoadmapItem> items = new java.util.ArrayList<>();
        items.add(item);
        for (int i = 2; i <= itemCount; i++) {
            RoadmapItem extra = new RoadmapItem();
            extra.setItemOrder(i);
            extra.setTitle("Item " + i);
            items.add(extra);
        }
        roadmap.setItems(items);
        return roadmap;
    }

    @Test
    void firstEverComputationExplainsThereIsNoPreviousScore() {
        String explanation = CareerIntelligenceService.buildChangeExplanation(
                CareerRefreshTrigger.ASSESSMENT_COMPLETED, Optional.empty(), 62.5, Map.of(),
                Optional.empty(), roadmapWithTopItem("Practice weak topic: SQL", 3));

        assertTrue(explanation.contains("first time"));
        assertTrue(explanation.contains("62.5"));
    }

    @Test
    void improvedScoreIsNarratedWithSignedDelta() {
        ReadinessScoreDAO.Snapshot previous = snapshot(60.0, 50, 50, 50, 50, 50);
        String explanation = CareerIntelligenceService.buildChangeExplanation(
                CareerRefreshTrigger.MOCK_INTERVIEW_COMPLETED, Optional.of(previous), 68.0, Map.of(),
                Optional.empty(), roadmapWithTopItem("Take your first AI Mock Interview", 2));

        assertTrue(explanation.contains("improved"));
        assertTrue(explanation.contains("60.0"));
        assertTrue(explanation.contains("68.0"));
        assertTrue(explanation.contains("+8.0"));
    }

    @Test
    void droppedScoreIsNarratedWithoutAPlusSign() {
        ReadinessScoreDAO.Snapshot previous = snapshot(70.0, 50, 50, 50, 50, 50);
        String explanation = CareerIntelligenceService.buildChangeExplanation(
                CareerRefreshTrigger.ASSESSMENT_COMPLETED, Optional.of(previous), 64.0, Map.of(),
                Optional.empty(), roadmapWithTopItem("Practice weak topic: OS", 2));

        assertTrue(explanation.contains("dropped"));
        assertFalse(explanation.contains("+"), "a drop should never be shown with a leading plus sign");
    }

    @Test
    void changedTopRoadmapItemIsCalledOut() {
        LearningRoadmap previousRoadmap = roadmapWithTopItem("Resolve 2 repeated mistake(s)", 3);
        LearningRoadmap newRoadmap = roadmapWithTopItem("Job description needs: Kubernetes", 4);

        String explanation = CareerIntelligenceService.buildChangeExplanation(
                CareerRefreshTrigger.JOB_DESCRIPTION_ANALYZED, Optional.empty(), 55.0, Map.of(),
                Optional.of(previousRoadmap), newRoadmap);

        assertTrue(explanation.contains("Job description needs: Kubernetes"));
        assertTrue(explanation.contains("4 item"));
    }

    @Test
    void biggestDimensionMoverPicksLargestAbsoluteDelta() {
        ReadinessScoreDAO.Snapshot previous = snapshot(60.0, 50, 50, 50, 50, 50);
        Map<String, Double> newDimensions = Map.of(
                "technical", 52.0, "problem_solving", 50.0, "communication", 78.0,
                "behavioral", 51.0, "role_alignment", 50.0);

        String mover = CareerIntelligenceService.biggestDimensionMover(previous, newDimensions);
        assertEquals("Communication", mover);
    }

    @Test
    void biggestDimensionMoverIsNullWhenNothingMovedMeaningfully() {
        ReadinessScoreDAO.Snapshot previous = snapshot(60.0, 50, 50, 50, 50, 50);
        Map<String, Double> newDimensions = Map.of(
                "technical", 50.1, "problem_solving", 50.0, "communication", 50.0,
                "behavioral", 50.0, "role_alignment", 50.0);

        assertNull(CareerIntelligenceService.biggestDimensionMover(previous, newDimensions));
    }

    @Test
    void biggestDimensionMoverIsNullWhenNoDimensionDataYet() {
        ReadinessScoreDAO.Snapshot previous = snapshot(60.0, 50, 50, 50, 50, 50);
        assertNull(CareerIntelligenceService.biggestDimensionMover(previous, Map.of()));
    }
}
