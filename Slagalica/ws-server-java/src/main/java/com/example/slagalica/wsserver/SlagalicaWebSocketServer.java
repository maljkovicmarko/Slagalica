package com.example.slagalica.wsserver;

import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;
import org.json.JSONException;
import org.json.JSONObject;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class SlagalicaWebSocketServer extends WebSocketServer {

    private static final String SESSION_TYPE_RANKED = "ranked";

    private final ConnectionRegistry connectionRegistry;
    private final SocketEventSender eventSender;
    private final SessionService sessionService;
    private final MatchmakingService matchmakingService;
    private final CountDownLatch startupLatch;

    private volatile boolean started;
    private volatile Throwable startupFailure;

    public SlagalicaWebSocketServer(InetSocketAddress address) {
        super(address);
        this.connectionRegistry = new ConnectionRegistry();
        this.eventSender = new SocketEventSender(connectionRegistry);
        this.sessionService = new SessionService(this::emitSessionState);
        this.matchmakingService = new MatchmakingService(sessionService, SESSION_TYPE_RANKED);
        this.startupLatch = new CountDownLatch(1);
    }

    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake) {
    }

    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        unregisterConnection(conn);
    }

    @Override
    public void onMessage(WebSocket conn, String message) {
        try {
            ProtocolMessage protocolMessage = ProtocolMessage.parse(message);
            String type = protocolMessage.getType();
            if (type == null || type.isBlank()) {
                eventSender.reply(conn, protocolMessage.getRequestId(), false, null, "Message type is required.");
                return;
            }

            switch (type) {
                case "hello":
                    handleHello(conn, protocolMessage.getRequestId(), protocolMessage.getPayload());
                    break;
                case "join_ranked_queue":
                    handleJoinRankedQueue(conn, protocolMessage.getRequestId());
                    break;
                case "leave_ranked_queue":
                    handleLeaveRankedQueue(conn, protocolMessage.getRequestId());
                    break;
                case "subscribe_session":
                    handleSubscribeSession(conn, protocolMessage.getRequestId(), protocolMessage.getPayload());
                    break;
                case "unsubscribe_session":
                    handleUnsubscribeSession(conn, protocolMessage.getRequestId(), protocolMessage.getPayload());
                    break;
                case "get_session":
                    handleGetSession(conn, protocolMessage.getRequestId(), protocolMessage.getPayload());
                    break;
                case "abandon_session":
                    handleAbandonSession(conn, protocolMessage.getRequestId(), protocolMessage.getPayload());
                    break;
                case "game_action":
                    handleGameAction(conn, protocolMessage.getRequestId(), protocolMessage.getPayload());
                    break;
                default:
                    eventSender.reply(conn, protocolMessage.getRequestId(), false, null, "Unknown message type: " + type);
                    break;
            }
        } catch (JSONException e) {
            eventSender.reply(conn, null, false, null, "Invalid JSON message.");
        }
    }

    @Override
    public void onError(WebSocket conn, Exception ex) {
        if (conn == null && !started && startupFailure == null) {
            startupFailure = ex;
            startupLatch.countDown();
        }
        System.err.println("WebSocket server error: " + ex.getMessage());
        ex.printStackTrace(System.err);
        if (conn != null) {
            unregisterConnection(conn);
        }
    }

    @Override
    public void onStart() {
        started = true;
        startupLatch.countDown();
        System.out.println("Slagalica websocket server listening on ws://0.0.0.0:" + getPort());
    }

    public void awaitStartup(long timeoutMs) throws InterruptedException {
        if (!startupLatch.await(timeoutMs, TimeUnit.MILLISECONDS)) {
            throw new IllegalStateException(
                    "WebSocket server did not finish startup within " + timeoutMs + " ms."
            );
        }
        if (startupFailure != null) {
            throw new IllegalStateException("WebSocket server failed to start.", startupFailure);
        }
        if (!started) {
            throw new IllegalStateException("WebSocket server stopped before completing startup.");
        }
    }

    private void handleHello(WebSocket conn, String requestId, JSONObject payload) {
        String uid = payload.optString("uid", null);
        if (uid == null || uid.isBlank()) {
            eventSender.reply(conn, requestId, false, null, "User id is required.");
            return;
        }

        WebSocket previous = connectionRegistry.register(uid, conn);
        if (previous != null && previous != conn && previous.isOpen()) {
            previous.close(4001, "Replaced by a newer connection");
        }

        List<SessionState> connectedSessions = sessionService.markUserConnected(uid);
        for (SessionState session : connectedSessions) {
            emitSessionState(session);
        }

        JSONObject data = new JSONObject();
        data.put("uid", uid);
        data.put("activeSessionIds", sessionService.getActiveSessionIds(uid));
        eventSender.reply(conn, requestId, true, data, null);
    }

    private void handleJoinRankedQueue(WebSocket conn, String requestId) {
        String uid = requireUid(conn, requestId);
        if (uid == null) {
            return;
        }

        MatchmakingService.JoinRankedQueueResult result = matchmakingService.joinRankedQueue(uid);
        if (!result.isAccepted()) {
            JSONObject data = new JSONObject();
            data.put("queued", false);
            data.put("matched", false);
            data.put("queueSize", result.getQueueSize());
            eventSender.reply(conn, requestId, false, data, result.getErrorMessage());
            emitQueueState(uid, false, result.getQueueSize());
            return;
        }

        if (result.isMatched()) {
            emitMatchFound(result.getMatchedSession());
            JSONObject data = new JSONObject();
            data.put("queued", false);
            data.put("matched", true);
            data.put("sessionId", result.getMatchedSession().getSessionId());
            eventSender.reply(conn, requestId, true, data, null);
            return;
        }

        JSONObject data = new JSONObject();
        data.put("queued", true);
        data.put("matched", false);
        data.put("queueSize", result.getQueueSize());
        eventSender.reply(conn, requestId, true, data, null);
        emitQueueState(uid, true, result.getQueueSize());
    }

    private void handleLeaveRankedQueue(WebSocket conn, String requestId) {
        String uid = requireUid(conn, requestId);
        if (uid == null) {
            return;
        }

        MatchmakingService.LeaveRankedQueueResult result = matchmakingService.leaveRankedQueue(uid);
        JSONObject data = new JSONObject();
        data.put("queued", false);
        data.put("removed", result.wasRemoved());
        data.put("queueSize", result.getQueueSize());
        eventSender.reply(conn, requestId, true, data, null);
        emitQueueState(uid, false, result.getQueueSize());
    }

    private void handleSubscribeSession(WebSocket conn, String requestId, JSONObject payload) {
        String uid = requireUid(conn, requestId);
        if (uid == null) {
            return;
        }

        SessionState session = requireAuthorizedSession(conn, requestId, uid, payload.optString("sessionId", null));
        if (session == null) {
            return;
        }

        sessionService.subscribeUser(uid, session);
        JSONObject data = new JSONObject();
        data.put("sessionId", session.getSessionId());
        eventSender.reply(conn, requestId, true, data, null);
        emitSessionStateTo(uid, session);
    }

    private void handleUnsubscribeSession(WebSocket conn, String requestId, JSONObject payload) {
        String uid = requireUid(conn, requestId);
        if (uid == null) {
            return;
        }

        String sessionId = payload.optString("sessionId", null);
        sessionService.unsubscribeUser(uid, sessionId);

        JSONObject data = new JSONObject();
        data.put("sessionId", sessionId == null ? JSONObject.NULL : sessionId);
        eventSender.reply(conn, requestId, true, data, null);
    }

    private void handleGetSession(WebSocket conn, String requestId, JSONObject payload) {
        String uid = requireUid(conn, requestId);
        if (uid == null) {
            return;
        }

        SessionState session = requireAuthorizedSession(conn, requestId, uid, payload.optString("sessionId", null));
        if (session == null) {
            return;
        }

        JSONObject data = new JSONObject();
        data.put("session", session.toJson());
        eventSender.reply(conn, requestId, true, data, null);
    }

    private void handleAbandonSession(WebSocket conn, String requestId, JSONObject payload) {
        String uid = requireUid(conn, requestId);
        if (uid == null) {
            return;
        }

        SessionState session = requireAuthorizedSession(conn, requestId, uid, payload.optString("sessionId", null));
        if (session == null) {
            return;
        }

        sessionService.abandonSession(uid, session);
        emitSessionState(session);

        JSONObject data = new JSONObject();
        data.put("sessionId", session.getSessionId());
        data.put("winnerUid", session.getWinnerUid() == null ? JSONObject.NULL : session.getWinnerUid());
        eventSender.reply(conn, requestId, true, data, null);
    }

    private void handleGameAction(WebSocket conn, String requestId, JSONObject payload) {
        String uid = requireUid(conn, requestId);
        if (uid == null) {
            return;
        }

        GameAction action = GameAction.fromJson(payload);
        SessionService.GameActionProcessingResult result = sessionService.handleGameAction(uid, action);
        GameActionResult actionResult = result.getActionResult();
        SessionState session = result.getSession();

        if (!actionResult.isAccepted()) {
            eventSender.reply(conn, requestId, false, null, actionResult.getErrorMessage());
            return;
        }

        eventSender.reply(conn, requestId, true, actionResult.toJson(session), null);
        if (session != null && actionResult.isSessionChanged()) {
            emitSessionState(session);
        }
    }

    private SessionState requireAuthorizedSession(WebSocket conn, String requestId, String uid, String sessionId) {
        SessionState session = requireSession(conn, requestId, sessionId);
        if (session == null) {
            return null;
        }
        if (!session.containsPlayer(uid)) {
            eventSender.reply(conn, requestId, false, null, "User is not part of this session.");
            return null;
        }
        return session;
    }

    private SessionState requireSession(WebSocket conn, String requestId, String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            eventSender.reply(conn, requestId, false, null, "Session id is required.");
            return null;
        }

        SessionState session = sessionService.requireSession(sessionId);
        if (session == null) {
            eventSender.reply(conn, requestId, false, null, "Session does not exist.");
            return null;
        }
        return session;
    }

    private String requireUid(WebSocket conn, String requestId) {
        String uid = connectionRegistry.getUid(conn);
        if (uid == null || uid.isBlank()) {
            eventSender.reply(conn, requestId, false, null, "Call hello before sending commands.");
            return null;
        }
        return uid;
    }

    private void unregisterConnection(WebSocket conn) {
        String uid = connectionRegistry.getUid(conn);
        connectionRegistry.removeIfCurrent(conn);
        if (uid == null) {
            return;
        }

        matchmakingService.removeUser(uid);
        List<SessionState> disconnectedSessions = sessionService.markUserDisconnected(uid);
        for (SessionState session : disconnectedSessions) {
            emitSessionState(session);
        }
    }

    private void emitMatchFound(SessionState session) {
        JSONObject data = new JSONObject();
        data.put("sessionId", session.getSessionId());
        data.put("session", session.toJson());
        eventSender.sendEventTo(session.getPlayer1Uid(), "match_found", data);
        eventSender.sendEventTo(session.getPlayer2Uid(), "match_found", data);
        emitSessionState(session);
    }

    private void emitQueueState(String uid, boolean queued, int queueSize) {
        JSONObject data = new JSONObject();
        data.put("queued", queued);
        data.put("queueSize", queueSize);
        eventSender.sendEventTo(uid, "queue_state", data);
    }

    private void emitSessionState(SessionState session) {
        JSONObject data = session.toJson();
        for (String subscriberUid : sessionService.getSubscribers(session.getSessionId())) {
            eventSender.sendEventTo(subscriberUid, "session_state", data);
        }
    }

    private void emitSessionStateTo(String uid, SessionState session) {
        eventSender.sendEventTo(uid, "session_state", session.toJson());
    }
}
