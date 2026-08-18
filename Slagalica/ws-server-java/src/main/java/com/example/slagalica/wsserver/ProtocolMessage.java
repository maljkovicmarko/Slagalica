package com.example.slagalica.wsserver;

import org.json.JSONException;
import org.json.JSONObject;

public final class ProtocolMessage {
    private final String type;
    private final String requestId;
    private final JSONObject payload;

    private ProtocolMessage(String type, String requestId, JSONObject payload) {
        this.type = type;
        this.requestId = requestId;
        this.payload = payload;
    }

    public static ProtocolMessage parse(String rawMessage) throws JSONException {
        JSONObject envelope = new JSONObject(rawMessage);
        String type = envelope.optString("type", null);
        String requestId = envelope.optString("requestId", null);
        JSONObject payload = envelope.optJSONObject("payload");
        if (payload == null) {
            payload = new JSONObject();
        }
        return new ProtocolMessage(type, requestId, payload);
    }

    public String getType() {
        return type;
    }

    public String getRequestId() {
        return requestId;
    }

    public JSONObject getPayload() {
        return payload;
    }
}
