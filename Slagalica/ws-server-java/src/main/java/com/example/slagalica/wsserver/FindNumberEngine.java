package com.example.slagalica.wsserver;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FindNumberEngine implements GameEngine {
    private static final String ACTION_STOP_TARGET = "find_number_stop_target";
    private static final String ACTION_STOP_NUMBERS = "find_number_stop_numbers";
    private static final String ACTION_SUBMIT = "find_number_submit";
    private static final long STOP_DURATION_MS = 5000L;
    private static final long SOLVE_DURATION_MS = 60000L;

    @Override
    public String getGameType() {
        return GameTypes.FIND_THE_NUMBER;
    }

    @Override
    public GameActionResult handleAction(SessionState session, String playerUid, GameAction action) {
        FindNumberGameState gameState = requireGameState(session);
        if (gameState == null || gameState.isFinished()) {
            return GameActionResult.rejected("Find Number state is not active.");
        }

        FindNumberGameState.RoundState round = gameState.getCurrentRound();
        if (round == null) {
            finishGame(session, gameState);
            return GameActionResult.accepted(true);
        }

        String actionType = action.getActionType();
        String phaseType = session.getGamePhase() == null ? null : session.getGamePhase().getPhaseType();
        if (ACTION_STOP_TARGET.equals(actionType)) {
            if (!PhaseTypes.FIND_NUMBER_TARGET_STOP.equals(phaseType)) {
                return GameActionResult.rejected("Target stop is not allowed in the current phase.");
            }
            revealTargetAndStartNumbersStop(session, gameState, round);
            return GameActionResult.accepted(true);
        }
        if (ACTION_STOP_NUMBERS.equals(actionType)) {
            if (!PhaseTypes.FIND_NUMBER_NUMBERS_STOP.equals(phaseType)) {
                return GameActionResult.rejected("Numbers stop is not allowed in the current phase.");
            }
            revealNumbersAndStartSolve(session, gameState, round);
            return GameActionResult.accepted(true);
        }
        if (ACTION_SUBMIT.equals(actionType)) {
            if (!PhaseTypes.FIND_NUMBER_SOLVE.equals(phaseType)) {
                return GameActionResult.rejected("Expression submit is not allowed in the current phase.");
            }
            return submitExpression(session, gameState, round, playerUid, action);
        }

        return GameActionResult.rejected("Unsupported Find Number action.");
    }

    @Override
    public GameActionResult handleTimeout(SessionState session, long phaseVersion) {
        if (session.getGamePhase() == null || session.getGamePhase().getPhaseVersion() != phaseVersion) {
            return GameActionResult.accepted(false);
        }

        FindNumberGameState gameState = requireGameState(session);
        if (gameState == null || gameState.isFinished()) {
            return GameActionResult.accepted(false);
        }

        FindNumberGameState.RoundState round = gameState.getCurrentRound();
        if (round == null) {
            finishGame(session, gameState);
            return GameActionResult.accepted(true);
        }

        String phaseType = session.getGamePhase().getPhaseType();
        if (PhaseTypes.FIND_NUMBER_TARGET_STOP.equals(phaseType)) {
            revealTargetAndStartNumbersStop(session, gameState, round);
            return GameActionResult.accepted(true);
        }
        if (PhaseTypes.FIND_NUMBER_NUMBERS_STOP.equals(phaseType)) {
            revealNumbersAndStartSolve(session, gameState, round);
            return GameActionResult.accepted(true);
        }
        if (PhaseTypes.FIND_NUMBER_SOLVE.equals(phaseType)) {
            scoreRoundAndAdvance(session, gameState, round);
            return GameActionResult.accepted(true);
        }

        return GameActionResult.accepted(false);
    }

    @Override
    public GameActionResult handlePlayerAbandoned(SessionState session, String abandonedPlayerUid) {
        FindNumberGameState gameState = requireGameState(session);
        if (gameState == null || gameState.isFinished() || session.getGamePhase() == null) {
            return GameActionResult.accepted(false);
        }

        FindNumberGameState.RoundState round = gameState.getCurrentRound();
        if (round == null) {
            finishGame(session, gameState);
            return GameActionResult.accepted(true);
        }

        String remainingPlayerUid = session.getRemainingPlayerUid();
        String phaseType = session.getGamePhase().getPhaseType();
        if (PhaseTypes.FIND_NUMBER_SOLVE.equals(phaseType)) {
            addEmptySubmissionIfMissing(session, round, abandonedPlayerUid);
            if (round.getSubmission(remainingPlayerUid, session.getPlayer1Uid(), session.getPlayer2Uid()) != null) {
                scoreRoundAndAdvance(session, gameState, round);
            } else {
                session.setTurnState(TurnState.single(round.getOwnerUid(), remainingPlayerUid));
            }
            return GameActionResult.accepted(true);
        }

        if (session.isPlayerAbandoned(session.getCurrentTurnUid())) {
            session.setTurnState(TurnState.single(round.getOwnerUid(), remainingPlayerUid));
            return GameActionResult.accepted(true);
        }

        return GameActionResult.accepted(false);
    }

    private GameActionResult submitExpression(SessionState session,
                                              FindNumberGameState gameState,
                                              FindNumberGameState.RoundState round,
                                              String playerUid,
                                              GameAction action) {
        if (round.getSubmission(playerUid, session.getPlayer1Uid(), session.getPlayer2Uid()) != null) {
            return GameActionResult.rejected("Expression already submitted.");
        }

        String expression = action.getData().optString("expression", null);
        if (expression == null || expression.isBlank()) {
            round.setSubmission(
                    playerUid,
                    session.getPlayer1Uid(),
                    session.getPlayer2Uid(),
                    new FindNumberGameState.Submission("", 0, false)
            );
        } else {
            FindNumberGameState.Submission submission = buildSubmission(expression, round.getNumbers());
            round.setSubmission(playerUid, session.getPlayer1Uid(), session.getPlayer2Uid(), submission);
        }

        if (round.bothSubmitted()) {
            scoreRoundAndAdvance(session, gameState, round);
        } else if (session.getAbandonedByUid() != null) {
            addEmptySubmissionIfMissing(session, round, session.getAbandonedByUid());
            scoreRoundAndAdvance(session, gameState, round);
        }
        return GameActionResult.accepted(true);
    }

    private void addEmptySubmissionIfMissing(SessionState session,
                                             FindNumberGameState.RoundState round,
                                             String playerUid) {
        if (playerUid == null || round.getSubmission(playerUid, session.getPlayer1Uid(), session.getPlayer2Uid()) != null) {
            return;
        }
        round.setSubmission(
                playerUid,
                session.getPlayer1Uid(),
                session.getPlayer2Uid(),
                new FindNumberGameState.Submission("", 0, false)
        );
    }

    private FindNumberGameState.Submission buildSubmission(String expression, List<Integer> numbers) {
        try {
            List<Integer> usedNumbers = extractNumbers(expression);
            if (!usesAvailableNumbers(usedNumbers, numbers)) {
                return new FindNumberGameState.Submission(expression, 0, false);
            }
            double result = new ExpressionParser(expression).parse();
            if (Double.isNaN(result) || Double.isInfinite(result)) {
                return new FindNumberGameState.Submission(expression, 0, false);
            }
            return new FindNumberGameState.Submission(expression, result, true);
        } catch (Exception e) {
            return new FindNumberGameState.Submission(expression, 0, false);
        }
    }

    private void scoreRoundAndAdvance(SessionState session,
                                      FindNumberGameState gameState,
                                      FindNumberGameState.RoundState round) {
        FindNumberGameState.Submission player1Submission = round.getPlayer1Submission();
        FindNumberGameState.Submission player2Submission = round.getPlayer2Submission();

        boolean player1Exact = isExact(player1Submission, round.getTargetNumber());
        boolean player2Exact = isExact(player2Submission, round.getTargetNumber());

        if (player1Exact) {
            applyScore(session, gameState, session.getPlayer1Uid(), 10);
        }
        if (player2Exact) {
            applyScore(session, gameState, session.getPlayer2Uid(), 10);
        }

        if (!player1Exact && !player2Exact) {
            int player1Distance = distance(player1Submission, round.getTargetNumber());
            int player2Distance = distance(player2Submission, round.getTargetNumber());
            if (player1Distance != Integer.MAX_VALUE || player2Distance != Integer.MAX_VALUE) {
                if (player1Distance < player2Distance) {
                    applyScore(session, gameState, session.getPlayer1Uid(), 5);
                } else if (player2Distance < player1Distance) {
                    applyScore(session, gameState, session.getPlayer2Uid(), 5);
                } else if (!session.isPlayerAbandoned(round.getOwnerUid())) {
                    applyScore(session, gameState, round.getOwnerUid(), 5);
                }
            }
        }

        round.setCompleted(true);
        startNextRoundOrFinish(session, gameState);
    }

    private boolean isExact(FindNumberGameState.Submission submission, int targetNumber) {
        return submission != null && submission.isValid() && Math.abs(submission.getResult() - targetNumber) < 0.0001;
    }

    private int distance(FindNumberGameState.Submission submission, int targetNumber) {
        if (submission == null || !submission.isValid()) {
            return Integer.MAX_VALUE;
        }
        if (Math.abs(submission.getResult()) < 0.0001) {
            return Integer.MAX_VALUE;
        }
        return (int) Math.round(Math.abs(submission.getResult() - targetNumber));
    }

    private void revealTargetAndStartNumbersStop(SessionState session,
                                                 FindNumberGameState gameState,
                                                 FindNumberGameState.RoundState round) {
        round.setTargetRevealed(true);
        String activePlayerUid = session.isPlayerAbandoned(round.getOwnerUid())
                ? session.getRemainingPlayerUid()
                : round.getOwnerUid();
        session.setTurnState(TurnState.single(round.getOwnerUid(), activePlayerUid));
        session.startGamePhase(
                PhaseTypes.FIND_NUMBER_NUMBERS_STOP,
                gameState.getCurrentRoundIndex() + 1,
                STOP_DURATION_MS
        );
    }

    private void revealNumbersAndStartSolve(SessionState session,
                                            FindNumberGameState gameState,
                                            FindNumberGameState.RoundState round) {
        round.setTargetRevealed(true);
        round.setNumbersRevealed(true);
        if (session.getAbandonedByUid() == null) {
            session.setTurnState(TurnState.both(
                    round.getOwnerUid(),
                    round.getOwnerUid(),
                    session.getPlayer1Uid(),
                    session.getPlayer2Uid()
            ));
        } else {
            session.setTurnState(TurnState.single(round.getOwnerUid(), session.getRemainingPlayerUid()));
            addEmptySubmissionIfMissing(session, round, session.getAbandonedByUid());
        }
        session.startGamePhase(
                PhaseTypes.FIND_NUMBER_SOLVE,
                gameState.getCurrentRoundIndex() + 1,
                SOLVE_DURATION_MS
        );
    }

    private void startNextRoundOrFinish(SessionState session, FindNumberGameState gameState) {
        int nextRoundIndex = gameState.getCurrentRoundIndex() + 1;
        if (nextRoundIndex >= gameState.getRounds().size()) {
            finishGame(session, gameState);
            return;
        }

        gameState.setCurrentRoundIndex(nextRoundIndex);
        FindNumberGameState.RoundState nextRound = gameState.getCurrentRound();
        if (nextRound == null) {
            finishGame(session, gameState);
            return;
        }

        String activePlayerUid = session.isPlayerAbandoned(nextRound.getOwnerUid())
                ? session.getRemainingPlayerUid()
                : nextRound.getOwnerUid();
        session.setTurnState(TurnState.single(nextRound.getOwnerUid(), activePlayerUid));
        session.startGamePhase(
                PhaseTypes.FIND_NUMBER_TARGET_STOP,
                nextRoundIndex + 1,
                STOP_DURATION_MS
        );
    }

    private List<Integer> extractNumbers(String expression) {
        List<Integer> numbers = new ArrayList<>();
        int index = 0;
        while (index < expression.length()) {
            char character = expression.charAt(index);
            if (Character.isDigit(character)) {
                int start = index;
                while (index < expression.length() && Character.isDigit(expression.charAt(index))) {
                    index++;
                }
                numbers.add(Integer.parseInt(expression.substring(start, index)));
            } else {
                index++;
            }
        }
        return numbers;
    }

    private boolean usesAvailableNumbers(List<Integer> usedNumbers, List<Integer> availableNumbers) {
        Map<Integer, Integer> counts = new HashMap<>();
        for (Integer number : availableNumbers) {
            counts.put(number, counts.getOrDefault(number, 0) + 1);
        }
        for (Integer number : usedNumbers) {
            int count = counts.getOrDefault(number, 0);
            if (count <= 0) {
                return false;
            }
            counts.put(number, count - 1);
        }
        return true;
    }

    private FindNumberGameState requireGameState(SessionState session) {
        if (session.getActiveGameState() instanceof FindNumberGameState) {
            return (FindNumberGameState) session.getActiveGameState();
        }
        return null;
    }

    private void applyScore(SessionState session, FindNumberGameState gameState, String playerUid, int scoreDelta) {
        session.addScore(playerUid, scoreDelta);
        gameState.setPlayer1Score(session.getPlayer1Score());
        gameState.setPlayer2Score(session.getPlayer2Score());
    }

    private void finishGame(SessionState session, FindNumberGameState gameState) {
        gameState.setFinished(true);
        session.setTurnState(TurnState.nobody(session.getPlayer1Uid()));
        session.setGamePhase(null);
    }

    private static class ExpressionParser {
        private final String input;
        private int pos = -1;
        private int ch;

        ExpressionParser(String input) {
            this.input = input;
        }

        double parse() {
            nextChar();
            double value = parseExpression();
            if (pos < input.length()) {
                throw new RuntimeException("Unexpected character");
            }
            return value;
        }

        private void nextChar() {
            ch = (++pos < input.length()) ? input.charAt(pos) : -1;
        }

        private boolean eat(int charToEat) {
            while (ch == ' ') {
                nextChar();
            }
            if (ch == charToEat) {
                nextChar();
                return true;
            }
            return false;
        }

        private double parseExpression() {
            double value = parseTerm();
            while (true) {
                if (eat('+')) {
                    value += parseTerm();
                } else if (eat('-')) {
                    value -= parseTerm();
                } else {
                    return value;
                }
            }
        }

        private double parseTerm() {
            double value = parseFactor();
            while (true) {
                if (eat('*')) {
                    value *= parseFactor();
                } else if (eat('/')) {
                    value /= parseFactor();
                } else {
                    return value;
                }
            }
        }

        private double parseFactor() {
            if (eat('+')) return parseFactor();
            if (eat('-')) return -parseFactor();

            double value;
            int startPos = this.pos;
            if (eat('(')) {
                value = parseExpression();
                if (!eat(')')) {
                    throw new RuntimeException("Missing parenthesis");
                }
            } else if (ch >= '0' && ch <= '9') {
                while (ch >= '0' && ch <= '9') {
                    nextChar();
                }
                value = Double.parseDouble(input.substring(startPos, this.pos));
            } else {
                throw new RuntimeException("Unexpected character");
            }
            return value;
        }
    }
}
