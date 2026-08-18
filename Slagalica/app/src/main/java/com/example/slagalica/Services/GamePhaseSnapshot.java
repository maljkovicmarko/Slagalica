package com.example.slagalica.Services;

import org.json.JSONObject;

public class GamePhaseSnapshot {
    private final String phaseType;
    private final long phaseVersion;
    private final long phaseStartedAtMs;
    private final long phaseDeadlineAtMs;
    private final int roundNumber;

    public GamePhaseSnapshot(JSONObject data) {
        JSONObject safeData = data != null ? data : new JSONObject();
        phaseType = safeData.optString("phaseType", null);
        phaseVersion = safeData.optLong("phaseVersion", 0L);
        phaseStartedAtMs = safeData.optLong("phaseStartedAtMs", 0L);
        phaseDeadlineAtMs = safeData.optLong("phaseDeadlineAtMs", 0L);
        roundNumber = safeData.optInt("roundNumber", 0);
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
        return Math.max(0L, phaseDeadlineAtMs - nowMs);
    }

    public boolean isExpired(long nowMs) {
        return getRemainingMs(nowMs) == 0L;
    }
}
