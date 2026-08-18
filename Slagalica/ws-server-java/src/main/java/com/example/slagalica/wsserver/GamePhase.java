package com.example.slagalica.wsserver;

import org.json.JSONObject;

public class GamePhase {
    private final String phaseType;
    private final long phaseVersion;
    private final long phaseStartedAtMs;
    private final long phaseDeadlineAtMs;
    private final int roundNumber;

    public GamePhase(String phaseType,
                     long phaseVersion,
                     long phaseStartedAtMs,
                     long phaseDeadlineAtMs,
                     int roundNumber) {
        this.phaseType = phaseType;
        this.phaseVersion = phaseVersion;
        this.phaseStartedAtMs = phaseStartedAtMs;
        this.phaseDeadlineAtMs = phaseDeadlineAtMs;
        this.roundNumber = roundNumber;
    }

    public static GamePhase startingNow(String phaseType, long phaseVersion, int roundNumber, long durationMs) {
        long nowMs = System.currentTimeMillis();
        return new GamePhase(phaseType, phaseVersion, nowMs, nowMs + durationMs, roundNumber);
    }

    public static GamePhase startingAt(String phaseType,
                                       long phaseVersion,
                                       int roundNumber,
                                       long startedAtMs,
                                       long durationMs) {
        return new GamePhase(phaseType, phaseVersion, startedAtMs, startedAtMs + durationMs, roundNumber);
    }

    public String getPhaseType() {
        return phaseType;
    }

    public long getPhaseVersion() {
        return phaseVersion;
    }

    public long getPhaseStartedAtMs() {
        return phaseStartedAtMs;
    }

    public long getPhaseDeadlineAtMs() {
        return phaseDeadlineAtMs;
    }

    public int getRoundNumber() {
        return roundNumber;
    }

    public long getRemainingMs(long nowMs) {
        return Math.max(0, phaseDeadlineAtMs - nowMs);
    }

    public boolean isExpired(long nowMs) {
        return getRemainingMs(nowMs) == 0;
    }

    public JSONObject toJson() {
        JSONObject json = new JSONObject();
        json.put("phaseType", phaseType);
        json.put("phaseVersion", phaseVersion);
        json.put("phaseStartedAtMs", phaseStartedAtMs);
        json.put("phaseDeadlineAtMs", phaseDeadlineAtMs);
        json.put("roundNumber", roundNumber);
        return json;
    }
}
