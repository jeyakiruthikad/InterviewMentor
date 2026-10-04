package com.careerintelligence.util;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Small, dependency-free text helper used by the mock interview to avoid
 * asking the candidate the same (or almost the same) question twice.
 */
public final class TextSimilarity {

    private static final Set<String> FILLER = Set.of(
            "a", "an", "the", "is", "are", "was", "were", "of", "in", "on", "at", "to", "and", "or", "for",
            "with", "that", "this", "it", "you", "your", "me", "can", "could", "would", "do", "did", "how",
            "what", "why", "about", "please", "tell", "give", "walk", "through", "us", "i", "my", "we");

    private TextSimilarity() {
    }

    /** Lower-cased, punctuation-free, filler-free word set of the text. */
    static Set<String> contentWords(String text) {
        Set<String> words = new HashSet<>();
        if (text == null) {
            return words;
        }
        for (String token : text.toLowerCase(Locale.ROOT).split("[^a-z0-9+#.]+")) {
            String t = token.replaceAll("^\\.+|\\.+$", "");
            if (t.length() > 1 && !FILLER.contains(t)) {
                words.add(t);
            }
        }
        return words;
    }

    /** Jaccard overlap (0.0 - 1.0) of the two texts' content words. */
    public static double overlap(String a, String b) {
        Set<String> wa = contentWords(a);
        Set<String> wb = contentWords(b);
        if (wa.isEmpty() || wb.isEmpty()) {
            return 0.0;
        }
        Set<String> inter = new HashSet<>(wa);
        inter.retainAll(wb);
        Set<String> union = new HashSet<>(wa);
        union.addAll(wb);
        return (double) inter.size() / union.size();
    }

    /** True if the two questions are effectively the same question (default threshold 0.6). */
    public static boolean isNearDuplicate(String a, String b) {
        return overlap(a, b) >= 0.6;
    }
}
