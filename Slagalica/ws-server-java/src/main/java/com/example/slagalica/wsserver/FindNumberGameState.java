package com.example.slagalica.wsserver;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class FindNumberGameState implements GameState {
    private final List<RoundState> rounds;

    private int currentRoundIndex;
    private int player1Score;
    private int player2Score;
    private boolean finished;

    public FindNumberGameState(List<RoundState> rounds) {
        this.rounds = rounds == null ? Collections.emptyList() : new ArrayList<>(rounds);
        currentRoundIndex = 0;
        player1Score = 0;
        player2Score = 0;
        finished = false;
    }

    @Override
    public String getGameType() {
        return GameTypes.FIND_THE_NUMBER;
    }

    @Override
    public boolean isFinished() {
        return finished;
    }

    public List<RoundState> getRounds() {
        return Collections.unmodifiableList(rounds);
    }

    public RoundState getCurrentRound() {
        if (currentRoundIndex < 0 || currentRoundIndex >= rounds.size()) {
            return null;
        }
        return rounds.get(currentRoundIndex);
    }

    public int getCurrentRoundIndex() {
        return currentRoundIndex;
    }

    public void setCurrentRoundIndex(int currentRoundIndex) {
        this.currentRoundIndex = currentRoundIndex;
    }

    public int getPlayer1Score() {
        return player1Score;
    }

    public void setPlayer1Score(int player1Score) {
        this.player1Score = player1Score;
    }

    public int getPlayer2Score() {
        return player2Score;
    }

    public void setPlayer2Score(int player2Score) {
        this.player2Score = player2Score;
    }

    public void setFinished(boolean finished) {
        this.finished = finished;
    }

    @Override
    public JSONObject toJson() {
        JSONObject json = new JSONObject();
        json.put("gameType", getGameType());
        json.put("currentRoundIndex", currentRoundIndex);
        json.put("roundNumber", currentRoundIndex + 1);
        json.put("totalRounds", rounds.size());
        json.put("player1Score", player1Score);
        json.put("player2Score", player2Score);
        json.put("finished", finished);

        RoundState currentRound = getCurrentRound();
        json.put("currentRound", currentRound == null ? JSONObject.NULL : currentRound.toJson());
        return json;
    }

    public static class RoundState {
        private final String ownerUid;
        private final int targetNumber;
        private final List<Integer> numbers;
        private boolean targetRevealed;
        private boolean numbersRevealed;
        private boolean completed;
        private Submission player1Submission;
        private Submission player2Submission;

        public RoundState(String ownerUid, int targetNumber, List<Integer> numbers) {
            this.ownerUid = ownerUid;
            this.targetNumber = targetNumber;
            this.numbers = numbers == null ? Collections.emptyList() : new ArrayList<>(numbers);
            targetRevealed = false;
            numbersRevealed = false;
            completed = false;
        }

        public String getOwnerUid() {
            return ownerUid;
        }

        public int getTargetNumber() {
            return targetNumber;
        }

        public List<Integer> getNumbers() {
            return Collections.unmodifiableList(numbers);
        }

        public boolean isTargetRevealed() {
            return targetRevealed;
        }

        public void setTargetRevealed(boolean targetRevealed) {
            this.targetRevealed = targetRevealed;
        }

        public boolean isNumbersRevealed() {
            return numbersRevealed;
        }

        public void setNumbersRevealed(boolean numbersRevealed) {
            this.numbersRevealed = numbersRevealed;
        }

        public boolean isCompleted() {
            return completed;
        }

        public void setCompleted(boolean completed) {
            this.completed = completed;
        }

        public Submission getPlayer1Submission() {
            return player1Submission;
        }

        public Submission getPlayer2Submission() {
            return player2Submission;
        }

        public Submission getSubmission(String playerUid, String player1Uid, String player2Uid) {
            if (playerUid != null && playerUid.equals(player1Uid)) {
                return player1Submission;
            }
            if (playerUid != null && playerUid.equals(player2Uid)) {
                return player2Submission;
            }
            return null;
        }

        public void setSubmission(String playerUid, String player1Uid, String player2Uid, Submission submission) {
            if (playerUid != null && playerUid.equals(player1Uid)) {
                player1Submission = submission;
            } else if (playerUid != null && playerUid.equals(player2Uid)) {
                player2Submission = submission;
            }
        }

        public boolean bothSubmitted() {
            return player1Submission != null && player2Submission != null;
        }

        public JSONObject toJson() {
            JSONObject json = new JSONObject();
            json.put("ownerUid", ownerUid == null ? JSONObject.NULL : ownerUid);
            json.put("targetNumber", targetRevealed ? targetNumber : JSONObject.NULL);
            json.put("targetRevealed", targetRevealed);
            json.put("numbers", numbersRevealed ? buildNumbersJson() : JSONObject.NULL);
            json.put("numbersRevealed", numbersRevealed);
            json.put("completed", completed);
            json.put("player1Submission", player1Submission == null ? JSONObject.NULL : player1Submission.toJson());
            json.put("player2Submission", player2Submission == null ? JSONObject.NULL : player2Submission.toJson());
            return json;
        }

        private JSONArray buildNumbersJson() {
            JSONArray json = new JSONArray();
            for (Integer number : numbers) {
                json.put(number);
            }
            return json;
        }
    }

    public static class Submission {
        private final String expression;
        private final double result;
        private final boolean valid;

        public Submission(String expression, double result, boolean valid) {
            this.expression = expression;
            this.result = result;
            this.valid = valid;
        }

        public String getExpression() {
            return expression;
        }

        public double getResult() {
            return result;
        }

        public boolean isValid() {
            return valid;
        }

        public JSONObject toJson() {
            JSONObject json = new JSONObject();
            json.put("expression", expression == null ? JSONObject.NULL : expression);
            json.put("result", result);
            json.put("valid", valid);
            return json;
        }
    }
}
