package com.example.slagalica.Services;

import com.example.slagalica.Model.GameSession;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

public class GameSessionService {

    private final FirebaseFirestore db;

    public GameSessionService() {
        db = FirebaseFirestore.getInstance();
    }

    public void createSession(
            String player1UID,
            String player2UID,
            String type,
            OnSessionCreated callback
    ) {
        GameSession session = new GameSession(player1UID, player2UID, type);

        db.collection("game_sessions")
                .add(session)
                .addOnSuccessListener(documentReference ->
                        callback.onSuccess(documentReference.getId())
                )
                .addOnFailureListener(e ->
                        callback.onFailure(e.getMessage())
                );
    }

    public void getSession(String sessionId, OnSessionLoaded callback) {
        db.collection("game_sessions")
                .document(sessionId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (!documentSnapshot.exists()) {
                        callback.onFailure("Session does not exist.");
                        return;
                    }

                    GameSession session = documentSnapshot.toObject(GameSession.class);

                    if (session == null) {
                        callback.onFailure("Failed to parse session.");
                        return;
                    }

                    callback.onSuccess(session);
                })
                .addOnFailureListener(e ->
                        callback.onFailure(e.getMessage())
                );
    }

    public ListenerRegistration listenToSession(
            String sessionId,
            OnSessionChanged callback
    ) {
        return db.collection("game_sessions")
                .document(sessionId)
                .addSnapshotListener((snapshot, error) -> {
                    if (error != null) {
                        callback.onFailure(error.getMessage());
                        return;
                    }

                    if (snapshot == null || !snapshot.exists()) {
                        callback.onFailure("Session does not exist.");
                        return;
                    }

                    GameSession session = snapshot.toObject(GameSession.class);

                    if (session == null) {
                        callback.onFailure("Failed to parse session.");
                        return;
                    }

                    callback.onChanged(session);
                });
    }

    public void updateCurrentGame(
            String sessionId,
            String currentGame,
            OnSessionUpdated callback
    ) {
        db.collection("game_sessions")
                .document(sessionId)
                .update("currentGame", currentGame)
                .addOnSuccessListener(unused ->
                        callback.onSuccess()
                )
                .addOnFailureListener(e ->
                        callback.onFailure(e.getMessage())
                );
    }

    public void updateTotalScores(
            String sessionId,
            int player1TotalScore,
            int player2TotalScore,
            OnSessionUpdated callback
    ) {
        db.collection("game_sessions")
                .document(sessionId)
                .update(
                        "player1TotalScore", player1TotalScore,
                        "player2TotalScore", player2TotalScore
                )
                .addOnSuccessListener(unused ->
                        callback.onSuccess()
                )
                .addOnFailureListener(e ->
                        callback.onFailure(e.getMessage())
                );
    }

    public void addToTotalScores(
            String sessionId,
            int player1Points,
            int player2Points,
            OnSessionUpdated callback
    ) {
        db.runTransaction(transaction -> {
            DocumentReference sessionRef = db.collection("game_sessions")
                    .document(sessionId);

            DocumentSnapshot snapshot = transaction.get(sessionRef);

            if (!snapshot.exists()) {
                try {
                    throw new Exception("Session does not exist.");
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }

            Long currentPlayer1Score = snapshot.getLong("player1TotalScore");
            Long currentPlayer2Score = snapshot.getLong("player2TotalScore");

            int newPlayer1Score =
                    (currentPlayer1Score != null ? currentPlayer1Score.intValue() : 0)
                            + player1Points;

            int newPlayer2Score =
                    (currentPlayer2Score != null ? currentPlayer2Score.intValue() : 0)
                            + player2Points;

            transaction.update(
                    sessionRef,
                    "player1TotalScore", newPlayer1Score,
                    "player2TotalScore", newPlayer2Score
            );

            return null;
        }).addOnSuccessListener(unused -> {
            callback.onSuccess();
        }).addOnFailureListener(e -> {
            callback.onFailure(e.getMessage());
        });
    }

    public void updateCurrentTurn(
            String sessionId,
            String currentTurnUID,
            OnSessionUpdated callback
    ) {
        db.collection("game_sessions")
                .document(sessionId)
                .update("currentTurnUID", currentTurnUID)
                .addOnSuccessListener(unused ->
                        callback.onSuccess()
                )
                .addOnFailureListener(e ->
                        callback.onFailure(e.getMessage())
                );
    }

    public void finishSession(
            String sessionId,
            String winnerUID,
            OnSessionUpdated callback
    ) {
        db.collection("game_sessions")
                .document(sessionId)
                .update(
                        "status", "finished",
                        "winnerUID", winnerUID
                )
                .addOnSuccessListener(unused ->
                        callback.onSuccess()
                )
                .addOnFailureListener(e ->
                        callback.onFailure(e.getMessage())
                );
    }

    public void abandonSession(
            String sessionId,
            String winnerUID,
            OnSessionUpdated callback
    ) {
        db.collection("game_sessions")
                .document(sessionId)
                .update(
                        "status", "abandoned",
                        "winnerUID", winnerUID
                )
                .addOnSuccessListener(unused ->
                        callback.onSuccess()
                )
                .addOnFailureListener(e ->
                        callback.onFailure(e.getMessage())
                );
    }

    public interface OnSessionCreated {
        void onSuccess(String sessionId);
        void onFailure(String errorMessage);
    }

    public interface OnSessionLoaded {
        void onSuccess(GameSession session);
        void onFailure(String errorMessage);
    }

    public interface OnSessionChanged {
        void onChanged(GameSession session);
        void onFailure(String errorMessage);
    }

    public interface OnSessionUpdated {
        void onSuccess();
        void onFailure(String errorMessage);
    }
}