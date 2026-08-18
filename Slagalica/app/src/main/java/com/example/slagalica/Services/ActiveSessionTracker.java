package com.example.slagalica.Services;

public final class ActiveSessionTracker {
    private static String activeSessionId;

    private ActiveSessionTracker() {
    }

    public static synchronized void markActiveSession(String sessionId) {
        if (sessionId == null || sessionId.trim().isEmpty()) {
            return;
        }
        activeSessionId = sessionId;
    }

    public static synchronized String getActiveSessionId() {
        return activeSessionId;
    }

    public static synchronized void clearActiveSession(String sessionId) {
        if (sessionId == null || sessionId.equals(activeSessionId)) {
            activeSessionId = null;
        }
    }
}
