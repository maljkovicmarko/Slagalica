package com.example.slagalica.wsserver;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class StepByStepGameState implements GameState {
    private final List<RoundState> rounds;

    private int currentRoundIndex;
    private int player1Score;
    private int player2Score;
    private boolean finished;

    public StepByStepGameState(List<RoundState> rounds) {
        this.rounds = rounds == null ? Collections.emptyList() : new ArrayList<>(rounds);
        currentRoundIndex = 0;
        player1Score = 0;
        player2Score = 0;
        finished = false;
    }

    @Override
    public String getGameType() {
        return GameTypes.STEP_BY_STEP;
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
        private final String sourceQuestionId;
        private final String ownerUid;
        private final String answer;
        private final List<String> steps;
        private int currentStepIndex;
        private boolean completed;
        private boolean answerRevealed;

        public RoundState(String sourceQuestionId, String ownerUid, String answer, List<String> steps) {
            this.sourceQuestionId = sourceQuestionId;
            this.ownerUid = ownerUid;
            this.answer = answer;
            this.steps = steps == null ? Collections.emptyList() : new ArrayList<>(steps);
            currentStepIndex = 0;
            completed = false;
            answerRevealed = false;
        }

        public String getSourceQuestionId() {
            return sourceQuestionId;
        }

        public String getOwnerUid() {
            return ownerUid;
        }

        public String getAnswer() {
            return answer;
        }

        public List<String> getSteps() {
            return Collections.unmodifiableList(steps);
        }

        public int getCurrentStepIndex() {
            return currentStepIndex;
        }

        public void setCurrentStepIndex(int currentStepIndex) {
            this.currentStepIndex = currentStepIndex;
        }

        public boolean isCompleted() {
            return completed;
        }

        public void setCompleted(boolean completed) {
            this.completed = completed;
        }

        public boolean isAnswerRevealed() {
            return answerRevealed;
        }

        public void setAnswerRevealed(boolean answerRevealed) {
            this.answerRevealed = answerRevealed;
        }

        public JSONObject toJson() {
            JSONObject json = new JSONObject();
            json.put("sourceQuestionId", sourceQuestionId == null ? JSONObject.NULL : sourceQuestionId);
            json.put("ownerUid", ownerUid == null ? JSONObject.NULL : ownerUid);
            json.put("answer", answerRevealed ? answer : JSONObject.NULL);
            json.put("currentStepIndex", currentStepIndex);
            json.put("completed", completed);
            json.put("answerRevealed", answerRevealed);
            json.put("steps", buildStepsJson());
            return json;
        }

        private JSONArray buildStepsJson() {
            JSONArray json = new JSONArray();
            for (int i = 0; i < steps.size(); i++) {
                JSONObject stepJson = new JSONObject();
                stepJson.put("index", i);
                stepJson.put("label", "Korak " + (i + 1));
                boolean opened = answerRevealed || i <= currentStepIndex;
                stepJson.put("opened", opened);
                stepJson.put("value", opened ? steps.get(i) : JSONObject.NULL);
                json.put(stepJson);
            }
            return json;
        }
    }
}
