package com.example.slagalica.Services;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okio.ByteString;

public class WebSocketGameClient {

    public interface ListenerHandle {
        void remove();
    }

    public interface OnConnected {
        void onConnected();
        void onFailure(String errorMessage);
    }

    public interface OnRequestResult {
        void onSuccess(JSONObject data);
        void onFailure(String errorMessage);
    }

    public interface OnMatchmakingListener {
        void onQueueState(boolean queued, int queueSize);
        void onMatchFound(SessionSnapshot session);
        void onFailure(String errorMessage);
    }

    public interface OnSessionListener {
        void onSessionState(SessionSnapshot snapshot);
        void onFailure(String errorMessage);
    }

    private static WebSocketGameClient instance;

    private final OkHttpClient httpClient;
    private final Handler mainHandler;
    private final Map<String, OnRequestResult> pendingRequests;
    private final Map<String, CopyOnWriteArrayList<OnSessionListener>> sessionListeners;
    private final CopyOnWriteArrayList<OnMatchmakingListener> matchmakingListeners;
    private final Map<String, SessionSnapshot> lastSessionSnapshots;
    private final List<OnConnected> pendingConnectCallbacks;

    private WebSocket socket;
    private String currentUid;
    private String serverUrl;
    private boolean connecting;
    private boolean identified;

    private WebSocketGameClient() {
        httpClient = new OkHttpClient.Builder().build();
        mainHandler = new Handler(Looper.getMainLooper());
        pendingRequests = new ConcurrentHashMap<>();
        sessionListeners = new ConcurrentHashMap<>();
        matchmakingListeners = new CopyOnWriteArrayList<>();
        lastSessionSnapshots = new ConcurrentHashMap<>();
        pendingConnectCallbacks = new ArrayList<>();
        serverUrl = WebSocketConfig.getDefaultServerUrl();
    }

    public static synchronized WebSocketGameClient getInstance() {
        if (instance == null) {
            instance = new WebSocketGameClient();
        }
        return instance;
    }

    public synchronized void setServerUrl(String serverUrl) {
        if (serverUrl != null && !serverUrl.trim().isEmpty()) {
            this.serverUrl = serverUrl.trim();
        }
    }

    public synchronized void connect(String uid, OnConnected callback) {
        if (uid == null || uid.trim().isEmpty()) {
            if (callback != null) {
                post(() -> callback.onFailure("User id is required."));
            }
            return;
        }

        if (callback != null) {
            pendingConnectCallbacks.add(callback);
        }

        if (socket != null && identified && uid.equals(currentUid)) {
            dispatchConnected();
            return;
        }

        if (socket != null && currentUid != null && !currentUid.equals(uid)) {
            socket.close(1000, "Switching user");
            socket = null;
            identified = false;
            connecting = false;
        }

        currentUid = uid;
        if (connecting) {
            return;
        }

        connecting = true;
        identified = false;

        Request request = new Request.Builder()
                .url(serverUrl)
                .build();

        socket = httpClient.newWebSocket(request, new WebSocketListener() {
            @Override
            public void onOpen(@NonNull WebSocket webSocket, @NonNull Response response) {
                sendHello();
            }

            @Override
            public void onMessage(@NonNull WebSocket webSocket, @NonNull String text) {
                handleIncomingMessage(text);
            }

            @Override
            public void onMessage(@NonNull WebSocket webSocket, @NonNull ByteString bytes) {
                handleIncomingMessage(bytes.utf8());
            }

            @Override
            public void onClosing(@NonNull WebSocket webSocket, int code, @NonNull String reason) {
                webSocket.close(code, reason);
            }

            @Override
            public void onClosed(@NonNull WebSocket webSocket, int code, @NonNull String reason) {
                synchronized (WebSocketGameClient.this) {
                    if (socket == webSocket) {
                        socket = null;
                    }
                    connecting = false;
                    identified = false;
                }
            }

            @Override
            public void onFailure(@NonNull WebSocket webSocket, @NonNull Throwable t, Response response) {
                synchronized (WebSocketGameClient.this) {
                    if (socket == webSocket) {
                        socket = null;
                    }
                    connecting = false;
                    identified = false;
                }
                dispatchConnectFailure(t.getMessage() != null ? t.getMessage() : "WebSocket failure");
                dispatchListenerFailure(t.getMessage() != null ? t.getMessage() : "WebSocket failure");
            }
        });
    }

    public synchronized void disconnect() {
        if (socket != null) {
            socket.close(1000, "Client disconnect");
            socket = null;
        }
        connecting = false;
        identified = false;
    }

    public void joinRankedQueue(OnRequestResult callback) {
        request("join_ranked_queue", new JSONObject(), callback);
    }

    public void leaveRankedQueue(OnRequestResult callback) {
        request("leave_ranked_queue", new JSONObject(), callback);
    }

    public ListenerHandle addMatchmakingListener(OnMatchmakingListener listener) {
        matchmakingListeners.add(listener);
        return () -> matchmakingListeners.remove(listener);
    }

    public ListenerHandle subscribeSession(String sessionId, OnSessionListener listener) {
        sessionListeners.computeIfAbsent(sessionId, ignored -> new CopyOnWriteArrayList<>()).add(listener);

        SessionSnapshot snapshot = lastSessionSnapshots.get(sessionId);
        if (snapshot != null) {
            post(() -> listener.onSessionState(snapshot));
        }

        try {
            JSONObject payload = new JSONObject();
            payload.put("sessionId", sessionId);
            request("subscribe_session", payload, new NoOpRequestResult(listener));
        } catch (JSONException e) {
            post(() -> listener.onFailure(e.getMessage() != null ? e.getMessage() : "Failed to subscribe session."));
        }

        return () -> {
            CopyOnWriteArrayList<OnSessionListener> listeners = sessionListeners.get(sessionId);
            if (listeners != null) {
                listeners.remove(listener);
            }
        };
    }

    public void getSession(String sessionId, OnRequestResult callback) {
        try {
            JSONObject payload = new JSONObject();
            payload.put("sessionId", sessionId);
            request("get_session", payload, callback);
        } catch (JSONException e) {
            if (callback != null) {
                callback.onFailure(e.getMessage() != null ? e.getMessage() : "Failed to create request.");
            }
        }
    }

    public void abandonSession(String sessionId, OnRequestResult callback) {
        try {
            JSONObject payload = new JSONObject();
            payload.put("sessionId", sessionId);
            request("abandon_session", payload, callback);
        } catch (JSONException e) {
            if (callback != null) {
                callback.onFailure(e.getMessage() != null ? e.getMessage() : "Failed to create request.");
            }
        }
    }

    public void sendGameAction(String sessionId,
                               String actionType,
                               Long phaseVersion,
                               JSONObject data,
                               OnRequestResult callback) {
        try {
            JSONObject payload = new JSONObject();
            payload.put("sessionId", sessionId);
            payload.put("actionType", actionType);
            if (phaseVersion != null) {
                payload.put("phaseVersion", phaseVersion);
            }
            payload.put("data", data != null ? data : new JSONObject());
            request("game_action", payload, callback);
        } catch (JSONException e) {
            if (callback != null) {
                callback.onFailure(e.getMessage() != null ? e.getMessage() : "Failed to create game action.");
            }
        }
    }

    public synchronized void request(String type, JSONObject payload, OnRequestResult callback) {
        if (!identified && !"hello".equals(type)) {
            if (callback != null) {
                callback.onFailure("WebSocket is not connected yet.");
            }
            return;
        }

        if (socket == null) {
            if (callback != null) {
                callback.onFailure("WebSocket is not connected.");
            }
            return;
        }

        try {
            JSONObject envelope = new JSONObject();
            String requestId = UUID.randomUUID().toString();
            envelope.put("type", type);
            envelope.put("requestId", requestId);
            envelope.put("payload", payload != null ? payload : new JSONObject());
            if (callback != null) {
                pendingRequests.put(requestId, callback);
            }
            socket.send(envelope.toString());
        } catch (JSONException e) {
            if (callback != null) {
                callback.onFailure(e.getMessage() != null ? e.getMessage() : "Failed to build request.");
            }
        }
    }

    private void sendHello() {
        try {
            JSONObject payload = new JSONObject();
            payload.put("uid", currentUid);
            request("hello", payload, new OnRequestResult() {
                @Override
                public void onSuccess(JSONObject data) {
                    synchronized (WebSocketGameClient.this) {
                        connecting = false;
                        identified = true;
                    }
                    dispatchConnected();
                }

                @Override
                public void onFailure(String errorMessage) {
                    synchronized (WebSocketGameClient.this) {
                        connecting = false;
                        identified = false;
                    }
                    dispatchConnectFailure(errorMessage);
                }
            });
        } catch (JSONException e) {
            synchronized (this) {
                connecting = false;
                identified = false;
            }
            dispatchConnectFailure(e.getMessage() != null ? e.getMessage() : "Failed to send hello.");
        }
    }

    private void handleIncomingMessage(String text) {
        try {
            JSONObject message = new JSONObject(text);
            String type = message.optString("type", null);
            if (type == null) {
                return;
            }

            if ("response".equals(type)) {
                handleResponse(message);
                return;
            }

            JSONObject data = message.optJSONObject("data");
            if ("queue_state".equals(type)) {
                handleQueueState(data);
            } else if ("match_found".equals(type)) {
                handleMatchFound(data);
            } else if ("session_state".equals(type)) {
                handleSessionState(data);
            }
        } catch (JSONException e) {
            dispatchListenerFailure("Invalid server message.");
        }
    }

    private void handleResponse(JSONObject message) {
        String requestId = message.optString("requestId", null);
        if (requestId == null) {
            return;
        }

        OnRequestResult callback = pendingRequests.remove(requestId);
        if (callback == null) {
            return;
        }

        boolean ok = message.optBoolean("ok", false);
        if (ok) {
            JSONObject data = message.optJSONObject("data");
            post(() -> callback.onSuccess(data != null ? data : new JSONObject()));
            return;
        }

        String error = message.optString("error", "Request failed.");
        post(() -> callback.onFailure(error));
    }

    private void handleQueueState(JSONObject data) {
        boolean queued = data != null && data.optBoolean("queued", false);
        int queueSize = data != null ? data.optInt("queueSize", 0) : 0;
        for (OnMatchmakingListener listener : matchmakingListeners) {
            post(() -> listener.onQueueState(queued, queueSize));
        }
    }

    private void handleMatchFound(JSONObject data) {
        if (data == null) {
            return;
        }

        JSONObject sessionJson = data.optJSONObject("session");
        if (sessionJson == null) {
            return;
        }

        SessionSnapshot snapshot = new SessionSnapshot(sessionJson);
        String sessionId = snapshot.getString("sessionId");
        if (sessionId != null) {
            lastSessionSnapshots.put(sessionId, snapshot);
            notifySessionListeners(sessionId, snapshot);
        }

        for (OnMatchmakingListener listener : matchmakingListeners) {
            post(() -> listener.onMatchFound(snapshot));
        }
    }

    private void handleSessionState(JSONObject data) {
        if (data == null) {
            return;
        }

        SessionSnapshot snapshot = new SessionSnapshot(data);
        String sessionId = snapshot.getString("sessionId");
        if (sessionId == null) {
            return;
        }

        lastSessionSnapshots.put(sessionId, snapshot);
        notifySessionListeners(sessionId, snapshot);
    }

    private void notifySessionListeners(String sessionId, SessionSnapshot snapshot) {
        CopyOnWriteArrayList<OnSessionListener> listeners = sessionListeners.get(sessionId);
        if (listeners == null) {
            return;
        }

        for (OnSessionListener listener : listeners) {
            post(() -> listener.onSessionState(snapshot));
        }
    }

    private synchronized void dispatchConnected() {
        if (pendingConnectCallbacks.isEmpty()) {
            return;
        }

        List<OnConnected> callbacks = new ArrayList<>(pendingConnectCallbacks);
        pendingConnectCallbacks.clear();
        for (OnConnected callback : callbacks) {
            post(callback::onConnected);
        }
    }

    private synchronized void dispatchConnectFailure(String errorMessage) {
        if (pendingConnectCallbacks.isEmpty()) {
            return;
        }

        List<OnConnected> callbacks = new ArrayList<>(pendingConnectCallbacks);
        pendingConnectCallbacks.clear();
        for (OnConnected callback : callbacks) {
            post(() -> callback.onFailure(errorMessage));
        }
    }

    private void dispatchListenerFailure(String errorMessage) {
        for (OnMatchmakingListener listener : matchmakingListeners) {
            post(() -> listener.onFailure(errorMessage));
        }
        for (CopyOnWriteArrayList<OnSessionListener> listeners : sessionListeners.values()) {
            for (OnSessionListener listener : listeners) {
                post(() -> listener.onFailure(errorMessage));
            }
        }
    }

    private void post(Runnable runnable) {
        mainHandler.post(runnable);
    }

    private static final class NoOpRequestResult implements OnRequestResult {
        private final OnSessionListener listener;

        private NoOpRequestResult(OnSessionListener listener) {
            this.listener = listener;
        }

        @Override
        public void onSuccess(JSONObject data) {
        }

        @Override
        public void onFailure(String errorMessage) {
            listener.onFailure(errorMessage);
        }
    }
}
