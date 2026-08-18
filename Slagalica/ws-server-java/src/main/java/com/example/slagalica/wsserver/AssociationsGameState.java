package com.example.slagalica.wsserver;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class AssociationsGameState implements GameState {
    public static final String TURN_MODE_OPEN_FIELD = "open_field";
    public static final String TURN_MODE_GUESS_OR_PASS = "guess_or_pass";

    private final List<RoundState> rounds;

    private int currentRoundIndex;
    private int player1Score;
    private int player2Score;
    private String turnMode;
    private boolean finished;

    public AssociationsGameState(List<RoundState> rounds) {
        this.rounds = rounds == null ? Collections.emptyList() : new ArrayList<>(rounds);
        currentRoundIndex = 0;
        player1Score = 0;
        player2Score = 0;
        turnMode = TURN_MODE_OPEN_FIELD;
        finished = false;
    }

    @Override
    public String getGameType() {
        return GameTypes.ASSOCIATIONS;
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

    public String getTurnMode() {
        return turnMode;
    }

    public void setTurnMode(String turnMode) {
        this.turnMode = turnMode;
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
        json.put("turnMode", turnMode);
        json.put("finished", finished);

        RoundState currentRound = getCurrentRound();
        json.put("currentRound", currentRound == null ? JSONObject.NULL : currentRound.toJson());
        return json;
    }

    public static class RoundState {
        private final String sourceQuestionId;
        private final String ownerUid;
        private final String finalAnswer;
        private final List<ColumnState> columns;
        private final Map<String, Set<Integer>> openedFieldIndexesByColumnId;
        private final Set<String> solvedColumnIds;
        private boolean finalSolved;

        public RoundState(String sourceQuestionId,
                          String ownerUid,
                          String finalAnswer,
                          List<ColumnState> columns) {
            this.sourceQuestionId = sourceQuestionId;
            this.ownerUid = ownerUid;
            this.finalAnswer = finalAnswer;
            this.columns = columns == null ? Collections.emptyList() : new ArrayList<>(columns);
            this.openedFieldIndexesByColumnId = new HashMap<>();
            this.solvedColumnIds = new HashSet<>();
            this.finalSolved = false;
            for (ColumnState column : this.columns) {
                openedFieldIndexesByColumnId.put(column.getId(), new HashSet<>());
            }
        }

        public String getSourceQuestionId() {
            return sourceQuestionId;
        }

        public String getOwnerUid() {
            return ownerUid;
        }

        public String getFinalAnswer() {
            return finalAnswer;
        }

        public List<ColumnState> getColumns() {
            return Collections.unmodifiableList(columns);
        }

        public Set<String> getSolvedColumnIds() {
            return solvedColumnIds;
        }

        public boolean isFinalSolved() {
            return finalSolved;
        }

        public void setFinalSolved(boolean finalSolved) {
            this.finalSolved = finalSolved;
        }

        public ColumnState findColumn(String columnId) {
            for (ColumnState column : columns) {
                if (column.getId().equals(columnId)) {
                    return column;
                }
            }
            return null;
        }

        public Set<Integer> getOpenedFieldIndexes(String columnId) {
            return openedFieldIndexesByColumnId.computeIfAbsent(columnId, ignored -> new HashSet<>());
        }

        public boolean isFieldOpened(String columnId, int fieldIndex) {
            return getOpenedFieldIndexes(columnId).contains(fieldIndex);
        }

        public void openField(String columnId, int fieldIndex) {
            getOpenedFieldIndexes(columnId).add(fieldIndex);
        }

        public int getOpenedFieldCount(String columnId) {
            return getOpenedFieldIndexes(columnId).size();
        }

        public int getUnopenedFieldCount(String columnId) {
            return Math.max(0, 4 - getOpenedFieldCount(columnId));
        }

        public boolean isColumnSolved(String columnId) {
            return solvedColumnIds.contains(columnId);
        }

        public void solveColumn(String columnId) {
            solvedColumnIds.add(columnId);
        }

        public boolean allColumnsSolved() {
            return solvedColumnIds.size() >= columns.size();
        }

        public JSONObject toJson() {
            JSONObject json = new JSONObject();
            json.put("sourceQuestionId", sourceQuestionId == null ? JSONObject.NULL : sourceQuestionId);
            json.put("ownerUid", ownerUid == null ? JSONObject.NULL : ownerUid);
            json.put("finalAnswer", finalSolved ? finalAnswer : JSONObject.NULL);
            json.put("finalSolved", finalSolved);
            json.put("columns", buildColumnsJson());
            return json;
        }

        private JSONArray buildColumnsJson() {
            JSONArray json = new JSONArray();
            for (ColumnState column : columns) {
                json.put(column.toJson(
                        getOpenedFieldIndexes(column.getId()),
                        finalSolved || isColumnSolved(column.getId())
                ));
            }
            return json;
        }
    }

    public static class ColumnState {
        private final String id;
        private final String solution;
        private final List<String> fields;

        public ColumnState(String id, String solution, List<String> fields) {
            this.id = id;
            this.solution = solution;
            this.fields = fields == null ? Collections.emptyList() : new ArrayList<>(fields);
        }

        public String getId() {
            return id;
        }

        public String getSolution() {
            return solution;
        }

        public List<String> getFields() {
            return Collections.unmodifiableList(fields);
        }

        public JSONObject toJson(Set<Integer> openedFieldIndexes, boolean solved) {
            JSONObject json = new JSONObject();
            json.put("id", id);
            json.put("solution", solved ? solution : JSONObject.NULL);
            json.put("solved", solved);

            JSONArray fieldsJson = new JSONArray();
            for (int i = 0; i < fields.size(); i++) {
                JSONObject fieldJson = new JSONObject();
                fieldJson.put("index", i);
                fieldJson.put("label", id + (i + 1));
                boolean opened = solved || (openedFieldIndexes != null && openedFieldIndexes.contains(i));
                fieldJson.put("opened", opened);
                fieldJson.put("value", opened ? fields.get(i) : JSONObject.NULL);
                fieldsJson.put(fieldJson);
            }
            json.put("fields", fieldsJson);
            return json;
        }
    }
}
