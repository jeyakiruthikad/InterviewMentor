package com.careerintelligence.util;

import java.util.Scanner;
import java.util.concurrent.*;

/**
 * Reads a line from a Scanner with a timeout, using a single-thread
 * ExecutorService per call. This is what allows the assessment timer
 * (backed by a shared ScheduledExecutorService, see service.AssessmentService)
 * to auto-submit the assessment even while the user is in the middle of
 * answering a question, instead of blocking forever on System.in.
 *
 * Note: Java cannot forcibly interrupt a blocking System.in.read() call, so
 * on timeout the background reader thread is left running as a daemon and
 * is discarded; the next real line typed by the user (if any) is simply
 * ignored because a brand-new reader/executor is used for every prompt.
 */
public final class TimedInputReader {

    /**
     * Attempts to read one line of input within the given timeout.
     *
     * @return the line typed by the user, or {@code null} if the timeout elapsed first.
     */
    public static String readLineWithTimeout(Scanner scanner, long timeout, TimeUnit unit) {
        ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "timed-input-reader");
            t.setDaemon(true);
            return t;
        });
        try {
            Future<String> future = executor.submit(scanner::nextLine);
            return future.get(timeout, unit);
        } catch (TimeoutException e) {
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } catch (ExecutionException e) {
            return null;
        } finally {
            executor.shutdownNow();
        }
    }
}
