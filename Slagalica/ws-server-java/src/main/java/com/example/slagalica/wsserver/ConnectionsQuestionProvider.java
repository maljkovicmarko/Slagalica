package com.example.slagalica.wsserver;

import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.QueryDocumentSnapshot;
import com.google.cloud.firestore.QuerySnapshot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

public class ConnectionsQuestionProvider {
    private static final String COLLECTION_NAME = "connecting_game_questions";
    private static final int ROUNDS_TO_SELECT = 2;

    public List<ConnectionsGameState.RoundState> selectRounds(String player1Uid, String player2Uid) {
        List<ConnectionsGameState.RoundState> questionBank = fetchQuestionBank();
        if (questionBank.size() < ROUNDS_TO_SELECT) {
            questionBank = buildFallbackQuestions();
        }

        Collections.shuffle(questionBank);
        List<ConnectionsGameState.RoundState> selected = new ArrayList<>();
        selected.add(copyWithOwner(questionBank.get(0), player1Uid));
        selected.add(copyWithOwner(questionBank.get(1), player2Uid));
        return selected;
    }

    private List<ConnectionsGameState.RoundState> fetchQuestionBank() {
        try {
            Firestore firestore = FirebaseAdmin.getFirestore();
            QuerySnapshot snapshot = firestore.collection(COLLECTION_NAME).get().get();
            List<ConnectionsGameState.RoundState> rounds = new ArrayList<>();
            for (QueryDocumentSnapshot document : snapshot.getDocuments()) {
                ConnectionsGameState.RoundState round = mapRound(document.getId(), document.getData());
                if (round != null) {
                    rounds.add(round);
                }
            }
            return rounds;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("Firestore connections fetch interrupted: " + e.getMessage());
            return Collections.emptyList();
        } catch (ExecutionException | java.io.IOException e) {
            System.err.println("Firestore connections fetch failed: " + e.getMessage());
            return Collections.emptyList();
        }
    }

    private ConnectionsGameState.RoundState mapRound(String sourceQuestionId, Map<String, Object> data) {
        if (data == null) {
            return null;
        }

        String title = readString(data, "title");
        List<ConnectionsGameState.LeftItem> leftItems = readLeftItems(data);
        List<ConnectionsGameState.RightItem> rightItems = readRightItems(data);

        if (title == null || leftItems.size() < 5 || rightItems.size() < 5) {
            return null;
        }
        return new ConnectionsGameState.RoundState(sourceQuestionId, title, null, leftItems, rightItems);
    }

    private String readString(Map<String, Object> data, String fieldName) {
        Object value = data.get(fieldName);
        return value instanceof String ? (String) value : null;
    }

    private List<ConnectionsGameState.LeftItem> readLeftItems(Map<String, Object> data) {
        List<Map<String, Object>> itemObjects = readMapList(data, "leftItems");
        List<ConnectionsGameState.LeftItem> items = new ArrayList<>();
        for (Map<String, Object> itemObject : itemObjects) {
            String id = readString(itemObject, "id");
            String text = readString(itemObject, "text");
            String matchId = readString(itemObject, "matchId");
            if (id != null && text != null && matchId != null) {
                items.add(new ConnectionsGameState.LeftItem(id, text, matchId));
            }
        }
        return items;
    }

    private List<ConnectionsGameState.RightItem> readRightItems(Map<String, Object> data) {
        List<Map<String, Object>> itemObjects = readMapList(data, "rightItems");
        List<ConnectionsGameState.RightItem> items = new ArrayList<>();
        for (Map<String, Object> itemObject : itemObjects) {
            String id = readString(itemObject, "id");
            String text = readString(itemObject, "text");
            if (id != null && text != null) {
                items.add(new ConnectionsGameState.RightItem(id, text));
            }
        }
        return items;
    }

    private List<Map<String, Object>> readMapList(Map<String, Object> data, String fieldName) {
        Object value = data.get(fieldName);
        if (!(value instanceof List<?>)) {
            return Collections.emptyList();
        }

        List<Map<String, Object>> maps = new ArrayList<>();
        for (Object item : (List<?>) value) {
            if (item instanceof Map<?, ?>) {
                @SuppressWarnings("unchecked")
                Map<String, Object> map = (Map<String, Object>) item;
                maps.add(map);
            }
        }
        return maps;
    }

    private ConnectionsGameState.RoundState copyWithOwner(ConnectionsGameState.RoundState round, String ownerUid) {
        return new ConnectionsGameState.RoundState(
                round.getSourceQuestionId(),
                round.getTitle(),
                ownerUid,
                round.getLeftItems(),
                round.getRightItems()
        );
    }

    private List<ConnectionsGameState.RoundState> buildFallbackQuestions() {
        List<ConnectionsGameState.RoundState> fallback = new ArrayList<>();
        fallback.add(new ConnectionsGameState.RoundState(
                "fallback_connections_1",
                "Poveži naučnika sa oblašću",
                null,
                buildLeftItems("Nikola Tesla", "Albert Ajnštajn", "Čarls Darvin", "Dmitrij Mendeljejev", "Pitagora"),
                buildRightItems("Elektrotehnika", "Fizika", "Biologija", "Hemija", "Matematika")
        ));
        fallback.add(new ConnectionsGameState.RoundState(
                "fallback_connections_2",
                "Poveži državu i glavni grad",
                null,
                buildLeftItems("Srbija", "Francuska", "Italija", "Nemačka", "Španija"),
                buildRightItems("Beograd", "Pariz", "Rim", "Berlin", "Madrid")
        ));
        return fallback;
    }

    private List<ConnectionsGameState.LeftItem> buildLeftItems(String text1,
                                                              String text2,
                                                              String text3,
                                                              String text4,
                                                              String text5) {
        List<ConnectionsGameState.LeftItem> items = new ArrayList<>();
        items.add(new ConnectionsGameState.LeftItem("left_1", text1, "right_1"));
        items.add(new ConnectionsGameState.LeftItem("left_2", text2, "right_2"));
        items.add(new ConnectionsGameState.LeftItem("left_3", text3, "right_3"));
        items.add(new ConnectionsGameState.LeftItem("left_4", text4, "right_4"));
        items.add(new ConnectionsGameState.LeftItem("left_5", text5, "right_5"));
        return items;
    }

    private List<ConnectionsGameState.RightItem> buildRightItems(String text1,
                                                                 String text2,
                                                                 String text3,
                                                                 String text4,
                                                                 String text5) {
        List<ConnectionsGameState.RightItem> items = new ArrayList<>();
        items.add(new ConnectionsGameState.RightItem("right_1", text1));
        items.add(new ConnectionsGameState.RightItem("right_2", text2));
        items.add(new ConnectionsGameState.RightItem("right_3", text3));
        items.add(new ConnectionsGameState.RightItem("right_4", text4));
        items.add(new ConnectionsGameState.RightItem("right_5", text5));
        return items;
    }
}
