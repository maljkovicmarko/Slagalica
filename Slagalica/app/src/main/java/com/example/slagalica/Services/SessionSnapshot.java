package com.example.slagalica.Services;

import org.json.JSONArray;
import org.json.JSONObject;

public class SessionSnapshot {

    private final JSONObject data;

    public SessionSnapshot(JSONObject data) {
        this.data = data != null ? data : new JSONObject();
    }

    public boolean has(String field) {
        return data.has(field) && !data.isNull(field);
    }

    public String getString(String field) {
        if (!has(field)) {
            return null;
        }
        return data.optString(field, null);
    }

    public Long getLong(String field) {
        if (!has(field)) {
            return null;
        }
        return data.optLong(field);
    }

    public Integer getInt(String field) {
        if (!has(field)) {
            return null;
        }
        return data.optInt(field);
    }

    public Boolean getBoolean(String field) {
        if (!has(field)) {
            return null;
        }
        return data.optBoolean(field);
    }

    public JSONObject getObject(String field) {
        if (!has(field)) {
            return null;
        }
        return data.optJSONObject(field);
    }

    public JSONArray getArray(String field) {
        if (!has(field)) {
            return null;
        }
        return data.optJSONArray(field);
    }

    public TurnSnapshot getTurnState() {
        JSONObject turnStateJson = getObject("turnState");
        if (turnStateJson != null) {
            return new TurnSnapshot(turnStateJson);
        }

        String currentTurnUid = getString("currentTurnUid");
        if (currentTurnUid == null) {
            return null;
        }

        try {
            JSONObject legacyTurnStateJson = new JSONObject();
            legacyTurnStateJson.put("roundOwnerUid", currentTurnUid);
            legacyTurnStateJson.put("activePlayerUid", currentTurnUid);

            JSONArray allowedActorUidsJson = new JSONArray();
            allowedActorUidsJson.put(currentTurnUid);
            legacyTurnStateJson.put("allowedActorUids", allowedActorUidsJson);

            return new TurnSnapshot(legacyTurnStateJson);
        } catch (Exception ignored) {
            return null;
        }
    }

    public GamePhaseSnapshot getGamePhase() {
        JSONObject gamePhaseJson = getObject("gamePhase");
        if (gamePhaseJson == null) {
            return null;
        }
        return new GamePhaseSnapshot(gamePhaseJson);
    }

    public JSONObject toJson() {
        try {
            return new JSONObject(data.toString());
        } catch (Exception ignored) {
            return new JSONObject();
        }
    }
}
