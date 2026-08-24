package com.example.slagalica.wsserver;

import org.java_websocket.WebSocket;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ConnectionRegistry {
    private final Map<String, WebSocket> socketsByUid = new ConcurrentHashMap<>();
    private final Map<String, Boolean> appForegroundByUid = new ConcurrentHashMap<>();

    public WebSocket register(String uid, WebSocket connection) {
        if (uid == null || connection == null) {
            return null;
        }
        connection.setAttachment(uid);
        appForegroundByUid.put(uid, false);
        return socketsByUid.put(uid, connection);
    }

    public WebSocket getSocket(String uid) {
        return uid == null ? null : socketsByUid.get(uid);
    }

    public boolean isConnected(String uid) {
        WebSocket socket = getSocket(uid);
        return socket != null && socket.isOpen();
    }

    public void setAppForeground(String uid, boolean foreground) {
        if (uid != null && socketsByUid.containsKey(uid)) {
            appForegroundByUid.put(uid, foreground);
        }
    }

    public boolean isAppForeground(String uid) {
        return isConnected(uid) && Boolean.TRUE.equals(appForegroundByUid.get(uid));
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
            appForegroundByUid.remove(uid);
        }
    }
}
