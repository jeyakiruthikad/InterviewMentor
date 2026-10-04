package com.careerintelligence.util;

import com.careerintelligence.model.DifficultyLevel;

/**
 * Pure, database-free adaptive-learning logic shared by
 * {@code dao.TopicPerformanceDAO} and {@code service.ProgressAnalyticsService}.
 * Kept separate (and stateless/static) so the adaptive rules themselves -
 * "what should the next difficulty be" and "is this topic trending up or
 * down" - can be unit tested directly, the same way
 * {@code service.CareerIntelligenceService#mergeUnique} is.
 *
 * Two signals feed the Adaptive Learning engine:
 *  1. Cumulative accuracy (the existing final implementation rule: EASY under
 *     3 attempts, then EASY/MEDIUM/HARD by accuracy band).
 *  2. Recent momentum - the last {@link #WINDOW} outcomes, stored as a
 *     compact '1'/'0' string (most recent last) in
 *     {@code topic_performance.recent_results}. A hot streak nudges the
 *     cumulative recommendation up a level (so a recovering user is
 *     re-challenged before their lifetime average catches up); a cold
 *     streak forces it back down to EASY (so a recent slump - even for a
 *     historically strong topic - is never left on HARD questions),
 *     directly connecting "mistakes" and "improvement" to question
 *     selection rather than only a slow-moving lifetime average.
 */
public final class AdaptiveDifficultyCalculator {

    /** How many recent outcomes are tracked/considered for momentum. */
    public static final int WINDOW = 5;
    private static final int STREAK_LENGTH = 3;

    private AdaptiveDifficultyCalculator() {
    }

    /** Trend classification for Progress Analytics, based on the recent-results window. */
    public enum Trend {
        IMPROVING, DECLINING, STABLE, INSUFFICIENT_DATA
    }

    /**
     * Appends one outcome ('1' correct / '0' wrong) to the tracked recent-results
     * string, keeping only the most recent {@link #WINDOW} entries.
     */
    public static String appendResult(String recentResults, boolean correct) {
        String base = recentResults == null ? "" : recentResults;
        String appended = base + (correct ? "1" : "0");
        if (appended.length() > WINDOW) {
            appended = appended.substring(appended.length() - WINDOW);
        }
        return appended;
    }

    /** The cumulative-accuracy baseline rule alone (no momentum), kept for callers that only want that. */
    public static DifficultyLevel baselineDifficulty(double accuracyPercent, int attempts) {
        if (attempts < 3) {
            return DifficultyLevel.EASY;
        }
        if (accuracyPercent >= 75.0) {
            return DifficultyLevel.HARD;
        }
        if (accuracyPercent >= 45.0) {
            return DifficultyLevel.MEDIUM;
        }
        return DifficultyLevel.EASY;
    }

    /**
     * Full adaptive recommendation: the cumulative-accuracy baseline, adjusted by
     * recent momentum. A {@value #STREAK_LENGTH}-in-a-row cold streak always wins
     * (forces EASY); a {@value #STREAK_LENGTH}-in-a-row hot streak bumps the
     * baseline up one level (capped at HARD). Anything short of a clear streak
     * leaves the accuracy-based baseline untouched.
     */
    public static DifficultyLevel deriveDifficulty(double accuracyPercent, int attempts, String recentResults) {
        DifficultyLevel baseline = baselineDifficulty(accuracyPercent, attempts);
        String recent = recentResults == null ? "" : recentResults;

        if (isColdStreak(recent)) {
            return DifficultyLevel.EASY;
        }
        if (isHotStreak(recent) && attempts >= 3) {
            return bumpUp(baseline);
        }
        return baseline;
    }

    private static boolean isColdStreak(String recent) {
        if (recent.length() < STREAK_LENGTH) {
            return false;
        }
        String tail = recent.substring(recent.length() - STREAK_LENGTH);
        return tail.chars().allMatch(c -> c == '0');
    }

    private static boolean isHotStreak(String recent) {
        if (recent.length() < STREAK_LENGTH) {
            return false;
        }
        String tail = recent.substring(recent.length() - STREAK_LENGTH);
        return tail.chars().allMatch(c -> c == '1');
    }

    private static DifficultyLevel bumpUp(DifficultyLevel level) {
        return switch (level) {
            case EASY -> DifficultyLevel.MEDIUM;
            case MEDIUM -> DifficultyLevel.HARD;
            case HARD -> DifficultyLevel.HARD;
        };
    }

    /**
     * Classifies whether a topic is trending up, down, or holding steady by
     * comparing the accuracy of the first half of the tracked window against
     * the second half. Needs at least 4 tracked outcomes to say anything
     * meaningful; otherwise reports {@link Trend#INSUFFICIENT_DATA}.
     */
    public static Trend classifyTrend(String recentResults) {
        String recent = recentResults == null ? "" : recentResults;
        if (recent.length() < 4) {
            return Trend.INSUFFICIENT_DATA;
        }
        int mid = recent.length() / 2;
        double firstHalfAccuracy = accuracyOf(recent.substring(0, mid));
        double secondHalfAccuracy = accuracyOf(recent.substring(mid));
        double delta = secondHalfAccuracy - firstHalfAccuracy;
        if (delta >= 20.0) {
            return Trend.IMPROVING;
        }
        if (delta <= -20.0) {
            return Trend.DECLINING;
        }
        return Trend.STABLE;
    }

    private static double accuracyOf(String segment) {
        if (segment.isEmpty()) {
            return 0.0;
        }
        long correct = segment.chars().filter(c -> c == '1').count();
        return correct * 100.0 / segment.length();
    }
}
