package com.example.slagalica.wsserver;

public class MatchEngine {
    private static final long CONNECTIONS_OWNER_ATTEMPT_DURATION_MS = 30000L;
    private static final long ASSOCIATIONS_ROUND_DURATION_MS = 120000L;
    private static final long GUESS_COMBINATION_OWNER_ATTEMPT_DURATION_MS = 30000L;
    private static final long STEP_BY_STEP_STEP_DURATION_MS = 10000L;
    private static final long FIND_NUMBER_STOP_DURATION_MS = 5000L;

    private final GameStateFactory gameStateFactory;

    public MatchEngine() {
        gameStateFactory = new GameStateFactory();
    }

    public boolean advanceIfNeeded(SessionState session) {
        if (session == null || session.getActiveGameState() == null || !session.getActiveGameState().isFinished()) {
            return false;
        }

        String nextGameType = session.getNextGame();
        if (nextGameType == null) {
            session.setStatus("finished");
            session.setWinnerUid(resolveWinnerUid(session));
            session.setTurnState(TurnState.nobody(session.getPlayer1Uid()));
            session.setGamePhase(null);
            return true;
        }

        GameState nextGameState = gameStateFactory.create(nextGameType, session);
        if (nextGameState == null) {
            return false;
        }

        session.setActiveGameState(nextGameState);
        session.setCurrentGameIndex(GameSequence.indexOf(nextGameType));
        initializeNextGame(session, nextGameType);
        return true;
    }

    private void initializeNextGame(SessionState session, String nextGameType) {
        if (GameTypes.CONNECTIONS.equals(nextGameType)) {
            session.setTurnState(TurnState.single(session.getPlayer1Uid(), session.getPlayer1Uid()));
            session.startGamePhase(
                    PhaseTypes.CONNECTIONS_OWNER_ATTEMPT,
                    1,
                    CONNECTIONS_OWNER_ATTEMPT_DURATION_MS
            );
        } else if (GameTypes.ASSOCIATIONS.equals(nextGameType)) {
            session.setTurnState(TurnState.single(session.getPlayer1Uid(), session.getPlayer1Uid()));
            session.startGamePhase(
                    PhaseTypes.ASSOCIATIONS_ROUND_ACTIVE,
                    1,
                    ASSOCIATIONS_ROUND_DURATION_MS
            );
        } else if (GameTypes.GUESS_THE_COMBINATION.equals(nextGameType)) {
            session.setTurnState(TurnState.single(session.getPlayer1Uid(), session.getPlayer1Uid()));
            session.startGamePhase(
                    PhaseTypes.GUESS_COMBINATION_OWNER_ATTEMPT,
                    1,
                    GUESS_COMBINATION_OWNER_ATTEMPT_DURATION_MS
            );
        } else if (GameTypes.STEP_BY_STEP.equals(nextGameType)) {
            session.setTurnState(TurnState.single(session.getPlayer1Uid(), session.getPlayer1Uid()));
            session.startGamePhase(
                    PhaseTypes.STEP_BY_STEP_OWNER_STEP,
                    1,
                    STEP_BY_STEP_STEP_DURATION_MS
            );
        } else if (GameTypes.FIND_THE_NUMBER.equals(nextGameType)) {
            session.setTurnState(TurnState.single(session.getPlayer1Uid(), session.getPlayer1Uid()));
            session.startGamePhase(
                    PhaseTypes.FIND_NUMBER_TARGET_STOP,
                    1,
                    FIND_NUMBER_STOP_DURATION_MS
            );
        }
    }

    private String resolveWinnerUid(SessionState session) {
        if (session.getPlayer1Score() > session.getPlayer2Score()) {
            return session.getPlayer1Uid();
        }
        if (session.getPlayer2Score() > session.getPlayer1Score()) {
            return session.getPlayer2Uid();
        }
        return null;
    }
}
