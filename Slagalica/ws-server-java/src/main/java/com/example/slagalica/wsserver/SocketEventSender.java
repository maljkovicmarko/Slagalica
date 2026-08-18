package com.example.slagalica.wsserver;

import org.java_websocket.WebSocket;
import org.json.JSONObject;

public class SocketEventSender {
    private final ConnectionRegistry connectionRegistry;

    public SocketEventSender(ConnectionRegistry connectionRegistry) {
        this.connectionRegistry = connectionRegistry;
    }

    public void reply(WebSocket connection, String requestId, boolean ok, JSONObject data, String errorMessage) {
        JSONObject envelope = new JSONObject();
        envelope.put("type", "response");
        envelope.put("requestId", requestId == null ? JSONObject.NULL : requestId);
        envelope.put("ok", ok);
        if (data != null) {
            envelope.put("data", data);
        }
        if (errorMessage != null) {
            envelope.put("error", errorMessage);
        }
        send(connection, envelope);
    }

    public void sendEventTo(String uid, String type, JSONObject data) {
        WebSocket connection = connectionRegistry.getSocket(uid);
        if (connection == null) {
            return;
        }

        JSONObject envelope = new JSONObject();
        envelope.put("type", type);
        envelope.put("data", data != null ? data : new JSONObject());
        send(connection, envelope);
    }

    public void send(WebSocket connection, JSONObject envelope) {
        if (connection != null && connection.isOpen()) {
            connection.send(envelope.toString());
        }
    }
}
