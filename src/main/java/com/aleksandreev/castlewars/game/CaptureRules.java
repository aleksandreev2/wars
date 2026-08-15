package com.aleksandreev.castlewars.game;

public final class CaptureRules {
    public static final int DEFAULT_WIN_SCORE = 10;

    private CaptureRules() {
    }

    public static boolean awardsPoint(boolean victimBedDestroyed, Side victim, Side killer) {
        return victimBedDestroyed && victim != null && killer != null && killer == victim.opponent();
    }

    public static boolean hasWon(int score, int targetScore) {
        if (targetScore <= 0) {
            throw new IllegalArgumentException("targetScore must be positive");
        }
        return score >= targetScore;
    }
}
