package com.example.slagalica.Services;

import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.HashMap;
import java.util.Map;

public class MatchmakingService {

    private final FirebaseFirestore db;
    private final GameSessionService gameSessionService;
    private final TokenService tokenService;

    public MatchmakingService() {
        db = FirebaseFirestore.getInstance();
        gameSessionService = new GameSessionService();
        tokenService = new TokenService();
    }

    public void findRandomOpponent(String currentPlayerUID, OnMatchmakingResult callback) {

        db.collection("matchmaking_queue")
                .limit(10)
                .get()
                .addOnSuccessListener(querySnapshot -> {

                    String opponentUID = null;

                    for (QueryDocumentSnapshot doc : querySnapshot) {
                        String queuedPlayerUID = doc.getString("playerUID");

                        if (queuedPlayerUID != null && !queuedPlayerUID.equals(currentPlayerUID)) {
                            opponentUID = queuedPlayerUID;
                            break;
                        }
                    }

                    if (opponentUID == null) {
                        addPlayerToQueue(currentPlayerUID, callback);
                        return;
                    }

                    String finalOpponentUID = opponentUID;

                    tokenService.spendTokenForRankedGame(
                            currentPlayerUID,
                            new TokenService.OnTokenSpent() {
                                @Override
                                public void onSuccess() {
                                    tokenService.spendTokenForRankedGame(
                                            finalOpponentUID,
                                            new TokenService.OnTokenSpent() {
                                                @Override
                                                public void onSuccess() {
                                                    createRankedSession(
                                                            currentPlayerUID,
                                                            finalOpponentUID,
                                                            callback
                                                    );
                                                }

                                                @Override
                                                public void onFailure(String errorMessage) {
                                                    callback.onFailure(errorMessage);
                                                }
                                            }
                                    );
                                }

                                @Override
                                public void onFailure(String errorMessage) {
                                    callback.onFailure(errorMessage);
                                }
                            }
                    );
                })
                .addOnFailureListener(e -> callback.onFailure(e.getMessage()));
    }

    private void addPlayerToQueue(String playerUID, OnMatchmakingResult callback) {
        Map<String, Object> queueData = new HashMap<>();
        queueData.put("playerUID", playerUID);
        queueData.put("createdAt", new java.util.Date());
        queueData.put("matchedSessionId", null);

        db.collection("matchmaking_queue")
                .document(playerUID)
                .set(queueData)
                .addOnSuccessListener(unused -> callback.onWaiting())
                .addOnFailureListener(e -> callback.onFailure(e.getMessage()));
    }

    private void createRankedSession(
            String currentPlayerUID,
            String opponentUID,
            OnMatchmakingResult callback
    ) {
        gameSessionService.createSession(
                currentPlayerUID,
                opponentUID,
                "ranked",
                new GameSessionService.OnSessionCreated() {
                    @Override
                    public void onSuccess(String sessionId) {
                        db.collection("matchmaking_queue")
                                .document(opponentUID)
                                .update("matchedSessionId", sessionId)
                                .addOnSuccessListener(unused -> {
                                    db.collection("matchmaking_queue")
                                            .document(currentPlayerUID)
                                            .delete();

                                    callback.onMatched(sessionId);
                                })
                                .addOnFailureListener(e -> {
                                    callback.onFailure(e.getMessage());
                                });
                    }

                    @Override
                    public void onFailure(String errorMessage) {
                        callback.onFailure(errorMessage);
                    }
                }
        );
    }

    public interface OnMatchmakingResult {
        void onMatched(String sessionId);
        void onWaiting();
        void onFailure(String errorMessage);
    }

    public ListenerRegistration listenForMatch(
            String playerUID,
            OnMatchmakingResult callback
    ) {
        return db.collection("matchmaking_queue")
                .document(playerUID)
                .addSnapshotListener((snapshot, error) -> {

                    if (error != null) {
                        callback.onFailure(error.getMessage());
                        return;
                    }

                    if (snapshot == null || !snapshot.exists()) {
                        return;
                    }

                    String sessionId = snapshot.getString("matchedSessionId");

                    if (sessionId != null) {

                        db.collection("matchmaking_queue")
                                .document(playerUID)
                                .delete();

                        callback.onMatched(sessionId);
                    }
                });
    }
}