package com.careerintelligence.model;

/**
 * How an assessment's question set was built:
 *  - STANDARD: uniformly random questions across chosen topics (40% behaviour, unchanged)
 *  - ADAPTIVE: difficulty mix per topic chosen from the user's historical topic accuracy
 *  - RETRY: exact set of previously-incorrect/unanswered questions from the mistake log
 *  - RESUME: questions drawn from topics matched to the user's AI-extracted resume skills (current implementation)
 */
public enum AssessmentMode {
    STANDARD,
    ADAPTIVE,
    RETRY,
    RESUME
}
