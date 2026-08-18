package com.example.slagalica.wsserver;

import org.json.JSONObject;

public class GameAction {
    private final String sessionId;
    private final String actionType;
    private final Long phaseVersion;
    private final JSONObject data;

    public GameAction(String sessionId, String actionType, Long phaseVersion, JSONObject data) {
        this.sessionId = sessionId;
        this.actionType = actionType;
        this.phaseVersion = phaseVersion;
        this.data = data == null ? new JSONObject() : data;
    }

    public static GameAction fromJson(JSONObject payload) {
        if (payload == null) {
            return new GameAction(null, null, null, new JSONObject());
        }

        Long phaseVersion = null;
        if (payload.has("phaseVersion") && !payload.isNull("phaseVersion")) {
            phaseVersion = payload.optLong("phaseVersion");
        }

        return new GameAction(
                payload.optString("sessionId", null),
                payload.optString("actionType", null),
                phaseVersion,
                payload.optJSONObject("data")
        );
    }

    public String getSessionId() {
        return sessionId;
    }

    public String getActionType() {
        return actionType;
    }

    public Long getPhaseVersion() {
        return phaseVersion;
    }

    public JSONObject getData() {
        return data;
    }
}
