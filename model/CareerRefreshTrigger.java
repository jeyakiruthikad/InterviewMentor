package com.careerintelligence.model;

/**
 * What kind of user activity caused {@code service.CareerIntelligenceService
 * #refreshAfterActivity} to recompute the Interview Readiness Score and
 * regenerate the Learning Roadmap. Persisted to `career_refresh_log` purely
 * as an audit trail of the "Roadmap Update" step in the Career
 * Intelligence pipeline (Profile -> Resume -> Skills -> Skill Gap ->
 * Roadmap -> Recommendations -> Adaptive Assessment -> AI Evaluation ->
 * Mistakes -> Practice -> Mock Interview -> Readiness -> Dashboard ->
 * Roadmap Update).
 */
public enum CareerRefreshTrigger {
    ASSESSMENT_COMPLETED,
    MISTAKE_RETRY_COMPLETED,
    MOCK_INTERVIEW_COMPLETED,
    RESUME_UPDATED,
    MANUAL_ROADMAP_REQUEST,
    JOB_DESCRIPTION_ANALYZED
}
