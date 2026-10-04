package com.careerintelligence.util;

import com.careerintelligence.model.DifficultyLevel;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the pure, database-free Adaptive Learning momentum rules in
 * {@link AdaptiveDifficultyCalculator}. No database or MySQL connection is
 * required - run with: mvn test
 */
class AdaptiveDifficultyCalculatorTest {

    @Test
    void fewerThanThreeAttemptsAlwaysEasy() {
        assertEquals(DifficultyLevel.EASY, AdaptiveDifficultyCalculator.baselineDifficulty(95.0, 2));
    }

    @Test
    void baselineFollowsCumulativeAccuracyBands() {
        assertEquals(DifficultyLevel.HARD, AdaptiveDifficultyCalculator.baselineDifficulty(80.0, 10));
        assertEquals(DifficultyLevel.MEDIUM, AdaptiveDifficultyCalculator.baselineDifficulty(50.0, 10));
        assertEquals(DifficultyLevel.EASY, AdaptiveDifficultyCalculator.baselineDifficulty(20.0, 10));
    }

    @Test
    void coldStreakForcesEasyRegardlessOfCumulativeAccuracy() {
        // High lifetime accuracy, but the last 3 answers were all wrong.
        DifficultyLevel result = AdaptiveDifficultyCalculator.deriveDifficulty(85.0, 12, "1111000");
        assertEquals(DifficultyLevel.EASY, result);
    }

    @Test
    void hotStreakBumpsBaselineUpOneLevel() {
        assertEquals(DifficultyLevel.MEDIUM, AdaptiveDifficultyCalculator.deriveDifficulty(20.0, 5, "00111"));
        assertEquals(DifficultyLevel.HARD, AdaptiveDifficultyCalculator.deriveDifficulty(50.0, 5, "00111"));
    }

    @Test
    void hotStreakNeverExceedsHard() {
        assertEquals(DifficultyLevel.HARD, AdaptiveDifficultyCalculator.deriveDifficulty(90.0, 10, "11111"));
    }

    @Test
    void noClearStreakUsesBaselineUnchanged() {
        assertEquals(DifficultyLevel.HARD, AdaptiveDifficultyCalculator.deriveDifficulty(80.0, 10, "10101"));
    }

    @Test
    void appendResultGrowsAndCapsAtWindow() {
        assertEquals("111", AdaptiveDifficultyCalculator.appendResult("11", true));
        assertEquals("0", AdaptiveDifficultyCalculator.appendResult("", false));
        assertEquals("11111", AdaptiveDifficultyCalculator.appendResult("11111", true));
        assertEquals(AdaptiveDifficultyCalculator.WINDOW,
                AdaptiveDifficultyCalculator.appendResult("1111111111", true).length());
    }

    @Test
    void trendNeedsAtLeastFourOutcomes() {
        assertEquals(AdaptiveDifficultyCalculator.Trend.INSUFFICIENT_DATA,
                AdaptiveDifficultyCalculator.classifyTrend("101"));
    }

    @Test
    void trendDetectsImprovingAndDeclining() {
        assertEquals(AdaptiveDifficultyCalculator.Trend.IMPROVING, AdaptiveDifficultyCalculator.classifyTrend("0011"));
        assertEquals(AdaptiveDifficultyCalculator.Trend.DECLINING, AdaptiveDifficultyCalculator.classifyTrend("1100"));
        assertEquals(AdaptiveDifficultyCalculator.Trend.STABLE, AdaptiveDifficultyCalculator.classifyTrend("1010"));
    }
}
