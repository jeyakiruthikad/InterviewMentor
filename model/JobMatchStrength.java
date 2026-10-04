package com.careerintelligence.model;

/**
 * How well a single job-description skill is evidenced by a candidate's
 * resume + assessment history (service.ResumeService#matchResumeToJobDescription):
 *
 *  - STRONG_MATCH: on the resume AND assessed at/above a strong accuracy
 *    threshold (or on the resume with no contradicting evidence at all).
 *  - PARTIAL_MATCH: on the resume, but assessment accuracy is moderate, or
 *    there simply isn't enough assessment evidence yet to call it strong.
 *  - WEAK_EVIDENCE: on the resume, but assessment accuracy on it is low -
 *    a claimed skill the candidate has struggled to demonstrate.
 *  - MISSING: not found on the resume at all.
 */
public enum JobMatchStrength {
    STRONG_MATCH,
    PARTIAL_MATCH,
    WEAK_EVIDENCE,
    MISSING
}
