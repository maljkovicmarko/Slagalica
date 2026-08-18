package com.example.slagalica.wsserver;

import com.example.slagalica.Model.Question;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.QueryDocumentSnapshot;
import com.google.cloud.firestore.QuerySnapshot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

public class GeneralKnowledgeQuestionProvider {
    private static final String COLLECTION_NAME = "general_knowledge_questions";
    private static final int QUESTIONS_TO_SELECT = 5;

    public List<Question> selectQuestions() {
        List<Question> questionBank = fetchQuestionBank();
        if (questionBank.size() < QUESTIONS_TO_SELECT) {
            return buildFallbackQuestions();
        }

        Collections.shuffle(questionBank);
        return new ArrayList<>(questionBank.subList(0, QUESTIONS_TO_SELECT));
    }

    private List<Question> fetchQuestionBank() {
        try {
            Firestore firestore = FirebaseAdmin.getFirestore();
            QuerySnapshot snapshot = firestore.collection(COLLECTION_NAME).get().get();
            List<Question> questions = new ArrayList<>();
            for (QueryDocumentSnapshot document : snapshot.getDocuments()) {
                Question question = mapQuestion(document.getData());
                if (question != null) {
                    questions.add(question);
                }
            }
            return questions;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("Firestore general knowledge fetch interrupted: " + e.getMessage());
            return Collections.emptyList();
        } catch (ExecutionException | java.io.IOException e) {
            System.err.println("Firestore general knowledge fetch failed: " + e.getMessage());
            return Collections.emptyList();
        }
    }

    private Question mapQuestion(Map<String, Object> data) {
        if (data == null) {
            return null;
        }

        String questionText = readString(data, "question");
        List<String> answers = readStringList(data, "answers");
        Integer answerId = readInteger(data, "answer_id");

        if (questionText == null || answers.size() < 4 || answerId == null) {
            return null;
        }

        return new Question(
                questionText,
                answers.get(0),
                answers.get(1),
                answers.get(2),
                answers.get(3),
                answerId
        );
    }

    private String readString(Map<String, Object> data, String fieldName) {
        Object value = data.get(fieldName);
        return value instanceof String ? (String) value : null;
    }

    private Integer readInteger(Map<String, Object> data, String fieldName) {
        Object value = data.get(fieldName);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        if (value instanceof String && !((String) value).isBlank()) {
            return Integer.parseInt((String) value);
        }
        return null;
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

    private List<Question> buildFallbackQuestions() {
        List<Question> fallbackQuestions = new ArrayList<>();
        fallbackQuestions.add(new Question(
                "Koji bend je objavio album 'Abbey Road'?",
                "Queen",
                "The Beatles",
                "Pink Floyd",
                "The Rolling Stones",
                2
        ));
        fallbackQuestions.add(new Question(
                "Koliko igrača jedan fudbalski tim ima na terenu na početku utakmice?",
                "9",
                "10",
                "11",
                "12",
                3
        ));
        fallbackQuestions.add(new Question(
                "Koje godine je završen Drugi svetski rat?",
                "1943",
                "1944",
                "1945",
                "1946",
                3
        ));
        fallbackQuestions.add(new Question(
                "Ko je komponovao delo 'Četiri godišnja doba'?",
                "Mocart",
                "Bah",
                "Betoven",
                "Vivaldi",
                4
        ));
        fallbackQuestions.add(new Question(
                "Ko je bio prvi čovek koji je kročio na Mesec?",
                "Jurij Gagarin",
                "Baz Oldrin",
                "Nil Armstrong",
                "Džon Glen",
                3
        ));
        return fallbackQuestions;
    }
}
