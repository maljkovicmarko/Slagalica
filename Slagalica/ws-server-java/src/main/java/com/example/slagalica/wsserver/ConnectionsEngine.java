package com.example.slagalica.wsserver;

public class ConnectionsEngine implements GameEngine {
    private static final String ACTION_SUBMIT_PAIR = "connections_submit_pair";
    private static final long ATTEMPT_DURATION_MS = 30000L;
    private static final int CORRECT_PAIR_POINTS = 2;

    @Override
    public String getGameType() {
        return GameTypes.CONNECTIONS;
    }

    @Override
    public GameActionResult handleAction(SessionState session, String playerUid, GameAction action) {
        if (!ACTION_SUBMIT_PAIR.equals(action.getActionType())) {
            return GameActionResult.rejected("Unsupported Connections action.");
        }
        if (!isAttemptPhase(session)) {
            return GameActionResult.rejected("Connections pair submission is not allowed in the current phase.");
        }

        ConnectionsGameState gameState = requireConnectionsState(session);
        if (gameState == null || gameState.isFinished()) {
            return GameActionResult.rejected("Connections state is not active.");
        }

        ConnectionsGameState.RoundState round = gameState.getCurrentRound();
        if (round == null) {
            finishGame(session, gameState);
            return GameActionResult.accepted(true);
        }

        String leftId = action.getData().optString("leftId", null);
        String rightId = action.getData().optString("rightId", null);
        ConnectionsGameState.LeftItem leftItem = round.findLeftItem(leftId);
        ConnectionsGameState.RightItem rightItem = round.findRightItem(rightId);

        if (leftItem == null || rightItem == null) {
            return GameActionResult.rejected("Selected pair does not exist.");
        }
        if (round.getResolvedLeftIds().contains(leftId) || round.getAttemptedLeftIds().contains(leftId)) {
            return GameActionResult.rejected("Selected left item is not available.");
        }

        boolean correct = leftItem.getMatchId().equals(rightItem.getId());
        round.getAttemptResults().add(new ConnectionsGameState.AttemptResult(leftId, rightId, correct));

        if (correct) {
            round.getResolvedLeftIds().add(leftId);
            applyScore(session, gameState, playerUid, CORRECT_PAIR_POINTS);
        } else {
            round.getAttemptedLeftIds().add(leftId);
        }

        advancePhaseIfNeeded(session, gameState, round);
        return GameActionResult.accepted(true);
    }

    @Override
    public GameActionResult handleTimeout(SessionState session, long phaseVersion) {
        if (session.getGamePhase() == null || session.getGamePhase().getPhaseVersion() != phaseVersion) {
            return GameActionResult.accepted(false);
        }

        ConnectionsGameState gameState = requireConnectionsState(session);
        if (gameState == null || gameState.isFinished()) {
            return GameActionResult.accepted(false);
        }

        ConnectionsGameState.RoundState round = gameState.getCurrentRound();
        if (round == null) {
            finishGame(session, gameState);
        } else {
            advanceAfterCurrentPhase(session, gameState, round);
        }
        return GameActionResult.accepted(true);
    }

    @Override
    public GameActionResult handlePlayerAbandoned(SessionState session, String abandonedPlayerUid) {
        ConnectionsGameState gameState = requireConnectionsState(session);
        if (gameState == null || gameState.isFinished() || session.getGamePhase() == null) {
            return GameActionResult.accepted(false);
        }

        TurnState turnState = session.getTurnState();
        if (turnState != null && turnState.canAct(abandonedPlayerUid)) {
            ConnectionsGameState.RoundState round = gameState.getCurrentRound();
            if (round == null) {
                finishGame(session, gameState);
            } else {
                advanceAfterCurrentPhase(session, gameState, round);
                skipAbandonedTurns(session, gameState);
            }
            return GameActionResult.accepted(true);
        }

        return GameActionResult.accepted(false);
    }

    private boolean isAttemptPhase(SessionState session) {
        if (session.getGamePhase() == null) {
            return false;
        }
        String phaseType = session.getGamePhase().getPhaseType();
        return PhaseTypes.CONNECTIONS_OWNER_ATTEMPT.equals(phaseType)
                || PhaseTypes.CONNECTIONS_OPPONENT_ATTEMPT.equals(phaseType);
    }

    private ConnectionsGameState requireConnectionsState(SessionState session) {
        if (session.getActiveGameState() instanceof ConnectionsGameState) {
            return (ConnectionsGameState) session.getActiveGameState();
        }
        return null;
    }

    private void applyScore(SessionState session, ConnectionsGameState gameState, String playerUid, int scoreDelta) {
        session.addScore(playerUid, scoreDelta);
        gameState.setPlayer1Score(session.getPlayer1Score());
        gameState.setPlayer2Score(session.getPlayer2Score());
    }

    private void advancePhaseIfNeeded(SessionState session,
                                      ConnectionsGameState gameState,
                                      ConnectionsGameState.RoundState round) {
        if (round.getUnresolvedCount() == 0 || round.isCurrentAttemptComplete()) {
            advanceAfterCurrentPhase(session, gameState, round);
        }
    }

    private void advanceAfterCurrentPhase(SessionState session,
                                          ConnectionsGameState gameState,
                                          ConnectionsGameState.RoundState round) {
        String phaseType = session.getGamePhase() == null ? null : session.getGamePhase().getPhaseType();
        if (PhaseTypes.CONNECTIONS_OWNER_ATTEMPT.equals(phaseType) && round.getUnresolvedCount() > 0) {
            startOpponentAttempt(session, round);
            return;
        }
        startNextRoundOrFinish(session, gameState);
    }

    private void startOpponentAttempt(SessionState session, ConnectionsGameState.RoundState round) {
        round.clearAttemptedLeftIds();
        String opponentUid = session.otherPlayer(round.getOwnerUid());
        session.setTurnState(TurnState.single(round.getOwnerUid(), opponentUid));
        session.startGamePhase(
                PhaseTypes.CONNECTIONS_OPPONENT_ATTEMPT,
                1,
                ATTEMPT_DURATION_MS
        );
    }

    private void startNextRoundOrFinish(SessionState session, ConnectionsGameState gameState) {
        int nextRoundIndex = gameState.getCurrentRoundIndex() + 1;
        if (nextRoundIndex >= gameState.getRounds().size()) {
            finishGame(session, gameState);
            return;
        }

        gameState.setCurrentRoundIndex(nextRoundIndex);
        ConnectionsGameState.RoundState nextRound = gameState.getCurrentRound();
        if (nextRound == null) {
            finishGame(session, gameState);
            return;
        }

        session.setTurnState(TurnState.single(nextRound.getOwnerUid(), nextRound.getOwnerUid()));
        session.startGamePhase(
                PhaseTypes.CONNECTIONS_OWNER_ATTEMPT,
                nextRoundIndex + 1,
                ATTEMPT_DURATION_MS
        );
        skipAbandonedTurns(session, gameState);
    }

    private void skipAbandonedTurns(SessionState session, ConnectionsGameState gameState) {
        int guard = 0;
        while (guard++ < 4
                && !gameState.isFinished()
                && session.getCurrentTurnUid() != null
                && session.isPlayerAbandoned(session.getCurrentTurnUid())) {
            ConnectionsGameState.RoundState round = gameState.getCurrentRound();
            if (round == null) {
                finishGame(session, gameState);
            } else {
                advanceAfterCurrentPhase(session, gameState, round);
            }
        }
    }

    private void finishGame(SessionState session, ConnectionsGameState gameState) {
        gameState.setFinished(true);
        session.setTurnState(TurnState.nobody(session.getPlayer1Uid()));
        session.setGamePhase(null);
    }
}
