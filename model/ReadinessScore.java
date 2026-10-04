package com.careerintelligence.model;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Result of {@code service.ReadinessScoreService#compute}: an overall 0-100
 * interview-readiness score blended from up to five weighted components,
 * plus the individual component values/weights actually used (a component
 * is only included once the user has produced data for it) so the UI can
 * explain the number instead of just showing it.
 */
public class ReadinessScore {

    /** One weighted ingredient of the overall score. */
    public static class Component {
        private final String label;
        private final double value;   // 0-100
        private final int weight;     // relative weight actually applied (after renormalisation)
        private final String note;

        public Component(String label, double value, int weight, String note) {
            this.label = label;
            this.value = value;
            this.weight = weight;
            this.note = note;
        }

        public String getLabel() {
            return label;
        }

        public double getValue() {
            return value;
        }

        public int getWeight() {
            return weight;
        }

        public String getNote() {
            return note;
        }
    }

    private double overallScore;
    private String band;
    private final Map<String, Component> components = new LinkedHashMap<>();
    /**
     * The five Explainable Readiness dimensions (Technical, Problem
     * Solving, Communication, Behavioral, Role Alignment) - the same
     * underlying signals in {@link #components}, re-blended into
     * human-facing categories by {@code service.ReadinessScoreService}
     * so the score explains *what kind* of readiness is lacking, not
     * just a single number. Keyed by lower_snake_case dimension id
     * ("technical", "problem_solving", "communication", "behavioral",
     * "role_alignment"), insertion-ordered.
     */
    private final Map<String, Component> dimensions = new LinkedHashMap<>();
    /** The highest-scoring dimension the user currently has data for, e.g. "Technical (82.0/100)". */
    private String biggestStrength;
    /** The lowest-scoring dimension the user currently has data for, e.g. "Communication (41.0/100)". */
    private String biggestRisk;
    /** A single, concrete, data-driven "what to do next" recommendation - the Next Best Action. */
    private String nextRecommendedAction;
    private LocalDateTime computedAt;

    public double getOverallScore() {
        return overallScore;
    }

    public void setOverallScore(double overallScore) {
        this.overallScore = overallScore;
    }

    public String getBand() {
        return band;
    }

    public void setBand(String band) {
        this.band = band;
    }

    public Map<String, Component> getComponents() {
        return components;
    }

    public void addComponent(String key, Component component) {
        components.put(key, component);
    }

    public LocalDateTime getComputedAt() {
        return computedAt;
    }

    public void setComputedAt(LocalDateTime computedAt) {
        this.computedAt = computedAt;
    }

    public Map<String, Component> getDimensions() {
        return dimensions;
    }

    public void addDimension(String key, Component dimension) {
        dimensions.put(key, dimension);
    }

    public String getBiggestStrength() {
        return biggestStrength;
    }

    public void setBiggestStrength(String biggestStrength) {
        this.biggestStrength = biggestStrength;
    }

    public String getBiggestRisk() {
        return biggestRisk;
    }

    public void setBiggestRisk(String biggestRisk) {
        this.biggestRisk = biggestRisk;
    }

    public String getNextRecommendedAction() {
        return nextRecommendedAction;
    }

    public void setNextRecommendedAction(String nextRecommendedAction) {
        this.nextRecommendedAction = nextRecommendedAction;
    }
}
