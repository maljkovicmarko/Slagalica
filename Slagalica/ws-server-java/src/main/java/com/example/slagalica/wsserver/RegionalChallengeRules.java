package com.example.slagalica.wsserver;

final class RegionalChallengeRules {
    private RegionalChallengeRules() {
    }

    static int winnerShare(int stakePerPlayer, int participantCount) {
        if (stakePerPlayer <= 0 || participantCount <= 0) {
            return 0;
        }
        return stakePerPlayer * participantCount * 75 / 100;
    }

    static int compareResults(int leftScore,
                              long leftDurationMs,
                              int leftJoinOrder,
                              int rightScore,
                              long rightDurationMs,
                              int rightJoinOrder) {
        int scoreComparison = Integer.compare(rightScore, leftScore);
        if (scoreComparison != 0) {
            return scoreComparison;
        }
        int durationComparison = Long.compare(leftDurationMs, rightDurationMs);
        if (durationComparison != 0) {
            return durationComparison;
        }
        return Integer.compare(leftJoinOrder, rightJoinOrder);
    }
}
