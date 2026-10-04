package com.careerintelligence.ui;

import com.careerintelligence.model.Badge;
import com.careerintelligence.service.GamificationService;

/**
 * Tiny shared helper so every feature that awards gamification points
 * (assessments, mistakes, mock interviews, resume analysis, roadmap,
 * role prep, login streaks) prints the result the same way, instead of
 * each menu re-implementing it.
 */
final class GamificationUi {

    private GamificationUi() {
    }

    static void printAward(GamificationService.AwardResult award) {
        if (award == null) {
            return;
        }
        System.out.printf("[+] Points: %d total (Level %d)%n", award.points().getTotalPoints(), award.points().getLevel());
        for (Badge badge : award.newlyEarnedBadges()) {
            System.out.println("[BADGE UNLOCKED] " + badge.getBadgeName() + " - " + badge.getDescription());
        }
    }
}
