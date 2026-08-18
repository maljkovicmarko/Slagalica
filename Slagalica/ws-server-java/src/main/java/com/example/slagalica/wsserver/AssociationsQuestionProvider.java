package com.example.slagalica.wsserver;

import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.QueryDocumentSnapshot;
import com.google.cloud.firestore.QuerySnapshot;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

public class AssociationsQuestionProvider {
    private static final String COLLECTION_NAME = "association_questions";
    private static final int ROUNDS_TO_SELECT = 2;

    public List<AssociationsGameState.RoundState> selectRounds(String player1Uid, String player2Uid) {
        List<AssociationsGameState.RoundState> questionBank = fetchQuestionBank();
        if (questionBank.size() < ROUNDS_TO_SELECT) {
            questionBank = buildFallbackQuestions();
        }

        Collections.shuffle(questionBank);
        List<AssociationsGameState.RoundState> selected = new ArrayList<>();
        selected.add(copyWithOwner(questionBank.get(0), player1Uid));
        selected.add(copyWithOwner(questionBank.get(1), player2Uid));
        return selected;
    }

    private List<AssociationsGameState.RoundState> fetchQuestionBank() {
        try {
            Firestore firestore = FirebaseAdmin.getFirestore();
            QuerySnapshot snapshot = firestore.collection(COLLECTION_NAME).get().get();
            List<AssociationsGameState.RoundState> rounds = new ArrayList<>();
            for (QueryDocumentSnapshot document : snapshot.getDocuments()) {
                AssociationsGameState.RoundState round = mapRound(document.getId(), document.getData());
                if (round != null) {
                    rounds.add(round);
                }
            }
            return rounds;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("Firestore associations fetch interrupted: " + e.getMessage());
            return Collections.emptyList();
        } catch (ExecutionException | java.io.IOException e) {
            System.err.println("Firestore associations fetch failed: " + e.getMessage());
            return Collections.emptyList();
        }
    }

    private AssociationsGameState.RoundState mapRound(String sourceQuestionId, Map<String, Object> data) {
        if (data == null) {
            return null;
        }

        String finalAnswer = readString(data, "finalAnswer");
        List<AssociationsGameState.ColumnState> columns = readColumns(data);
        if (finalAnswer == null || columns.size() != 4) {
            return null;
        }

        return new AssociationsGameState.RoundState(sourceQuestionId, null, finalAnswer, columns);
    }

    private String readString(Map<String, Object> data, String fieldName) {
        Object value = data.get(fieldName);
        return value instanceof String ? (String) value : null;
    }

    private List<AssociationsGameState.ColumnState> readColumns(Map<String, Object> data) {
        List<Map<String, Object>> columnObjects = readMapList(data, "columns");
        List<AssociationsGameState.ColumnState> columns = new ArrayList<>();
        for (Map<String, Object> columnObject : columnObjects) {
            String id = readString(columnObject, "id");
            String solution = readString(columnObject, "solution");
            List<String> fieldValues = readStringList(columnObject, "fields");
            if (id != null && solution != null && fieldValues.size() == 4) {
                columns.add(new AssociationsGameState.ColumnState(id, solution, fieldValues));
            }
        }
        return columns;
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

    private List<String> readStringList(Map<String, Object> data, String fieldName) {
        Object value = data.get(fieldName);
        if (!(value instanceof List<?>)) {
            return Collections.emptyList();
        }

        List<String> strings = new ArrayList<>();
        for (Object item : (List<?>) value) {
            if (item instanceof String) {
                strings.add((String) item);
            }
        }
        return strings;
    }

    private AssociationsGameState.RoundState copyWithOwner(AssociationsGameState.RoundState round, String ownerUid) {
        return new AssociationsGameState.RoundState(
                round.getSourceQuestionId(),
                ownerUid,
                round.getFinalAnswer(),
                round.getColumns()
        );
    }

    private List<AssociationsGameState.RoundState> buildFallbackQuestions() {
        List<AssociationsGameState.RoundState> fallback = new ArrayList<>();
        fallback.add(new AssociationsGameState.RoundState(
                "fallback_associations_1",
                null,
                "Nauka",
                Arrays.asList(
                        column("A", "Elektrotehnika", "Tesla", "Edison", "Struja", "Napon"),
                        column("B", "Fizika", "Ajnštajn", "Njutn", "Kvant", "Sila"),
                        column("C", "Biologija", "Darvin", "Vrsta", "Evolucija", "Genetika"),
                        column("D", "Hemija", "Mendeljejev", "Element", "Periodni sistem", "Atom")
                )
        ));
        fallback.add(new AssociationsGameState.RoundState(
                "fallback_associations_2",
                null,
                "Evropa",
                Arrays.asList(
                        column("A", "Francuska", "Pariz", "Luvr", "Ajfelova kula", "Trikolor"),
                        column("B", "Italija", "Rim", "Koloseum", "Pica", "Vatikan"),
                        column("C", "Španija", "Madrid", "Flamenko", "Korida", "Barselona"),
                        column("D", "Nemačka", "Berlin", "Bavarska", "Oktoberfest", "Autoput")
                )
        ));
        return fallback;
    }

    private AssociationsGameState.ColumnState column(String id,
                                                     String solution,
                                                     String field1,
                                                     String field2,
                                                     String field3,
                                                     String field4) {
        return new AssociationsGameState.ColumnState(
                id,
                solution,
                Arrays.asList(field1, field2, field3, field4)
        );
    }
}
