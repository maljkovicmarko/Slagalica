package com.example.slagalica.wsserver;

import org.java_websocket.WebSocket;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ConnectionRegistry {
    private final Map<String, WebSocket> socketsByUid = new ConcurrentHashMap<>();

    public WebSocket register(String uid, WebSocket connection) {
        if (uid == null || connection == null) {
            return null;
        }
        connection.setAttachment(uid);
        return socketsByUid.put(uid, connection);
    }

    public WebSocket getSocket(String uid) {
        return uid == null ? null : socketsByUid.get(uid);
    }

    public String getUid(WebSocket connection) {
        return connection == null ? null : connection.getAttachment();
    }

    public void removeIfCurrent(WebSocket connection) {
        String uid = getUid(connection);
        if (uid == null) {
            return;
        }

        WebSocket current = socketsByUid.get(uid);
        if (current == connection) {
            socketsByUid.remove(uid);
        }
    }
}
