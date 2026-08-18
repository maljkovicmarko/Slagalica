package com.example.slagalica.wsserver;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class GuessCombinationGameState implements GameState {
    public static final List<String> SYMBOLS = Collections.unmodifiableList(Arrays.asList(
            "SKOCKO",
            "SQUARE",
            "CIRCLE",
            "HEART",
            "TRIANGLE",
            "STAR"
    ));

    private final List<RoundState> rounds;

    private int currentRoundIndex;
    private int player1Score;
    private int player2Score;
    private boolean finished;

    public GuessCombinationGameState(List<RoundState> rounds) {
        this.rounds = rounds == null ? Collections.emptyList() : new ArrayList<>(rounds);
        currentRoundIndex = 0;
        player1Score = 0;
        player2Score = 0;
        finished = false;
    }

    @Override
    public String getGameType() {
        return GameTypes.GUESS_THE_COMBINATION;
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
        private final List<String> targetCombination;
        private final List<AttemptResult> ownerAttempts;
        private AttemptResult opponentAttempt;
        private boolean completed;

        public RoundState(String ownerUid, List<String> targetCombination) {
            this.ownerUid = ownerUid;
            this.targetCombination = targetCombination == null ? Collections.emptyList() : new ArrayList<>(targetCombination);
            ownerAttempts = new ArrayList<>();
            completed = false;
        }

        public String getOwnerUid() {
            return ownerUid;
        }

        public List<String> getTargetCombination() {
            return Collections.unmodifiableList(targetCombination);
        }

        public List<AttemptResult> getOwnerAttempts() {
            return ownerAttempts;
        }

        public AttemptResult getOpponentAttempt() {
            return opponentAttempt;
        }

        public void setOpponentAttempt(AttemptResult opponentAttempt) {
            this.opponentAttempt = opponentAttempt;
        }

        public boolean isCompleted() {
            return completed;
        }

        public void setCompleted(boolean completed) {
            this.completed = completed;
        }

        public JSONObject toJson() {
            JSONObject json = new JSONObject();
            json.put("ownerUid", ownerUid == null ? JSONObject.NULL : ownerUid);
            json.put("completed", completed);
            json.put("targetCombination", completed ? buildStringArray(targetCombination) : JSONObject.NULL);
            json.put("ownerAttempts", buildAttemptsJson(ownerAttempts));
            json.put("opponentAttempt", opponentAttempt == null ? JSONObject.NULL : opponentAttempt.toJson());
            return json;
        }

        private JSONArray buildAttemptsJson(List<AttemptResult> attempts) {
            JSONArray json = new JSONArray();
            for (AttemptResult attempt : attempts) {
                json.put(attempt.toJson());
            }
            return json;
        }

        private JSONArray buildStringArray(List<String> values) {
            JSONArray json = new JSONArray();
            for (String value : values) {
                json.put(value);
            }
            return json;
        }
    }

    public static class AttemptResult {
        private final String playerUid;
        private final List<String> symbols;
        private final int exactMatches;
        private final int symbolOnlyMatches;

        public AttemptResult(String playerUid, List<String> symbols, int exactMatches, int symbolOnlyMatches) {
            this.playerUid = playerUid;
            this.symbols = symbols == null ? Collections.emptyList() : new ArrayList<>(symbols);
            this.exactMatches = exactMatches;
            this.symbolOnlyMatches = symbolOnlyMatches;
        }

        public int getExactMatches() {
            return exactMatches;
        }

        public JSONObject toJson() {
            JSONObject json = new JSONObject();
            json.put("playerUid", playerUid == null ? JSONObject.NULL : playerUid);
            json.put("symbols", buildStringArray(symbols));
            json.put("exactMatches", exactMatches);
            json.put("symbolOnlyMatches", symbolOnlyMatches);
            return json;
        }

        private JSONArray buildStringArray(List<String> values) {
            JSONArray json = new JSONArray();
            for (String value : values) {
                json.put(value);
            }
            return json;
        }
    }
}
