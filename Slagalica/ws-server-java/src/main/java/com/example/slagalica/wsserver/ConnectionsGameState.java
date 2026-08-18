package com.example.slagalica.wsserver;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ConnectionsGameState implements GameState {
    private final List<RoundState> rounds;

    private int currentRoundIndex;
    private int player1Score;
    private int player2Score;
    private boolean finished;

    public ConnectionsGameState(List<RoundState> rounds) {
        this.rounds = rounds == null ? Collections.emptyList() : new ArrayList<>(rounds);
        currentRoundIndex = 0;
        player1Score = 0;
        player2Score = 0;
        finished = false;
    }

    @Override
    public String getGameType() {
        return GameTypes.CONNECTIONS;
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
        private final String title;
        private final String ownerUid;
        private final List<LeftItem> leftItems;
        private final List<RightItem> rightItems;
        private final Set<String> resolvedLeftIds;
        private final Set<String> attemptedLeftIds;
        private final List<AttemptResult> attemptResults;

        public RoundState(String sourceQuestionId,
                          String title,
                          String ownerUid,
                          List<LeftItem> leftItems,
                          List<RightItem> rightItems) {
            this.sourceQuestionId = sourceQuestionId;
            this.title = title;
            this.ownerUid = ownerUid;
            this.leftItems = leftItems == null ? Collections.emptyList() : new ArrayList<>(leftItems);
            this.rightItems = rightItems == null ? Collections.emptyList() : new ArrayList<>(rightItems);
            this.resolvedLeftIds = new HashSet<>();
            this.attemptedLeftIds = new HashSet<>();
            this.attemptResults = new ArrayList<>();
        }

        public String getSourceQuestionId() {
            return sourceQuestionId;
        }

        public String getTitle() {
            return title;
        }

        public String getOwnerUid() {
            return ownerUid;
        }

        public List<LeftItem> getLeftItems() {
            return Collections.unmodifiableList(leftItems);
        }

        public List<RightItem> getRightItems() {
            return Collections.unmodifiableList(rightItems);
        }

        public Set<String> getResolvedLeftIds() {
            return resolvedLeftIds;
        }

        public Set<String> getAttemptedLeftIds() {
            return attemptedLeftIds;
        }

        public List<AttemptResult> getAttemptResults() {
            return attemptResults;
        }

        public LeftItem findLeftItem(String leftId) {
            for (LeftItem item : leftItems) {
                if (item.getId().equals(leftId)) {
                    return item;
                }
            }
            return null;
        }

        public RightItem findRightItem(String rightId) {
            for (RightItem item : rightItems) {
                if (item.getId().equals(rightId)) {
                    return item;
                }
            }
            return null;
        }

        public int getUnresolvedCount() {
            return leftItems.size() - resolvedLeftIds.size();
        }

        public boolean isCurrentAttemptComplete() {
            return resolvedLeftIds.size() + attemptedLeftIds.size() >= leftItems.size();
        }

        public void clearAttemptedLeftIds() {
            attemptedLeftIds.clear();
            attemptResults.clear();
        }

        public JSONObject toJson() {
            JSONObject json = new JSONObject();
            json.put("sourceQuestionId", sourceQuestionId == null ? JSONObject.NULL : sourceQuestionId);
            json.put("title", title == null ? JSONObject.NULL : title);
            json.put("ownerUid", ownerUid == null ? JSONObject.NULL : ownerUid);
            json.put("leftItems", buildLeftItemsJson());
            json.put("rightItems", buildRightItemsJson());
            json.put("resolvedLeftIds", buildStringArray(resolvedLeftIds));
            json.put("attemptedLeftIds", buildStringArray(attemptedLeftIds));
            json.put("attemptResults", buildAttemptResultsJson());
            return json;
        }

        private JSONArray buildLeftItemsJson() {
            JSONArray json = new JSONArray();
            for (LeftItem item : leftItems) {
                json.put(item.toJson());
            }
            return json;
        }

        private JSONArray buildRightItemsJson() {
            JSONArray json = new JSONArray();
            for (RightItem item : rightItems) {
                json.put(item.toJson());
            }
            return json;
        }

        private JSONArray buildStringArray(Set<String> values) {
            JSONArray json = new JSONArray();
            for (String value : values) {
                json.put(value);
            }
            return json;
        }

        private JSONArray buildAttemptResultsJson() {
            JSONArray json = new JSONArray();
            for (AttemptResult attemptResult : attemptResults) {
                json.put(attemptResult.toJson());
            }
            return json;
        }
    }

    public static class AttemptResult {
        private final String leftId;
        private final String rightId;
        private final boolean correct;

        public AttemptResult(String leftId, String rightId, boolean correct) {
            this.leftId = leftId;
            this.rightId = rightId;
            this.correct = correct;
        }

        public JSONObject toJson() {
            JSONObject json = new JSONObject();
            json.put("leftId", leftId);
            json.put("rightId", rightId);
            json.put("correct", correct);
            return json;
        }
    }

    public static class LeftItem {
        private final String id;
        private final String text;
        private final String matchId;

        public LeftItem(String id, String text, String matchId) {
            this.id = id;
            this.text = text;
            this.matchId = matchId;
        }

        public String getId() {
            return id;
        }

        public String getText() {
            return text;
        }

        public String getMatchId() {
            return matchId;
        }

        public JSONObject toJson() {
            JSONObject json = new JSONObject();
            json.put("id", id);
            json.put("text", text);
            json.put("matchId", matchId);
            return json;
        }
    }

    public static class RightItem {
        private final String id;
        private final String text;

        public RightItem(String id, String text) {
            this.id = id;
            this.text = text;
        }

        public String getId() {
            return id;
        }

        public String getText() {
            return text;
        }

        public JSONObject toJson() {
            JSONObject json = new JSONObject();
            json.put("id", id);
            json.put("text", text);
            return json;
        }
    }
}
