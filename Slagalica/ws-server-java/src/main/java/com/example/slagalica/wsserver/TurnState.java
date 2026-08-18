package com.example.slagalica.wsserver;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class TurnState {
    private final Set<String> allowedActorUids;

    private String roundOwnerUid;
    private String activePlayerUid;

    public TurnState(String roundOwnerUid, String activePlayerUid, Set<String> allowedActorUids) {
        this.roundOwnerUid = roundOwnerUid;
        this.activePlayerUid = activePlayerUid;
        this.allowedActorUids = allowedActorUids == null
                ? new HashSet<>()
                : new HashSet<>(allowedActorUids);
    }

    public static TurnState nobody(String roundOwnerUid) {
        return new TurnState(roundOwnerUid, null, Collections.emptySet());
    }

    public static TurnState single(String roundOwnerUid, String activePlayerUid) {
        Set<String> allowedActorUids = new HashSet<>();
        if (activePlayerUid != null) {
            allowedActorUids.add(activePlayerUid);
        }
        return new TurnState(roundOwnerUid, activePlayerUid, allowedActorUids);
    }

    public static TurnState both(String roundOwnerUid, String activePlayerUid, String player1Uid, String player2Uid) {
        Set<String> allowedActorUids = new HashSet<>();
        if (player1Uid != null) {
            allowedActorUids.add(player1Uid);
        }
        if (player2Uid != null) {
            allowedActorUids.add(player2Uid);
        }
        return new TurnState(roundOwnerUid, activePlayerUid, allowedActorUids);
    }

    public String getRoundOwnerUid() {
        return roundOwnerUid;
    }

    public void setRoundOwnerUid(String roundOwnerUid) {
        this.roundOwnerUid = roundOwnerUid;
    }

    public String getActivePlayerUid() {
        return activePlayerUid;
    }

    public void setActivePlayerUid(String activePlayerUid) {
        this.activePlayerUid = activePlayerUid;
    }

    public Set<String> getAllowedActorUids() {
        return Collections.unmodifiableSet(allowedActorUids);
    }

    public boolean canAct(String uid) {
        return uid != null && allowedActorUids.contains(uid);
    }

    public JSONObject toJson() {
        JSONObject json = new JSONObject();
        json.put("roundOwnerUid", roundOwnerUid == null ? JSONObject.NULL : roundOwnerUid);
        json.put("activePlayerUid", activePlayerUid == null ? JSONObject.NULL : activePlayerUid);

        JSONArray allowedActorsJson = new JSONArray();
        for (String allowedActorUid : allowedActorUids) {
            allowedActorsJson.put(allowedActorUid);
        }
        json.put("allowedActorUids", allowedActorsJson);

        return json;
    }
}
