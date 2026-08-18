package com.example.slagalica.Services;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class TurnSnapshot {
    private final String roundOwnerUid;
    private final String activePlayerUid;
    private final Set<String> allowedActorUids;

    public TurnSnapshot(JSONObject data) {
        JSONObject safeData = data != null ? data : new JSONObject();
        roundOwnerUid = safeData.optString("roundOwnerUid", null);
        activePlayerUid = safeData.optString("activePlayerUid", null);
        allowedActorUids = parseAllowedActorUids(safeData.optJSONArray("allowedActorUids"));
    }

    public String getRoundOwnerUid() {
        return roundOwnerUid;
    }

    public String getActivePlayerUid() {
        return activePlayerUid;
    }

    public Set<String> getAllowedActorUids() {
        return Collections.unmodifiableSet(allowedActorUids);
    }

    public boolean canAct(String uid) {
        return uid != null && allowedActorUids.contains(uid);
    }

    private Set<String> parseAllowedActorUids(JSONArray allowedActorUidsJson) {
        Set<String> parsedAllowedActorUids = new HashSet<>();
        if (allowedActorUidsJson == null) {
            return parsedAllowedActorUids;
        }

        for (int i = 0; i < allowedActorUidsJson.length(); i++) {
            String uid = allowedActorUidsJson.optString(i, null);
            if (uid != null && !uid.isBlank()) {
                parsedAllowedActorUids.add(uid);
            }
        }
        return parsedAllowedActorUids;
    }
}
