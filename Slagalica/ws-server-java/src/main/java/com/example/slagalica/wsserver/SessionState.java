package com.example.slagalica.wsserver;

import org.json.JSONArray;
import org.json.JSONObject;

public class SessionState {
    private static final long GENERAL_KNOWLEDGE_QUESTION_DURATION_MS = 5000L;

    private final String sessionId;
    private final String sessionType;
    private final String player1Uid;
    private final String player2Uid;
    private final long createdAtMs;

    private String status;
    private String winnerUid;
    private String abandonedByUid;
    private boolean player1Connected;
    private boolean player2Connected;
    private int player1Score;
    private int player2Score;
    private int currentGameIndex;
    private TurnState turnState;
    private GameState activeGameState;
    private GamePhase gamePhase;
    private long nextPhaseVersion;
    private boolean rankedRewardsApplied;

    public SessionState(String sessionId,
                        String sessionType,
                        String player1Uid,
                        String player2Uid,
                        long createdAtMs,
                        GameState activeGameState) {
        this.sessionId = sessionId;
        this.sessionType = sessionType;
        this.player1Uid = player1Uid;
        this.player2Uid = player2Uid;
        this.createdAtMs = createdAtMs;
        this.status = "active";
        this.player1Connected = true;
        this.player2Connected = true;
        this.player1Score = 0;
        this.player2Score = 0;
        this.currentGameIndex = Math.max(0, GameSequence.indexOf(activeGameState.getGameType()));
        this.nextPhaseVersion = 1L;
        this.turnState = TurnState.both(player1Uid, player1Uid, player1Uid, player2Uid);
        this.activeGameState = activeGameState;
        this.gamePhase = GamePhase.startingAt(
                PhaseTypes.GENERAL_KNOWLEDGE_QUESTION_OPEN,
                nextPhaseVersion++,
                1,
                createdAtMs,
                GENERAL_KNOWLEDGE_QUESTION_DURATION_MS
        );
        this.rankedRewardsApplied = false;
    }

    public String getSessionId() {
        return sessionId;
    }

    public String getSessionType() {
        return sessionType;
    }

    public String getPlayer1Uid() {
        return player1Uid;
    }

    public String getPlayer2Uid() {
        return player2Uid;
    }

    public long getCreatedAtMs() {
        return createdAtMs;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getCurrentGame() {
        if (activeGameState == null) {
            return null;
        }
        return activeGameState.getGameType();
    }

    public int getCurrentGameIndex() {
        return currentGameIndex;
    }

    public int getPlayer1Score() {
        return player1Score;
    }

    public int getPlayer2Score() {
        return player2Score;
    }

    public void addScore(String playerUid, int scoreDelta) {
        if (playerUid == null) {
            return;
        }
        if (playerUid.equals(player1Uid)) {
            player1Score += scoreDelta;
        } else if (playerUid.equals(player2Uid)) {
            player2Score += scoreDelta;
        }
    }

    public void setCurrentGameIndex(int currentGameIndex) {
        this.currentGameIndex = currentGameIndex;
    }

    public String getNextGame() {
        return GameSequence.nextAfter(getCurrentGame());
    }

    public String getCurrentTurnUid() {
        if (turnState == null) {
            return null;
        }
        return turnState.getActivePlayerUid();
    }

    public void setCurrentTurnUid(String currentTurnUid) {
        String roundOwnerUid = turnState == null ? currentTurnUid : turnState.getRoundOwnerUid();
        this.turnState = TurnState.single(roundOwnerUid, currentTurnUid);
    }

    public TurnState getTurnState() {
        return turnState;
    }

    public void setTurnState(TurnState turnState) {
        this.turnState = turnState;
    }

    public GameState getActiveGameState() {
        return activeGameState;
    }

    public void setActiveGameState(GameState activeGameState) {
        this.activeGameState = activeGameState;
        this.currentGameIndex = Math.max(0, GameSequence.indexOf(activeGameState.getGameType()));
    }

    public GamePhase getGamePhase() {
        return gamePhase;
    }

    public void setGamePhase(GamePhase gamePhase) {
        this.gamePhase = gamePhase;
    }

    public void startGamePhase(String phaseType, int roundNumber, long durationMs) {
        gamePhase = GamePhase.startingNow(phaseType, nextPhaseVersion++, roundNumber, durationMs);
    }

    public String getWinnerUid() {
        return winnerUid;
    }

    public void setWinnerUid(String winnerUid) {
        this.winnerUid = winnerUid;
    }

    public String getAbandonedByUid() {
        return abandonedByUid;
    }

    public void setAbandonedByUid(String abandonedByUid) {
        this.abandonedByUid = abandonedByUid;
    }

    public boolean isPlayerAbandoned(String uid) {
        return uid != null && uid.equals(abandonedByUid);
    }

    public String getRemainingPlayerUid() {
        if (abandonedByUid == null) {
            return null;
        }
        return otherPlayer(abandonedByUid);
    }

    public boolean isRankedRewardsApplied() {
        return rankedRewardsApplied;
    }

    public void setRankedRewardsApplied(boolean rankedRewardsApplied) {
        this.rankedRewardsApplied = rankedRewardsApplied;
    }

    public boolean containsPlayer(String uid) {
        return uid != null && (uid.equals(player1Uid) || uid.equals(player2Uid));
    }

    public String otherPlayer(String uid) {
        if (uid == null) {
            return null;
        }
        if (uid.equals(player1Uid)) {
            return player2Uid;
        }
        if (uid.equals(player2Uid)) {
            return player1Uid;
        }
        return null;
    }

    public void markConnected(String uid, boolean connected) {
        if (uid == null) {
            return;
        }
        if (uid.equals(player1Uid)) {
            player1Connected = connected;
        } else if (uid.equals(player2Uid)) {
            player2Connected = connected;
        }
    }

    public JSONObject toJson() {
        JSONObject json = new JSONObject();
        json.put("sessionId", sessionId);
        json.put("sessionType", sessionType);
        json.put("status", status);
        json.put("player1Uid", player1Uid);
        json.put("player2Uid", player2Uid);
        json.put("player1Connected", player1Connected);
        json.put("player2Connected", player2Connected);
        json.put("player1Score", player1Score);
        json.put("player2Score", player2Score);
        json.put("createdAtMs", createdAtMs);
        json.put("serverNowMs", System.currentTimeMillis());
        json.put("currentGame", getCurrentGame() == null ? JSONObject.NULL : getCurrentGame());
        json.put("currentGameIndex", currentGameIndex);
        json.put("nextGame", getNextGame() == null ? JSONObject.NULL : getNextGame());
        json.put("gameSequence", buildGameSequenceJson());
        json.put("currentTurnUid", getCurrentTurnUid() == null ? JSONObject.NULL : getCurrentTurnUid());
        json.put("turnState", turnState == null ? JSONObject.NULL : turnState.toJson());
        json.put("gamePhase", gamePhase == null ? JSONObject.NULL : gamePhase.toJson());
        json.put("winnerUid", winnerUid == null ? JSONObject.NULL : winnerUid);
        json.put("abandonedByUid", abandonedByUid == null ? JSONObject.NULL : abandonedByUid);
        json.put("activeGameState", activeGameState == null ? JSONObject.NULL : activeGameState.toJson());
        json.put("generalKnowledgeQuestions", buildLegacyGeneralKnowledgeQuestionsJson());
        return json;
    }

    private JSONArray buildGameSequenceJson() {
        JSONArray gameSequenceJson = new JSONArray();
        for (String gameType : GameSequence.orderedGameTypes()) {
            gameSequenceJson.put(gameType);
        }
        return gameSequenceJson;
    }

    private Object buildLegacyGeneralKnowledgeQuestionsJson() {
        if (activeGameState instanceof GeneralKnowledgeGameState) {
            GeneralKnowledgeGameState generalKnowledgeGameState = (GeneralKnowledgeGameState) activeGameState;
            return GeneralKnowledgeGameState.buildQuestionsJson(generalKnowledgeGameState.getQuestions());
        }
        return JSONObject.NULL;
    }
}
