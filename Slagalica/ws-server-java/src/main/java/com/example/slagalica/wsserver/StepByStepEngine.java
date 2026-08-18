package com.example.slagalica.wsserver;

import java.text.Normalizer;
import java.util.Locale;

public class StepByStepEngine implements GameEngine {
    private static final String ACTION_GUESS = "step_by_step_guess";
    private static final long STEP_DURATION_MS = 10000L;
    private static final long OPPONENT_ATTEMPT_DURATION_MS = 10000L;

    @Override
    public String getGameType() {
        return GameTypes.STEP_BY_STEP;
    }

    @Override
    public GameActionResult handleAction(SessionState session, String playerUid, GameAction action) {
        if (!ACTION_GUESS.equals(action.getActionType())) {
            return GameActionResult.rejected("Unsupported Step by Step action.");
        }
        if (!isGuessPhase(session)) {
            return GameActionResult.rejected("Step by Step guess is not allowed in the current phase.");
        }

        StepByStepGameState gameState = requireGameState(session);
        if (gameState == null || gameState.isFinished()) {
            return GameActionResult.rejected("Step by Step state is not active.");
        }

        StepByStepGameState.RoundState round = gameState.getCurrentRound();
        if (round == null) {
            finishGame(session, gameState);
            return GameActionResult.accepted(true);
        }

        String guess = action.getData().optString("guess", null);
        if (guess == null || guess.isBlank()) {
            return GameActionResult.rejected("Step by Step guess is required.");
        }

        if (!matches(guess, round.getAnswer())) {
            if (PhaseTypes.STEP_BY_STEP_OPPONENT_ATTEMPT.equals(session.getGamePhase().getPhaseType())) {
                round.setCompleted(true);
                round.setAnswerRevealed(true);
                startNextRoundOrFinish(session, gameState);
                return GameActionResult.accepted(true);
            }
            return GameActionResult.accepted(false);
        }

        int scoreDelta = PhaseTypes.STEP_BY_STEP_OPPONENT_ATTEMPT.equals(session.getGamePhase().getPhaseType())
                ? 5
                : 20 - round.getCurrentStepIndex() * 2;
        applyScore(session, gameState, playerUid, scoreDelta);
        round.setCompleted(true);
        round.setAnswerRevealed(true);
        startNextRoundOrFinish(session, gameState);
        return GameActionResult.accepted(true);
    }

    @Override
    public GameActionResult handleTimeout(SessionState session, long phaseVersion) {
        if (session.getGamePhase() == null || session.getGamePhase().getPhaseVersion() != phaseVersion) {
            return GameActionResult.accepted(false);
        }

        StepByStepGameState gameState = requireGameState(session);
        if (gameState == null || gameState.isFinished()) {
            return GameActionResult.accepted(false);
        }

        StepByStepGameState.RoundState round = gameState.getCurrentRound();
        if (round == null) {
            finishGame(session, gameState);
            return GameActionResult.accepted(true);
        }

        if (PhaseTypes.STEP_BY_STEP_OWNER_STEP.equals(session.getGamePhase().getPhaseType())) {
            if (round.getCurrentStepIndex() < 6) {
                round.setCurrentStepIndex(round.getCurrentStepIndex() + 1);
                session.startGamePhase(
                        PhaseTypes.STEP_BY_STEP_OWNER_STEP,
                        gameState.getCurrentRoundIndex() + 1,
                        STEP_DURATION_MS
                );
            } else {
                startOpponentAttempt(session, round);
            }
            return GameActionResult.accepted(true);
        }

        if (PhaseTypes.STEP_BY_STEP_OPPONENT_ATTEMPT.equals(session.getGamePhase().getPhaseType())) {
            round.setCompleted(true);
            round.setAnswerRevealed(true);
            startNextRoundOrFinish(session, gameState);
            return GameActionResult.accepted(true);
        }

        return GameActionResult.accepted(false);
    }

    @Override
    public GameActionResult handlePlayerAbandoned(SessionState session, String abandonedPlayerUid) {
        StepByStepGameState gameState = requireGameState(session);
        if (gameState == null || gameState.isFinished() || session.getGamePhase() == null) {
            return GameActionResult.accepted(false);
        }

        if (session.isPlayerAbandoned(session.getCurrentTurnUid())) {
            StepByStepGameState.RoundState round = gameState.getCurrentRound();
            if (round == null) {
                finishGame(session, gameState);
            } else {
                round.setCompleted(true);
                round.setAnswerRevealed(true);
                startNextRoundOrFinish(session, gameState);
                skipAbandonedTurns(session, gameState);
            }
            return GameActionResult.accepted(true);
        }

        return GameActionResult.accepted(false);
    }

    private boolean isGuessPhase(SessionState session) {
        if (session.getGamePhase() == null) {
            return false;
        }
        String phaseType = session.getGamePhase().getPhaseType();
        return PhaseTypes.STEP_BY_STEP_OWNER_STEP.equals(phaseType)
                || PhaseTypes.STEP_BY_STEP_OPPONENT_ATTEMPT.equals(phaseType);
    }

    private void startOpponentAttempt(SessionState session, StepByStepGameState.RoundState round) {
        String opponentUid = session.otherPlayer(round.getOwnerUid());
        session.setTurnState(TurnState.single(round.getOwnerUid(), opponentUid));
        session.startGamePhase(
                PhaseTypes.STEP_BY_STEP_OPPONENT_ATTEMPT,
                1,
                OPPONENT_ATTEMPT_DURATION_MS
        );
    }

    private void startNextRoundOrFinish(SessionState session, StepByStepGameState gameState) {
        int nextRoundIndex = gameState.getCurrentRoundIndex() + 1;
        if (nextRoundIndex >= gameState.getRounds().size()) {
            finishGame(session, gameState);
            return;
        }

        gameState.setCurrentRoundIndex(nextRoundIndex);
        StepByStepGameState.RoundState nextRound = gameState.getCurrentRound();
        if (nextRound == null) {
            finishGame(session, gameState);
            return;
        }

        session.setTurnState(TurnState.single(nextRound.getOwnerUid(), nextRound.getOwnerUid()));
        session.startGamePhase(
                PhaseTypes.STEP_BY_STEP_OWNER_STEP,
                nextRoundIndex + 1,
                STEP_DURATION_MS
        );
        skipAbandonedTurns(session, gameState);
    }

    private void skipAbandonedTurns(SessionState session, StepByStepGameState gameState) {
        int guard = 0;
        while (guard++ < 4
                && !gameState.isFinished()
                && session.getCurrentTurnUid() != null
                && session.isPlayerAbandoned(session.getCurrentTurnUid())) {
            StepByStepGameState.RoundState round = gameState.getCurrentRound();
            if (round == null) {
                finishGame(session, gameState);
            } else {
                round.setCompleted(true);
                round.setAnswerRevealed(true);
                startNextRoundOrFinish(session, gameState);
            }
        }
    }

    private StepByStepGameState requireGameState(SessionState session) {
        if (session.getActiveGameState() instanceof StepByStepGameState) {
            return (StepByStepGameState) session.getActiveGameState();
        }
        return null;
    }

    private void applyScore(SessionState session, StepByStepGameState gameState, String playerUid, int scoreDelta) {
        session.addScore(playerUid, scoreDelta);
        gameState.setPlayer1Score(session.getPlayer1Score());
        gameState.setPlayer2Score(session.getPlayer2Score());
    }

    private void finishGame(SessionState session, StepByStepGameState gameState) {
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
