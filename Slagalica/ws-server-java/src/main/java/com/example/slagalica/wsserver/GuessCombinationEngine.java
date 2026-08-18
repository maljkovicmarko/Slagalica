package com.example.slagalica.wsserver;

import org.json.JSONArray;

import java.util.ArrayList;
import java.util.List;

public class GuessCombinationEngine implements GameEngine {
    private static final String ACTION_SUBMIT = "guess_combination_submit";
    private static final long OWNER_ATTEMPT_DURATION_MS = 30000L;
    private static final long OPPONENT_ATTEMPT_DURATION_MS = 10000L;

    @Override
    public String getGameType() {
        return GameTypes.GUESS_THE_COMBINATION;
    }

    @Override
    public GameActionResult handleAction(SessionState session, String playerUid, GameAction action) {
        if (!ACTION_SUBMIT.equals(action.getActionType())) {
            return GameActionResult.rejected("Unsupported Guess Combination action.");
        }
        if (!isAttemptPhase(session)) {
            return GameActionResult.rejected("Guess Combination submit is not allowed in the current phase.");
        }

        GuessCombinationGameState gameState = requireGameState(session);
        if (gameState == null || gameState.isFinished()) {
            return GameActionResult.rejected("Guess Combination state is not active.");
        }

        GuessCombinationGameState.RoundState round = gameState.getCurrentRound();
        if (round == null) {
            finishGame(session, gameState);
            return GameActionResult.accepted(true);
        }

        List<String> symbols = parseSymbols(action.getData().optJSONArray("symbols"));
        if (symbols.size() != 4 || !GuessCombinationGameState.SYMBOLS.containsAll(symbols)) {
            return GameActionResult.rejected("Guess Combination submit requires 4 valid symbols.");
        }

        GuessCombinationGameState.AttemptResult attempt = buildAttempt(playerUid, symbols, round.getTargetCombination());
        String phaseType = session.getGamePhase().getPhaseType();
        if (PhaseTypes.GUESS_COMBINATION_OWNER_ATTEMPT.equals(phaseType)) {
            return handleOwnerAttempt(session, gameState, round, playerUid, attempt);
        }
        if (PhaseTypes.GUESS_COMBINATION_OPPONENT_ATTEMPT.equals(phaseType)) {
            return handleOpponentAttempt(session, gameState, round, playerUid, attempt);
        }

        return GameActionResult.accepted(false);
    }

    @Override
    public GameActionResult handleTimeout(SessionState session, long phaseVersion) {
        if (session.getGamePhase() == null || session.getGamePhase().getPhaseVersion() != phaseVersion) {
            return GameActionResult.accepted(false);
        }

        GuessCombinationGameState gameState = requireGameState(session);
        if (gameState == null || gameState.isFinished()) {
            return GameActionResult.accepted(false);
        }

        GuessCombinationGameState.RoundState round = gameState.getCurrentRound();
        if (round == null) {
            finishGame(session, gameState);
            return GameActionResult.accepted(true);
        }

        String phaseType = session.getGamePhase().getPhaseType();
        if (PhaseTypes.GUESS_COMBINATION_OWNER_ATTEMPT.equals(phaseType)) {
            startOpponentAttempt(session, round);
        } else if (PhaseTypes.GUESS_COMBINATION_OPPONENT_ATTEMPT.equals(phaseType)) {
            round.setCompleted(true);
            startNextRoundOrFinish(session, gameState);
        }
        return GameActionResult.accepted(true);
    }

    @Override
    public GameActionResult handlePlayerAbandoned(SessionState session, String abandonedPlayerUid) {
        GuessCombinationGameState gameState = requireGameState(session);
        if (gameState == null || gameState.isFinished() || session.getGamePhase() == null) {
            return GameActionResult.accepted(false);
        }

        if (session.isPlayerAbandoned(session.getCurrentTurnUid())) {
            GuessCombinationGameState.RoundState round = gameState.getCurrentRound();
            if (round == null) {
                finishGame(session, gameState);
            } else {
                round.setCompleted(true);
                startNextRoundOrFinish(session, gameState);
                skipAbandonedTurns(session, gameState);
            }
            return GameActionResult.accepted(true);
        }

        return GameActionResult.accepted(false);
    }

    private GameActionResult handleOwnerAttempt(SessionState session,
                                                GuessCombinationGameState gameState,
                                                GuessCombinationGameState.RoundState round,
                                                String playerUid,
                                                GuessCombinationGameState.AttemptResult attempt) {
        if (!playerUid.equals(round.getOwnerUid())) {
            return GameActionResult.rejected("Only round owner can submit owner attempts.");
        }
        if (round.getOwnerAttempts().size() >= 6) {
            return GameActionResult.rejected("Owner has no attempts left.");
        }

        round.getOwnerAttempts().add(attempt);
        if (attempt.getExactMatches() == 4) {
            applyScore(session, gameState, playerUid, pointsForAttempt(round.getOwnerAttempts().size()));
            round.setCompleted(true);
            startNextRoundOrFinish(session, gameState);
        } else if (round.getOwnerAttempts().size() >= 6) {
            startOpponentAttempt(session, round);
        }
        return GameActionResult.accepted(true);
    }

    private GameActionResult handleOpponentAttempt(SessionState session,
                                                   GuessCombinationGameState gameState,
                                                   GuessCombinationGameState.RoundState round,
                                                   String playerUid,
                                                   GuessCombinationGameState.AttemptResult attempt) {
        String opponentUid = session.otherPlayer(round.getOwnerUid());
        if (!playerUid.equals(opponentUid)) {
            return GameActionResult.rejected("Only opponent can submit cleanup attempt.");
        }
        if (round.getOpponentAttempt() != null) {
            return GameActionResult.rejected("Opponent attempt is already used.");
        }

        round.setOpponentAttempt(attempt);
        if (attempt.getExactMatches() == 4) {
            applyScore(session, gameState, playerUid, 10);
        }
        round.setCompleted(true);
        startNextRoundOrFinish(session, gameState);
        return GameActionResult.accepted(true);
    }

    private void startOpponentAttempt(SessionState session, GuessCombinationGameState.RoundState round) {
        String opponentUid = session.otherPlayer(round.getOwnerUid());
        session.setTurnState(TurnState.single(round.getOwnerUid(), opponentUid));
        session.startGamePhase(
                PhaseTypes.GUESS_COMBINATION_OPPONENT_ATTEMPT,
                1,
                OPPONENT_ATTEMPT_DURATION_MS
        );
    }

    private void startNextRoundOrFinish(SessionState session, GuessCombinationGameState gameState) {
        int nextRoundIndex = gameState.getCurrentRoundIndex() + 1;
        if (nextRoundIndex >= gameState.getRounds().size()) {
            finishGame(session, gameState);
            return;
        }

        gameState.setCurrentRoundIndex(nextRoundIndex);
        GuessCombinationGameState.RoundState nextRound = gameState.getCurrentRound();
        if (nextRound == null) {
            finishGame(session, gameState);
            return;
        }

        session.setTurnState(TurnState.single(nextRound.getOwnerUid(), nextRound.getOwnerUid()));
        session.startGamePhase(
                PhaseTypes.GUESS_COMBINATION_OWNER_ATTEMPT,
                nextRoundIndex + 1,
                OWNER_ATTEMPT_DURATION_MS
        );
        skipAbandonedTurns(session, gameState);
    }

    private void skipAbandonedTurns(SessionState session, GuessCombinationGameState gameState) {
        int guard = 0;
        while (guard++ < 4
                && !gameState.isFinished()
                && session.getCurrentTurnUid() != null
                && session.isPlayerAbandoned(session.getCurrentTurnUid())) {
            GuessCombinationGameState.RoundState round = gameState.getCurrentRound();
            if (round == null) {
                finishGame(session, gameState);
            } else {
                round.setCompleted(true);
                startNextRoundOrFinish(session, gameState);
            }
        }
    }

    private GuessCombinationGameState.AttemptResult buildAttempt(String playerUid, List<String> symbols, List<String> target) {
        int exactMatches = countExactMatches(symbols, target);
        int symbolOnlyMatches = countAllSymbolMatches(symbols, target) - exactMatches;
        return new GuessCombinationGameState.AttemptResult(playerUid, symbols, exactMatches, symbolOnlyMatches);
    }

    private int countExactMatches(List<String> symbols, List<String> target) {
        int count = 0;
        for (int i = 0; i < symbols.size(); i++) {
            if (symbols.get(i).equals(target.get(i))) {
                count++;
            }
        }
        return count;
    }

    private int countAllSymbolMatches(List<String> symbols, List<String> target) {
        List<String> remainingTarget = new ArrayList<>(target);
        int count = 0;
        for (String symbol : symbols) {
            if (remainingTarget.remove(symbol)) {
                count++;
            }
        }
        return count;
    }

    private List<String> parseSymbols(JSONArray jsonArray) {
        List<String> symbols = new ArrayList<>();
        if (jsonArray == null) {
            return symbols;
        }
        for (int i = 0; i < jsonArray.length(); i++) {
            String symbol = jsonArray.optString(i, null);
            if (symbol != null && !symbol.isBlank()) {
                symbols.add(symbol);
            }
        }
        return symbols;
    }

    private int pointsForAttempt(int attemptNumber) {
        if (attemptNumber <= 2) {
            return 20;
        }
        if (attemptNumber <= 4) {
            return 15;
        }
        return 10;
    }

    private boolean isAttemptPhase(SessionState session) {
        if (session.getGamePhase() == null) {
            return false;
        }
        String phaseType = session.getGamePhase().getPhaseType();
        return PhaseTypes.GUESS_COMBINATION_OWNER_ATTEMPT.equals(phaseType)
                || PhaseTypes.GUESS_COMBINATION_OPPONENT_ATTEMPT.equals(phaseType);
    }

    private GuessCombinationGameState requireGameState(SessionState session) {
        if (session.getActiveGameState() instanceof GuessCombinationGameState) {
            return (GuessCombinationGameState) session.getActiveGameState();
        }
        return null;
    }

    private void applyScore(SessionState session, GuessCombinationGameState gameState, String playerUid, int scoreDelta) {
        session.addScore(playerUid, scoreDelta);
        gameState.setPlayer1Score(session.getPlayer1Score());
        gameState.setPlayer2Score(session.getPlayer2Score());
    }

    private void finishGame(SessionState session, GuessCombinationGameState gameState) {
        gameState.setFinished(true);
        session.setTurnState(TurnState.nobody(session.getPlayer1Uid()));
        session.setGamePhase(null);
    }
}
