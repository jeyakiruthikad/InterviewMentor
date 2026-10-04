package com.careerintelligence.service;

import com.careerintelligence.ai.AIService.FollowUpType;
import com.careerintelligence.ai.AIService.InterviewPhase;
import com.careerintelligence.ai.AIServiceImpl.StarAnalysis;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Database-free tests for the interview flow policy (behavioral count, follow-up decisions, limits). */
class MockInterviewServiceFlowTest {

    private static StarAnalysis star(int found) {
        return new StarAnalysis(found > 0, found > 1, found > 2, found > 3, found * 25, "");
    }

    @Test
    void behavioralOpenersAreTwoOrThree() {
        assertEquals(2, MockInterviewService.behavioralCountFor(5));
        assertEquals(3, MockInterviewService.behavioralCountFor(6));
        assertEquals(3, MockInterviewService.behavioralCountFor(8));
        assertEquals(0, MockInterviewService.behavioralCountFor(1));
    }

    @Test
    void weakTechnicalAnswerIsClarifiedOnce() {
        assertEquals(FollowUpType.CLARIFY,
                MockInterviewService.decideFollowUp(40, InterviewPhase.TECHNICAL, 0, 0, null));
        assertNull(MockInterviewService.decideFollowUp(40, InterviewPhase.TECHNICAL, 1, 1, null));
    }

    @Test
    void strongTechnicalAnswerIsDeepenedButCapped() {
        assertEquals(FollowUpType.DEEPEN,
                MockInterviewService.decideFollowUp(92, InterviewPhase.TECHNICAL, 0, 0, null));
        assertEquals(FollowUpType.DEEPEN,
                MockInterviewService.decideFollowUp(92, InterviewPhase.TECHNICAL, 1, 1, null));
        assertNull(MockInterviewService.decideFollowUp(92, InterviewPhase.TECHNICAL, 2, 2, null));
    }

    @Test
    void midRangeAnswerMovesOn() {
        assertNull(MockInterviewService.decideFollowUp(75, InterviewPhase.TECHNICAL, 0, 0, null));
    }

    @Test
    void behavioralAnswerMissingStarElementsIsProbedOnce() {
        assertEquals(FollowUpType.STAR_PROBE,
                MockInterviewService.decideFollowUp(60, InterviewPhase.BEHAVIORAL, 0, 0, star(2)));
        assertNull(MockInterviewService.decideFollowUp(60, InterviewPhase.BEHAVIORAL, 1, 1, star(2)));
    }

    @Test
    void completeStrongBehavioralAnswerIsDeepened() {
        assertEquals(FollowUpType.DEEPEN,
                MockInterviewService.decideFollowUp(90, InterviewPhase.BEHAVIORAL, 0, 0, star(4)));
    }

    @Test
    void introductionOnlyClarifiedWhenThin() {
        assertNull(MockInterviewService.decideFollowUp(90, InterviewPhase.BEHAVIORAL, 0, 0, null));
        assertEquals(FollowUpType.CLARIFY,
                MockInterviewService.decideFollowUp(30, InterviewPhase.BEHAVIORAL, 0, 0, null));
    }

    @Test
    void sessionWideFollowUpLimitIsEnforced() {
        assertNull(MockInterviewService.decideFollowUp(20, InterviewPhase.TECHNICAL, 0,
                MockInterviewService.MAX_FOLLOWUPS_PER_SESSION, null));
    }
}
