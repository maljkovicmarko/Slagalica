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

public class StepByStepQuestionProvider {
    private static final String COLLECTION_NAME = "step_by_step_questions";
    private static final int ROUNDS_TO_SELECT = 2;

    public List<StepByStepGameState.RoundState> selectRounds(String player1Uid, String player2Uid) {
        List<StepByStepGameState.RoundState> questionBank = fetchQuestionBank();
        if (questionBank.size() < ROUNDS_TO_SELECT) {
            questionBank = buildFallbackQuestions();
        }

        Collections.shuffle(questionBank);
        List<StepByStepGameState.RoundState> selected = new ArrayList<>();
        selected.add(copyWithOwner(questionBank.get(0), player1Uid));
        selected.add(copyWithOwner(questionBank.get(1), player2Uid));
        return selected;
    }

    private List<StepByStepGameState.RoundState> fetchQuestionBank() {
        try {
            Firestore firestore = FirebaseAdmin.getFirestore();
            QuerySnapshot snapshot = firestore.collection(COLLECTION_NAME).get().get();
            List<StepByStepGameState.RoundState> rounds = new ArrayList<>();
            for (QueryDocumentSnapshot document : snapshot.getDocuments()) {
                StepByStepGameState.RoundState round = mapRound(document.getId(), document.getData());
                if (round != null) {
                    rounds.add(round);
                }
            }
            return rounds;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("Firestore step-by-step fetch interrupted: " + e.getMessage());
            return Collections.emptyList();
        } catch (ExecutionException | java.io.IOException e) {
            System.err.println("Firestore step-by-step fetch failed: " + e.getMessage());
            return Collections.emptyList();
        }
    }

    private StepByStepGameState.RoundState mapRound(String sourceQuestionId, Map<String, Object> data) {
        if (data == null) {
            return null;
        }

        String answer = readString(data, "answer");
        List<String> steps = readStringList(data, "steps");
        if (answer == null || steps.size() != 7) {
            return null;
        }

        return new StepByStepGameState.RoundState(sourceQuestionId, null, answer, steps);
    }

    private String readString(Map<String, Object> data, String fieldName) {
        Object value = data.get(fieldName);
        return value instanceof String ? (String) value : null;
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

    private StepByStepGameState.RoundState copyWithOwner(StepByStepGameState.RoundState round, String ownerUid) {
        return new StepByStepGameState.RoundState(
                round.getSourceQuestionId(),
                ownerUid,
                round.getAnswer(),
                round.getSteps()
        );
    }

    private List<StepByStepGameState.RoundState> buildFallbackQuestions() {
        List<StepByStepGameState.RoundState> fallback = new ArrayList<>();
        fallback.add(new StepByStepGameState.RoundState(
                "fallback_step_by_step_1",
                null,
                "atom",
                Arrays.asList(
                        "Ima veze sa materijom",
                        "Ne vidi se golim okom",
                        "Može biti deo molekula",
                        "Ima jezgro",
                        "Sadrži protone",
                        "Sadrži neutrone",
                        "Sadrži elektrone"
                )
        ));
        fallback.add(new StepByStepGameState.RoundState(
                "fallback_step_by_step_2",
                null,
                "Nikola Tesla",
                Arrays.asList(
                        "Rođen je u Smiljanu",
                        "Radio je u oblasti elektrotehnike",
                        "Bio je izumitelj i inženjer",
                        "Radio je sa naizmeničnom strujom",
                        "Imao je sukob ideja sa Tomasom Edisonom",
                        "Po njemu se zove jedinica za magnetnu indukciju",
                        "Poznat je kao jedan od najvećih srpskih naučnika"
                )
        ));
        return fallback;
    }
}
