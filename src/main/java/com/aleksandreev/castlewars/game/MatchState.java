package com.aleksandreev.castlewars.game;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Pure match rules/state: no Minecraft or Forge classes so it can be unit-tested cheaply. */
public final class MatchState {
    public enum KillResult {
        NONE,
        POINT,
        MATCH_WON
    }

    private final UUID redPlayer;
    private final UUID bluePlayer;
    private final int targetScore;
    private final Map<Side, Integer> scores = new EnumMap<>(Side.class);
    private final Map<Side, Boolean> bedDestroyed = new EnumMap<>(Side.class);
    private boolean finished;

    public MatchState(UUID redPlayer, UUID bluePlayer, int targetScore) {
        this.redPlayer = Objects.requireNonNull(redPlayer, "redPlayer");
        this.bluePlayer = Objects.requireNonNull(bluePlayer, "bluePlayer");
        if (redPlayer.equals(bluePlayer)) {
            throw new IllegalArgumentException("RED and BLUE must be different players");
        }
        if (targetScore <= 0) {
            throw new IllegalArgumentException("targetScore must be positive");
        }
        this.targetScore = targetScore;
        scores.put(Side.RED, 0);
        scores.put(Side.BLUE, 0);
        bedDestroyed.put(Side.RED, false);
        bedDestroyed.put(Side.BLUE, false);
    }

    public Side sideOf(UUID player) {
        if (redPlayer.equals(player)) return Side.RED;
        if (bluePlayer.equals(player)) return Side.BLUE;
        return null;
    }

    public UUID player(Side side) {
        return side == Side.RED ? redPlayer : bluePlayer;
    }

    public int score(Side side) {
        return scores.get(side);
    }

    public boolean isBedDestroyed(Side side) {
        return bedDestroyed.get(side);
    }

    public boolean isFinished() {
        return finished;
    }

    public boolean destroyBed(Side bedOwner, Side attacker) {
        if (finished || bedOwner == null || attacker == null || attacker != bedOwner.opponent()) {
            return false;
        }
        if (bedDestroyed.get(bedOwner)) {
            return false;
        }
        bedDestroyed.put(bedOwner, true);
        return true;
    }

    /**
     * A participant death scores only after that participant's objective bed was destroyed and the defeating
     * side is the opponent. Environmental deaths and self/friendly defeats intentionally do not score.
     */
    public KillResult onKill(UUID victimId, UUID killerId) {
        if (killerId == null) {
            return KillResult.NONE;
        }
        return onDefeat(victimId, sideOf(killerId));
    }

    /** Allows a team-owned arena entity, such as a castle guard golem, to receive team credit. */
    public KillResult onDefeat(UUID victimId, Side defeatingSide) {
        if (finished || victimId == null || defeatingSide == null) {
            return KillResult.NONE;
        }
        Side victim = sideOf(victimId);
        if (victim == null || !CaptureRules.awardsPoint(isBedDestroyed(victim), victim, defeatingSide)) {
            return KillResult.NONE;
        }

        int next = scores.get(defeatingSide) + 1;
        scores.put(defeatingSide, next);
        if (CaptureRules.hasWon(next, targetScore)) {
            finished = true;
            return KillResult.MATCH_WON;
        }
        return KillResult.POINT;
    }

    public void resetRoundObjectives() {
        if (!finished) {
            bedDestroyed.put(Side.RED, false);
            bedDestroyed.put(Side.BLUE, false);
        }
    }
}
