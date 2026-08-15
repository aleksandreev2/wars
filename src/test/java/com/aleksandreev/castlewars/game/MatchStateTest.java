package com.aleksandreev.castlewars.game;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MatchStateTest {
    private final UUID red = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private final UUID blue = UUID.fromString("00000000-0000-0000-0000-000000000002");

    @Test
    void bedBreakAloneNeverScores() {
        MatchState match = new MatchState(red, blue, 10);
        assertTrue(match.destroyBed(Side.BLUE, Side.RED));
        assertEquals(0, match.score(Side.RED));
    }

    @Test
    void enemyKillAfterBedBreakScoresExactlyOnce() {
        MatchState match = new MatchState(red, blue, 10);
        assertTrue(match.destroyBed(Side.BLUE, Side.RED));
        assertEquals(MatchState.KillResult.POINT, match.onKill(blue, red));
        assertEquals(1, match.score(Side.RED));

        match.resetRoundObjectives();
        assertFalse(match.isBedDestroyed(Side.BLUE));
        assertEquals(MatchState.KillResult.NONE, match.onKill(blue, red));
        assertEquals(1, match.score(Side.RED));
    }

    @Test
    void teamGuardCanSecureCaptureAfterBedBreak() {
        MatchState match = new MatchState(red, blue, 10);
        assertTrue(match.destroyBed(Side.BLUE, Side.RED));
        assertEquals(MatchState.KillResult.POINT, match.onDefeat(blue, Side.RED));
        assertEquals(1, match.score(Side.RED));
    }

    @Test
    void guardCannotScoreAgainstItsOwnParticipant() {
        MatchState match = new MatchState(red, blue, 10);
        assertTrue(match.destroyBed(Side.RED, Side.BLUE));
        assertEquals(MatchState.KillResult.NONE, match.onDefeat(red, Side.RED));
        assertEquals(0, match.score(Side.RED));
    }

    @Test
    void npcUuidBehavesLikeARealParticipantForCaptureRules() {
        UUID npc = UUID.fromString("00000000-0000-0000-0000-000000000099");
        MatchState match = new MatchState(red, npc, 10);

        assertEquals(MatchState.KillResult.NONE, match.onDefeat(npc, Side.RED));
        assertEquals(0, match.score(Side.RED));

        assertTrue(match.destroyBed(Side.BLUE, Side.RED));
        assertEquals(MatchState.KillResult.POINT, match.onDefeat(npc, Side.RED));
        assertEquals(1, match.score(Side.RED));
    }

    @Test
    void deathWithoutValidOpponentDoesNotScore() {
        MatchState match = new MatchState(red, blue, 10);
        match.destroyBed(Side.BLUE, Side.RED);
        assertEquals(MatchState.KillResult.NONE, match.onKill(blue, null));
        assertEquals(MatchState.KillResult.NONE, match.onKill(blue, blue));
        assertEquals(0, match.score(Side.RED));
    }

    @Test
    void firstToTenWins() {
        MatchState match = new MatchState(red, blue, 10);
        for (int i = 0; i < 9; i++) {
            assertTrue(match.destroyBed(Side.BLUE, Side.RED));
            assertEquals(MatchState.KillResult.POINT, match.onKill(blue, red));
            match.resetRoundObjectives();
        }

        assertTrue(match.destroyBed(Side.BLUE, Side.RED));
        assertEquals(MatchState.KillResult.MATCH_WON, match.onKill(blue, red));
        assertEquals(10, match.score(Side.RED));
        assertTrue(match.isFinished());
    }
}
