package com.example.slagalica.Services;

import com.example.slagalica.Model.Player;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.Date;
import java.util.concurrent.TimeUnit;

public class TokenService {

    private final FirebaseFirestore db;

    public TokenService() {
        db = FirebaseFirestore.getInstance();
    }

    public void grantDailyTokens(String playerUID) {

        DocumentReference playerRef =
                db.collection("players").document(playerUID);

        db.runTransaction(transaction -> {

            DocumentSnapshot snapshot = transaction.get(playerRef);

            if (!snapshot.exists()) {
                throw new RuntimeException("Player not found");
            }

            Player player = snapshot.toObject(Player.class);

            if (player == null) {
                throw new RuntimeException("Failed to parse player");
            }

            Date today = new Date();
            Date lastReward = player.getLastDailyTokenReward();

            int currentTokens = player.getTokens();

            if (lastReward == null) {
                transaction.update(
                        playerRef,
                        "tokens", currentTokens + 5,
                        "lastDailyTokenReward", today
                );

                return null;
            }

            long difference = today.getTime() - lastReward.getTime();
            long daysPassed = TimeUnit.MILLISECONDS.toDays(difference);

            if (daysPassed > 0) {
                int tokensToAdd = (int) daysPassed * 5;

                transaction.update(
                        playerRef,
                        "tokens", currentTokens + tokensToAdd,
                        "lastDailyTokenReward", today
                );
            }

            return null;
        });
    }

    public void spendTokenForRankedGame(String playerUID, OnTokenSpent callback) {
        DocumentReference playerRef =
                db.collection("players").document(playerUID);

        db.runTransaction(transaction -> {
            DocumentSnapshot snapshot = transaction.get(playerRef);

            if (!snapshot.exists()) {
                throw new RuntimeException("Player not found");
            }

            Long currentTokens = snapshot.getLong("tokens");

            int tokens = currentTokens != null ? currentTokens.intValue() : 0;

            if (tokens <= 0) {
                throw new RuntimeException("Not enough tokens");
            }

            transaction.update(playerRef, "tokens", tokens - 1);

            return null;
        }).addOnSuccessListener(unused -> {
            callback.onSuccess();
        }).addOnFailureListener(e -> {
            callback.onFailure(e.getMessage());
        });
    }

    public interface OnTokenSpent {
        void onSuccess();
        void onFailure(String errorMessage);
    }
}