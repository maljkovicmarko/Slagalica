package com.example.slagalica.Model;

public final class LeaderboardRewardNotification {
    private final String id;
    private final String cycleId;
    private final String cycleType;
    private final int rank;
    private final int tokens;
    private final long createdAtMs;

    public LeaderboardRewardNotification(String id,
                                         String cycleId,
                                         String cycleType,
                                         int rank,
                                         int tokens,
                                         long createdAtMs) {
        this.id = id;
        this.cycleId = cycleId;
        this.cycleType = cycleType;
        this.rank = rank;
        this.tokens = tokens;
        this.createdAtMs = createdAtMs;
    }

    public String getId() {
        return id;
    }

    public String getCycleId() {
        return cycleId;
    }

    public String getCycleType() {
        return cycleType;
    }

    public int getRank() {
        return rank;
    }

    public int getTokens() {
        return tokens;
    }

    public long getCreatedAtMs() {
        return createdAtMs;
    }
}
