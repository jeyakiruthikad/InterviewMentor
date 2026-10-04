package com.careerintelligence.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TextSimilarityTest {

    @Test
    void identicalAndRephrasedQuestionsAreDuplicates() {
        assertTrue(TextSimilarity.isNearDuplicate(
                "Explain how database indexing improves query performance.",
                "Can you explain how database indexing improves query performance?"));
    }

    @Test
    void differentQuestionsAreNotDuplicates() {
        assertFalse(TextSimilarity.isNearDuplicate(
                "Explain how database indexing improves query performance.",
                "Tell me about a time you disagreed with a teammate."));
    }

    @Test
    void nullAndBlankAreSafe() {
        assertFalse(TextSimilarity.isNearDuplicate(null, "x"));
        assertEquals(0.0, TextSimilarity.overlap("", ""));
    }
}
