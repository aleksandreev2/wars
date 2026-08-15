package com.aleksandreev.castlewars.game;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CaptureRulesTest {
    @Test
    void killDoesNotScoreWhileVictimBedIsAlive() {
        assertFalse(CaptureRules.awardsPoint(false, Side.BLUE, Side.RED));
    }

    @Test
    void enemyKillScoresAfterVictimBedWasDestroyed() {
        assertTrue(CaptureRules.awardsPoint(true, Side.BLUE, Side.RED));
        assertTrue(CaptureRules.awardsPoint(true, Side.RED, Side.BLUE));
    }

    @Test
    void sameSideNeverScores() {
        assertFalse(CaptureRules.awardsPoint(true, Side.RED, Side.RED));
    }

    @Test
    void firstToTenWins() {
        assertFalse(CaptureRules.hasWon(9, CaptureRules.DEFAULT_WIN_SCORE));
        assertTrue(CaptureRules.hasWon(10, CaptureRules.DEFAULT_WIN_SCORE));
        assertTrue(CaptureRules.hasWon(11, CaptureRules.DEFAULT_WIN_SCORE));
    }

    @Test
    void targetScoreMustBePositive() {
        assertThrows(IllegalArgumentException.class, () -> CaptureRules.hasWon(0, 0));
    }
}
