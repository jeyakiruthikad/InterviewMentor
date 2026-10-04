package com.careerintelligence.service;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the pure, database-free JD-gap-into-roadmap merging logic
 * behind step 2b of {@link RoadmapService#generate}:
 * {@link RoadmapService#buildJdGapItems}. No database or MySQL connection
 * is required - run with: mvn test
 */
class RoadmapServiceJobDescriptionGapTest {

    @Test
    void missingSkillIsFlaggedAsMissingFromResume() {
        List<RoadmapService.JdGapItem> items = RoadmapService.buildJdGapItems(
                List.of("Docker"), List.of("Docker"), Set.of(), 10);

        assertEquals(1, items.size());
        assertEquals("Docker", items.get(0).skill());
        assertTrue(items.get(0).missingFromResume());
    }

    @Test
    void weakButPresentSkillIsNotFlaggedAsMissingFromResume() {
        List<RoadmapService.JdGapItem> items = RoadmapService.buildJdGapItems(
                List.of("Java"), List.of(), Set.of(), 10);

        assertEquals(1, items.size());
        assertFalse(items.get(0).missingFromResume());
    }

    @Test
    void skillsAlreadyCoveredByRoleGapsAreSkipped() {
        Set<String> alreadyCovered = new HashSet<>(List.of("docker"));
        List<RoadmapService.JdGapItem> items = RoadmapService.buildJdGapItems(
                List.of("Docker", "Kubernetes"), List.of("Docker", "Kubernetes"), alreadyCovered, 10);

        assertEquals(1, items.size());
        assertEquals("Kubernetes", items.get(0).skill());
    }

    @Test
    void respectsTheRemainingItemBudget() {
        List<RoadmapService.JdGapItem> items = RoadmapService.buildJdGapItems(
                List.of("Java", "Docker", "Kubernetes"), List.of(), Set.of(), 2);

        assertEquals(2, items.size());
    }

    @Test
    void zeroBudgetOrNullHighPriorityYieldsNoItems() {
        assertTrue(RoadmapService.buildJdGapItems(List.of("Java"), List.of(), Set.of(), 0).isEmpty());
        assertTrue(RoadmapService.buildJdGapItems(null, List.of(), Set.of(), 5).isEmpty());
    }

    @Test
    void duplicateHighPrioritySkillIsOnlyAddedOnce() {
        List<RoadmapService.JdGapItem> items = RoadmapService.buildJdGapItems(
                List.of("Java", "Java"), List.of(), Set.of(), 10);
        assertEquals(1, items.size());
    }
}
