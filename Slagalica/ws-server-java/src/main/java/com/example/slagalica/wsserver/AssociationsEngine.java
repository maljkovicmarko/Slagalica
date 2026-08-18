package com.example.slagalica.wsserver;

import java.text.Normalizer;
import java.util.Locale;

public class AssociationsEngine implements GameEngine {
    private static final String ACTION_OPEN_FIELD = "associations_open_field";
    private static final String ACTION_GUESS_COLUMN = "associations_guess_column";
    private static final String ACTION_GUESS_FINAL = "associations_guess_final";
    private static final String ACTION_PASS = "associations_pass";
    private static final long ROUND_DURATION_MS = 120000L;
    private static final long FINAL_REVEAL_DURATION_MS = 2000L;

    @Override
    public String getGameType() {
        return GameTypes.ASSOCIATIONS;
    }

    @Override
    public GameActionResult handleAction(SessionState session, String playerUid, GameAction action) {
        if (!isActionAllowedPhase(session)) {
            return GameActionResult.rejected("Associations action is not allowed in the current phase.");
        }

        AssociationsGameState gameState = requireAssociationsState(session);
        if (gameState == null || gameState.isFinished()) {
            return GameActionResult.rejected("Associations state is not active.");
        }

        AssociationsGameState.RoundState round = gameState.getCurrentRound();
        if (round == null) {
            finishGame(session, gameState);
            return GameActionResult.accepted(true);
        }

        String actionType = action.getActionType();
        if (ACTION_OPEN_FIELD.equals(actionType)) {
            return openField(session, gameState, round, action);
        }
        if (ACTION_GUESS_COLUMN.equals(actionType)) {
            return guessColumn(session, gameState, round, playerUid, action);
        }
        if (ACTION_GUESS_FINAL.equals(actionType)) {
            return guessFinal(session, gameState, round, playerUid, action);
        }
        if (ACTION_PASS.equals(actionType)) {
            switchTurn(session, gameState);
            return GameActionResult.accepted(true);
        }

        return GameActionResult.rejected("Unsupported Associations action.");
    }

    @Override
    public GameActionResult handleTimeout(SessionState session, long phaseVersion) {
        if (session.getGamePhase() == null || session.getGamePhase().getPhaseVersion() != phaseVersion) {
            return GameActionResult.accepted(false);
        }

        AssociationsGameState gameState = requireAssociationsState(session);
        if (gameState == null || gameState.isFinished()) {
            return GameActionResult.accepted(false);
        }

        if (PhaseTypes.ASSOCIATIONS_FINAL_REVEAL.equals(session.getGamePhase().getPhaseType())) {
            startNextRoundOrFinish(session, gameState);
        } else {
            startNextRoundOrFinish(session, gameState);
        }
        return GameActionResult.accepted(true);
    }

    @Override
    public GameActionResult handlePlayerAbandoned(SessionState session, String abandonedPlayerUid) {
        AssociationsGameState gameState = requireAssociationsState(session);
        if (gameState == null || gameState.isFinished()) {
            return GameActionResult.accepted(false);
        }

        if (session.isPlayerAbandoned(session.getCurrentTurnUid())) {
            switchTurn(session, gameState);
            return GameActionResult.accepted(true);
        }

        return GameActionResult.accepted(false);
    }

    private GameActionResult openField(SessionState session,
                                       AssociationsGameState gameState,
                                       AssociationsGameState.RoundState round,
                                       GameAction action) {
        if (!AssociationsGameState.TURN_MODE_OPEN_FIELD.equals(gameState.getTurnMode())) {
            return GameActionResult.rejected("Open a field only at the start of your turn.");
        }

        String columnId = action.getData().optString("columnId", null);
        int fieldIndex = action.getData().optInt("fieldIndex", -1);
        AssociationsGameState.ColumnState column = round.findColumn(columnId);
        if (column == null || fieldIndex < 0 || fieldIndex >= column.getFields().size()) {
            return GameActionResult.rejected("Selected association field does not exist.");
        }
        if (round.isColumnSolved(columnId) || round.isFieldOpened(columnId, fieldIndex)) {
            return GameActionResult.rejected("Selected association field is not available.");
        }

        round.openField(columnId, fieldIndex);
        gameState.setTurnMode(AssociationsGameState.TURN_MODE_GUESS_OR_PASS);
        return GameActionResult.accepted(true);
    }

    private GameActionResult guessColumn(SessionState session,
                                         AssociationsGameState gameState,
                                         AssociationsGameState.RoundState round,
                                         String playerUid,
                                         GameAction action) {
        String columnId = action.getData().optString("columnId", null);
        String guess = action.getData().optString("guess", null);
        AssociationsGameState.ColumnState column = round.findColumn(columnId);
        if (column == null) {
            return GameActionResult.rejected("Selected association column does not exist.");
        }
        if (round.isColumnSolved(columnId)) {
            return GameActionResult.rejected("Selected association column is already solved.");
        }
        if (guess == null || guess.isBlank()) {
            return GameActionResult.rejected("Column guess is required.");
        }

        if (matches(guess, column.getSolution())) {
            round.solveColumn(columnId);
            int points = 2 + round.getUnopenedFieldCount(columnId);
            applyScore(session, gameState, playerUid, points);
            gameState.setTurnMode(AssociationsGameState.TURN_MODE_GUESS_OR_PASS);
            return GameActionResult.accepted(true);
        }

        switchTurn(session, gameState);
        return GameActionResult.accepted(true);
    }

    private GameActionResult guessFinal(SessionState session,
                                        AssociationsGameState gameState,
                                        AssociationsGameState.RoundState round,
                                        String playerUid,
                                        GameAction action) {
        String guess = action.getData().optString("guess", null);
        if (guess == null || guess.isBlank()) {
            return GameActionResult.rejected("Final association guess is required.");
        }

        if (matches(guess, round.getFinalAnswer())) {
            int points = calculateFinalPoints(round);
            round.setFinalSolved(true);
            applyScore(session, gameState, playerUid, points);
            session.setTurnState(TurnState.nobody(round.getOwnerUid()));
            session.startGamePhase(
                    PhaseTypes.ASSOCIATIONS_FINAL_REVEAL,
                    gameState.getCurrentRoundIndex() + 1,
                    FINAL_REVEAL_DURATION_MS
            );
            return GameActionResult.accepted(true);
        }

        switchTurn(session, gameState);
        return GameActionResult.accepted(true);
    }

    private int calculateFinalPoints(AssociationsGameState.RoundState round) {
        int points = 7;
        for (AssociationsGameState.ColumnState column : round.getColumns()) {
            if (round.isColumnSolved(column.getId())) {
                continue;
            }
            int opened = round.getOpenedFieldCount(column.getId());
            if (opened == 0) {
                points += 6;
            } else {
                points += 2 + (4 - opened);
            }
        }
        return points;
    }

    private boolean isActionAllowedPhase(SessionState session) {
        return session.getGamePhase() != null
                && PhaseTypes.ASSOCIATIONS_ROUND_ACTIVE.equals(session.getGamePhase().getPhaseType());
    }

    private AssociationsGameState requireAssociationsState(SessionState session) {
        if (session.getActiveGameState() instanceof AssociationsGameState) {
            return (AssociationsGameState) session.getActiveGameState();
        }
        return null;
    }

    private void applyScore(SessionState session, AssociationsGameState gameState, String playerUid, int scoreDelta) {
        session.addScore(playerUid, scoreDelta);
        gameState.setPlayer1Score(session.getPlayer1Score());
        gameState.setPlayer2Score(session.getPlayer2Score());
    }

    private void switchTurn(SessionState session, AssociationsGameState gameState) {
        String nextPlayerUid = session.otherPlayer(session.getCurrentTurnUid());
        if (session.isPlayerAbandoned(nextPlayerUid)) {
            nextPlayerUid = session.getRemainingPlayerUid();
        }
        AssociationsGameState.RoundState round = gameState.getCurrentRound();
        String roundOwnerUid = round == null ? nextPlayerUid : round.getOwnerUid();
        session.setTurnState(TurnState.single(roundOwnerUid, nextPlayerUid));
        gameState.setTurnMode(hasAvailableField(round)
                ? AssociationsGameState.TURN_MODE_OPEN_FIELD
                : AssociationsGameState.TURN_MODE_GUESS_OR_PASS);
    }

    private boolean hasAvailableField(AssociationsGameState.RoundState round) {
        if (round == null) {
            return false;
        }
        for (AssociationsGameState.ColumnState column : round.getColumns()) {
            if (round.isColumnSolved(column.getId())) {
                continue;
            }
            for (int i = 0; i < column.getFields().size(); i++) {
                if (!round.isFieldOpened(column.getId(), i)) {
                    return true;
                }
            }
        }
        return false;
    }

    private void startNextRoundOrFinish(SessionState session, AssociationsGameState gameState) {
        int nextRoundIndex = gameState.getCurrentRoundIndex() + 1;
        if (nextRoundIndex >= gameState.getRounds().size()) {
            finishGame(session, gameState);
            return;
        }

        gameState.setCurrentRoundIndex(nextRoundIndex);
        gameState.setTurnMode(AssociationsGameState.TURN_MODE_OPEN_FIELD);
        AssociationsGameState.RoundState nextRound = gameState.getCurrentRound();
        if (nextRound == null) {
            finishGame(session, gameState);
            return;
        }

        session.setTurnState(TurnState.single(nextRound.getOwnerUid(), nextRound.getOwnerUid()));
        if (session.isPlayerAbandoned(nextRound.getOwnerUid())) {
            session.setTurnState(TurnState.single(nextRound.getOwnerUid(), session.getRemainingPlayerUid()));
        }
        session.startGamePhase(
                PhaseTypes.ASSOCIATIONS_ROUND_ACTIVE,
                nextRoundIndex + 1,
                ROUND_DURATION_MS
        );
    }

    private void finishGame(SessionState session, AssociationsGameState gameState) {
        gameState.setFinished(true);
        session.setTurnState(TurnState.nobody(session.getPlayer1Uid()));
        session.setGamePhase(null);
    }

    private boolean matches(String guess, String answer) {
        return normalize(guess).equals(normalize(answer));
    }

    private String normalize(String value) {
        if (value == null) {
            return "";
        }
        String lower = value.trim().toLowerCase(Locale.ROOT);
        return Normalizer.normalize(lower, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
    }
}
