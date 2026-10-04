package com.careerintelligence.ai;

import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Observability for the AI layer: records whether each AI-backed operation
 * was served by the live LLM endpoint or by the local deterministic
 * fallback, so the application can <em>tell the user</em> which engine
 * produced a result instead of silently degrading.
 *
 * <p>The system is designed to stay fully functional with no API key at
 * all - {@code AIServiceImpl} always has a local heuristic path. That is a
 * strength, but only if it is visible: a candidate reading AI feedback
 * should know whether it came from a live model or the offline evaluator,
 * and a demo audience should see that an LLM outage degrades the product
 * gracefully rather than breaking it.
 *
 * <p>This class is process-wide, thread-safe, and deliberately dependency
 * free. It never throws and never blocks: recording a failure must be
 * cheaper and safer than the failure it describes.
 */
public final class AIHealth {

    /** How many recent degradation events to keep for display. */
    private static final int MAX_EVENTS = 10;

    private static final AtomicInteger liveCalls = new AtomicInteger();
    private static final AtomicInteger fallbackCalls = new AtomicInteger();
    private static final Deque<Event> events = new ArrayDeque<>();

    private static volatile boolean liveConfigured;

    /** One recorded degradation: which operation fell back, and why. */
    public record Event(String operation, String reason, LocalDateTime at) {
    }

    private AIHealth() {
    }

    /** Records that the live API was configured (or not) at startup. */
    public static void setLiveConfigured(boolean configured) {
        liveConfigured = configured;
    }

    public static boolean isLiveConfigured() {
        return liveConfigured;
    }

    /** Records a successful live LLM call for {@code operation}. */
    public static void recordLive(String operation) {
        liveCalls.incrementAndGet();
    }

    /**
     * Records that {@code operation} fell back to the local engine, with
     * the reason. Safe to call from any catch block; it swallows all
     * errors so it can never mask the original failure.
     */
    public static void recordFallback(String operation, String reason) {
        try {
            fallbackCalls.incrementAndGet();
            synchronized (events) {
                events.addFirst(new Event(operation, summarise(reason), LocalDateTime.now()));
                while (events.size() > MAX_EVENTS) {
                    events.removeLast();
                }
            }
        } catch (RuntimeException ignored) {
            // Never let health tracking break the request it is describing.
        }
    }

    public static int liveCallCount() {
        return liveCalls.get();
    }

    public static int fallbackCallCount() {
        return fallbackCalls.get();
    }

    /** The most recent degradation events, newest first. */
    public static List<Event> recentEvents() {
        synchronized (events) {
            return new ArrayList<>(events);
        }
    }

    /**
     * A short, user-facing description of which engine is currently
     * serving AI features - shown in the dashboard header and the mock
     * interview screen.
     */
    public static String describeMode() {
        if (!liveConfigured) {
            return "local semantic engine (no API key configured)";
        }
        if (fallbackCalls.get() == 0) {
            return "live LLM";
        }
        if (liveCalls.get() == 0) {
            return "local semantic engine (live API unreachable)";
        }
        return "live LLM with local fallback (" + fallbackCalls.get() + " degraded call(s))";
    }

    /**
     * A one-line banner shown after an AI-backed action when it was served
     * by the fallback, or {@code null} when nothing needs saying. Keeping
     * this in one place stops each screen inventing its own wording.
     */
    public static String degradationNotice() {
        Event latest;
        synchronized (events) {
            latest = events.peekFirst();
        }
        if (latest == null) {
            return null;
        }
        return "AI note: " + latest.operation() + " was served by the local semantic engine ("
                + latest.reason() + "). Results remain fully functional and deterministic.";
    }

    /** Clears all recorded state. Used by tests and by the guided demo between stages. */
    public static void reset() {
        liveCalls.set(0);
        fallbackCalls.set(0);
        synchronized (events) {
            events.clear();
        }
    }

    /** Trims an exception message to something short enough to display inline. */
    private static String summarise(String reason) {
        if (reason == null || reason.isBlank()) {
            return "no detail available";
        }
        String cleaned = reason.replaceAll("\\s+", " ").trim();
        return cleaned.length() <= 80 ? cleaned : cleaned.substring(0, 77) + "...";
    }
}
