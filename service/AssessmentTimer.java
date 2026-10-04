package com.careerintelligence.service;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Countdown timer for a single assessment attempt, backed by a
 * ScheduledExecutorService (java.util.concurrent), as required for the
 * "Timed assessments and auto-submission" feature.
 *
 * Usage pattern (see ui.AssessmentMenu):
 *   AssessmentTimer timer = new AssessmentTimer(durationMinutes * 60L, () -> autoSubmit());
 *   timer.start();
 *   ... read each answer with TimedInputReader.readLineWithTimeout(scanner, timer.remainingSeconds(), SECONDS) ...
 *   timer.stop(); // once the user submits manually before time is up
 */
public class AssessmentTimer {

    private final long totalSeconds;
    private final Runnable onExpire;
    private final ScheduledExecutorService scheduler =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "assessment-timer");
                t.setDaemon(true);
                return t;
            });
    private final long startEpochMillis;
    private final AtomicBoolean expired = new AtomicBoolean(false);
    private final AtomicBoolean stopped = new AtomicBoolean(false);

    public AssessmentTimer(long totalSeconds, Runnable onExpire) {
        this.totalSeconds = totalSeconds;
        this.onExpire = onExpire;
        this.startEpochMillis = System.currentTimeMillis();
    }

    /** Schedules the auto-submit callback to fire exactly once, when the countdown reaches zero. */
    public void start() {
        scheduler.schedule(() -> {
            if (stopped.compareAndSet(false, true)) {
                expired.set(true);
                onExpire.run();
            }
        }, totalSeconds, TimeUnit.SECONDS);
    }

    /** Seconds remaining before auto-submission fires (never negative). */
    public long remainingSeconds() {
        long elapsed = (System.currentTimeMillis() - startEpochMillis) / 1000;
        long remaining = totalSeconds - elapsed;
        return Math.max(remaining, 0);
    }

    public boolean hasExpired() {
        return expired.get() || remainingSeconds() <= 0;
    }

    public String formattedRemaining() {
        long seconds = remainingSeconds();
        long minutes = seconds / 60;
        long secs = seconds % 60;
        return String.format("%02d:%02d", minutes, secs);
    }

    /** Cancels the scheduled auto-submit (call this once the user submits manually). */
    public void stop() {
        if (stopped.compareAndSet(false, true)) {
            scheduler.shutdownNow();
        }
    }
}
