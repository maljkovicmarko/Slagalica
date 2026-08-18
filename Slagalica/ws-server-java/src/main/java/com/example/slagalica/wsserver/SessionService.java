package com.example.slagalica.wsserver;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class SessionService {
    private final Map<String, SessionState> sessionsById = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> subscribedUsersBySessionId = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> sessionIdsByUser = new ConcurrentHashMap<>();
    private final GeneralKnowledgeQuestionProvider generalKnowledgeQuestionProvider;
    private final RankedPlayerProgressService rankedPlayerProgressService;
    private final GameEngineRegistry gameEngineRegistry;
    private final MatchEngine matchEngine;
    private final ScheduledExecutorService phaseTimeoutExecutor;
    private final SessionChangeListener sessionChangeListener;

    public SessionService() {
        this(null);
    }

    public SessionService(SessionChangeListener sessionChangeListener) {
        generalKnowledgeQuestionProvider = new GeneralKnowledgeQuestionProvider();
        rankedPlayerProgressService = new RankedPlayerProgressService();
        gameEngineRegistry = new GameEngineRegistry();
        matchEngine = new MatchEngine();
        phaseTimeoutExecutor = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "slagalica-phase-timeouts");
            thread.setDaemon(true);
            return thread;
        });
        this.sessionChangeListener = sessionChangeListener;
    }

    public List<SessionState> markUserConnected(String uid) {
        List<SessionState> affectedSessions = new ArrayList<>();
        for (String sessionId : sessionIdsByUser.getOrDefault(uid, Collections.emptySet())) {
            SessionState session = sessionsById.get(sessionId);
            if (session == null) {
                continue;
            }
            session.markConnected(uid, true);
            affectedSessions.add(session);
        }
        return affectedSessions;
    }

    public List<String> getActiveSessionIds(String uid) {
        return new ArrayList<>(sessionIdsByUser.getOrDefault(uid, Collections.emptySet()));
    }

    public SessionState createSession(String player1Uid, String player2Uid, String sessionType) {
        String sessionId = UUID.randomUUID().toString();
        SessionState session = new SessionState(
                sessionId,
                sessionType,
                player1Uid,
                player2Uid,
                System.currentTimeMillis(),
                new GeneralKnowledgeGameState(generalKnowledgeQuestionProvider.selectQuestions())
        );
        sessionsById.put(sessionId, session);
        sessionIdsByUser.computeIfAbsent(player1Uid, ignored -> Collections.synchronizedSet(new HashSet<>())).add(sessionId);
        sessionIdsByUser.computeIfAbsent(player2Uid, ignored -> Collections.synchronizedSet(new HashSet<>())).add(sessionId);
        addSubscriber(sessionId, player1Uid);
        addSubscriber(sessionId, player2Uid);
        scheduleCurrentPhaseTimeout(session);
        return session;
    }

    public RankedPlayerProgressService.TokenCheckResult canJoinRankedQueue(String uid) {
        return rankedPlayerProgressService.canJoinRankedQueue(uid);
    }

    public RankedPlayerProgressService.TokenSpendResult spendRankedTokens(String player1Uid, String player2Uid) {
        return rankedPlayerProgressService.spendRankedTokens(player1Uid, player2Uid);
    }

    public SessionState requireSession(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return null;
        }
        return sessionsById.get(sessionId);
    }

    public boolean subscribeUser(String uid, SessionState session) {
        if (session == null || !session.containsPlayer(uid)) {
            return false;
        }
        addSubscriber(session.getSessionId(), uid);
        return true;
    }

    public void unsubscribeUser(String uid, String sessionId) {
        Set<String> subscribers = subscribedUsersBySessionId.get(sessionId);
        if (subscribers != null) {
            subscribers.remove(uid);
        }
    }

    public SessionState abandonSession(String uid, SessionState session) {
        if (session == null || !session.containsPlayer(uid)) {
            return null;
        }

        synchronized (session) {
            if (!"active".equals(session.getStatus())) {
                return session;
            }

            session.setAbandonedByUid(uid);
            session.setWinnerUid(session.otherPlayer(uid));
            session.markConnected(uid, false);

            GameEngine engine = gameEngineRegistry.getEngine(session.getCurrentGame());
            if (engine != null) {
                GameActionResult result = engine.handlePlayerAbandoned(session, uid);
                if (result.isAccepted() && result.isSessionChanged()) {
                    matchEngine.advanceIfNeeded(session);
                }
            }
            normalizeAbandonedPlayerProgress(session);
            applyRankedRewardsIfFinished(session);
            scheduleCurrentPhaseTimeout(session);
        }
        return session;
    }

    public GameActionProcessingResult handleGameAction(String uid, GameAction action) {
        if (action == null) {
            return GameActionProcessingResult.rejected(null, "Game action is required.");
        }

        SessionState session = requireSession(action.getSessionId());
        if (session == null) {
            return GameActionProcessingResult.rejected(null, "Session does not exist.");
        }

        synchronized (session) {
            GameActionResult validationResult = validateGameAction(uid, action, session);
            if (!validationResult.isAccepted()) {
                return GameActionProcessingResult.rejected(session, validationResult.getErrorMessage());
            }

            GameEngine engine = gameEngineRegistry.getEngine(session.getCurrentGame());
            if (engine == null) {
                return GameActionProcessingResult.rejected(session, "No engine registered for current game.");
            }

            GameActionResult result = engine.handleAction(session, uid, action);
            if (result.isAccepted() && result.isSessionChanged()) {
                matchEngine.advanceIfNeeded(session);
                normalizeAbandonedPlayerProgress(session);
                applyRankedRewardsIfFinished(session);
                scheduleCurrentPhaseTimeout(session);
            }
            return new GameActionProcessingResult(session, result);
        }
    }

    public List<SessionState> markUserDisconnected(String uid) {
        List<SessionState> affectedSessions = new ArrayList<>();
        for (String sessionId : sessionIdsByUser.getOrDefault(uid, Collections.emptySet())) {
            SessionState session = sessionsById.get(sessionId);
            if (session == null) {
                continue;
            }
            session.markConnected(uid, false);
            affectedSessions.add(session);
        }
        return affectedSessions;
    }

    public Set<String> getSubscribers(String sessionId) {
        Set<String> subscribers = subscribedUsersBySessionId.get(sessionId);
        if (subscribers == null) {
            return Collections.emptySet();
        }
        return new HashSet<>(subscribers);
    }

    private void addSubscriber(String sessionId, String uid) {
        subscribedUsersBySessionId
                .computeIfAbsent(sessionId, ignored -> Collections.synchronizedSet(new HashSet<>()))
                .add(uid);
    }

    private void scheduleCurrentPhaseTimeout(SessionState session) {
        if (session == null || !"active".equals(session.getStatus()) || session.getGamePhase() == null) {
            return;
        }

        GamePhase phase = session.getGamePhase();
        long delayMs = phase.getRemainingMs(System.currentTimeMillis());
        phaseTimeoutExecutor.schedule(
                () -> handleScheduledPhaseTimeout(session.getSessionId(), phase.getPhaseVersion()),
                delayMs,
                TimeUnit.MILLISECONDS
        );
    }

    private void handleScheduledPhaseTimeout(String sessionId, long phaseVersion) {
        SessionState session = requireSession(sessionId);
        if (session == null) {
            return;
        }

        GameActionResult result;
        synchronized (session) {
            if (!"active".equals(session.getStatus())) {
                return;
            }

            GamePhase currentPhase = session.getGamePhase();
            if (currentPhase == null || currentPhase.getPhaseVersion() != phaseVersion) {
                return;
            }

            GameEngine engine = gameEngineRegistry.getEngine(session.getCurrentGame());
            if (engine == null) {
                return;
            }

            result = engine.handleTimeout(session, phaseVersion);
            if (result.isAccepted() && result.isSessionChanged()) {
                matchEngine.advanceIfNeeded(session);
                normalizeAbandonedPlayerProgress(session);
                applyRankedRewardsIfFinished(session);
                scheduleCurrentPhaseTimeout(session);
            }
        }

        if (result.isAccepted() && result.isSessionChanged() && sessionChangeListener != null) {
            sessionChangeListener.onSessionChanged(session);
        }
    }

    private GameActionResult validateGameAction(String uid, GameAction action, SessionState session) {
        if (uid == null || uid.isBlank()) {
            return GameActionResult.rejected("User id is required.");
        }
        if (!session.containsPlayer(uid)) {
            return GameActionResult.rejected("User is not part of this session.");
        }
        if (!"active".equals(session.getStatus())) {
            return GameActionResult.rejected("Session is not active.");
        }
        if (action.getActionType() == null || action.getActionType().isBlank()) {
            return GameActionResult.rejected("Action type is required.");
        }

        TurnState turnState = session.getTurnState();
        if (turnState == null || !turnState.canAct(uid)) {
            return GameActionResult.rejected("User cannot act in the current turn.");
        }

        GamePhase gamePhase = session.getGamePhase();
        if (gamePhase == null) {
            return GameActionResult.rejected("Game phase is not active.");
        }
        if (gamePhase.isExpired(System.currentTimeMillis())) {
            return GameActionResult.rejected("Game phase has expired.");
        }
        if (action.getPhaseVersion() != null && action.getPhaseVersion().longValue() != gamePhase.getPhaseVersion()) {
            return GameActionResult.rejected("Action was sent for an outdated phase.");
        }

        return GameActionResult.accepted(false);
    }

    private void applyRankedRewardsIfFinished(SessionState session) {
        if (session == null || !"finished".equals(session.getStatus())) {
            return;
        }

        RankedPlayerProgressService.RewardResult rewardResult = rankedPlayerProgressService.applyRankedResult(session);
        if (rewardResult.isFailed()) {
            System.err.println(rewardResult.getErrorMessage());
        }
    }

    private void normalizeAbandonedPlayerProgress(SessionState session) {
        if (session == null || session.getAbandonedByUid() == null || !"active".equals(session.getStatus())) {
            return;
        }

        int guard = 0;
        while (guard++ < 8
                && "active".equals(session.getStatus())
                && session.getCurrentTurnUid() != null
                && session.isPlayerAbandoned(session.getCurrentTurnUid())) {
            GameEngine engine = gameEngineRegistry.getEngine(session.getCurrentGame());
            if (engine == null) {
                return;
            }

            GameActionResult result = engine.handlePlayerAbandoned(session, session.getAbandonedByUid());
            if (!result.isAccepted() || !result.isSessionChanged()) {
                return;
            }
            matchEngine.advanceIfNeeded(session);
        }
    }

    public static class GameActionProcessingResult {
        private final SessionState session;
        private final GameActionResult actionResult;

        public GameActionProcessingResult(SessionState session, GameActionResult actionResult) {
            this.session = session;
            this.actionResult = actionResult;
        }

        public static GameActionProcessingResult rejected(SessionState session, String errorMessage) {
            return new GameActionProcessingResult(session, GameActionResult.rejected(errorMessage));
        }

        public SessionState getSession() {
            return session;
        }

        public GameActionResult getActionResult() {
            return actionResult;
        }
    }

    public interface SessionChangeListener {
        void onSessionChanged(SessionState session);
    }
}
