package com.example.slagalica.wsserver;

import org.json.JSONObject;

public class GameActionResult {
    private final boolean accepted;
    private final boolean sessionChanged;
    private final String errorMessage;

    private GameActionResult(boolean accepted, boolean sessionChanged, String errorMessage) {
        this.accepted = accepted;
        this.sessionChanged = sessionChanged;
        this.errorMessage = errorMessage;
    }

    public static GameActionResult accepted(boolean sessionChanged) {
        return new GameActionResult(true, sessionChanged, null);
    }

    public static GameActionResult rejected(String errorMessage) {
        return new GameActionResult(false, false, errorMessage);
    }

    public boolean isAccepted() {
        return accepted;
    }

    public boolean isSessionChanged() {
        return sessionChanged;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public JSONObject toJson(SessionState session) {
        JSONObject json = new JSONObject();
        json.put("accepted", accepted);
        json.put("sessionChanged", sessionChanged);
        if (session != null) {
            json.put("sessionId", session.getSessionId());
        }
        return json;
    }
}
