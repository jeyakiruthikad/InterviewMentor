package com.careerintelligence.model;

/**
 * Aggregated cross-assessment statistics for one user, used by the
 * Performance Dashboard. Populated by dao.AssessmentDAO#getOverallStats.
 */
public class OverallStats {

    private int totalAssessments;
    private double overallAccuracyPercent; // correct / (correct + wrong) across all graded answers
    private double averageScorePercent;    // average of (total_score / max_score * 100) per assessment
    private double bestScorePercent;       // best single-assessment percentage
    private int totalCorrect;
    private int totalWrong;
    private int totalUnanswered;

    public int getTotalAssessments() {
        return totalAssessments;
    }

    public void setTotalAssessments(int totalAssessments) {
        this.totalAssessments = totalAssessments;
    }

    public double getOverallAccuracyPercent() {
        return overallAccuracyPercent;
    }

    public void setOverallAccuracyPercent(double overallAccuracyPercent) {
        this.overallAccuracyPercent = overallAccuracyPercent;
    }

    public double getAverageScorePercent() {
        return averageScorePercent;
    }

    public void setAverageScorePercent(double averageScorePercent) {
        this.averageScorePercent = averageScorePercent;
    }

    public double getBestScorePercent() {
        return bestScorePercent;
    }

    public void setBestScorePercent(double bestScorePercent) {
        this.bestScorePercent = bestScorePercent;
    }

    public int getTotalCorrect() {
        return totalCorrect;
    }

    public void setTotalCorrect(int totalCorrect) {
        this.totalCorrect = totalCorrect;
    }

    public int getTotalWrong() {
        return totalWrong;
    }

    public void setTotalWrong(int totalWrong) {
        this.totalWrong = totalWrong;
    }

    public int getTotalUnanswered() {
        return totalUnanswered;
    }

    public void setTotalUnanswered(int totalUnanswered) {
        this.totalUnanswered = totalUnanswered;
    }
}
