package com.example.slagalica.Services;

import com.example.slagalica.Model.LeaderboardRewardNotification;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.List;

public final class LeaderboardRewardService {
    public interface RewardsCallback {
        void onSuccess(List<LeaderboardRewardNotification> rewards);
    }

    public interface FailureCallback {
        void onFailure(String errorMessage);
    }

    private final FirebaseAuth auth;
    private final FirebaseFirestore db;

    public LeaderboardRewardService() {
        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
    }

    public void loadUnseenRewards(RewardsCallback onSuccess, FailureCallback onFailure) {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            onSuccess.onSuccess(new ArrayList<>());
            return;
        }

        db.collection("players")
                .document(user.getUid())
                .collection("rewardNotifications")
                .whereEqualTo("seen", false)
                .get()
                .addOnSuccessListener(snapshot -> {
                    List<LeaderboardRewardNotification> rewards = new ArrayList<>();
                    for (DocumentSnapshot document : snapshot.getDocuments()) {
                        rewards.add(new LeaderboardRewardNotification(
                                document.getId(),
                                stringValue(document, "cycleId"),
                                stringValue(document, "cycleType"),
                                intValue(document, "rank"),
                                intValue(document, "tokens"),
                                longValue(document, "createdAtMs")
                        ));
                    }
                    rewards.sort((first, second) -> Long.compare(
                            first.getCreatedAtMs(),
                            second.getCreatedAtMs()
                    ));
                    onSuccess.onSuccess(rewards);
                })
                .addOnFailureListener(exception -> onFailure.onFailure(messageOrDefault(
                        exception,
                        "Nije moguće učitati obaveštenja o nagradama."
                )));
    }

    public void markSeen(String rewardId, Runnable onSuccess, FailureCallback onFailure) {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            onFailure.onFailure("Korisnik nije prijavljen.");
            return;
        }
        if (rewardId == null || rewardId.trim().isEmpty()) {
            onFailure.onFailure("Nedostaje identifikator nagrade.");
            return;
        }

        db.collection("players")
                .document(user.getUid())
                .collection("rewardNotifications")
                .document(rewardId)
                .update("seen", true, "seenAtMs", System.currentTimeMillis())
                .addOnSuccessListener(unused -> onSuccess.run())
                .addOnFailureListener(exception -> onFailure.onFailure(messageOrDefault(
                        exception,
                        "Nije moguće označiti nagradu kao pregledanu."
                )));
    }

    private String stringValue(DocumentSnapshot document, String field) {
        String value = document.getString(field);
        return value == null ? "" : value;
    }

    private int intValue(DocumentSnapshot document, String field) {
        Object value = document.get(field);
        return value instanceof Number ? ((Number) value).intValue() : 0;
    }

    private long longValue(DocumentSnapshot document, String field) {
        Object value = document.get(field);
        return value instanceof Number ? ((Number) value).longValue() : 0L;
    }

    private String messageOrDefault(Exception exception, String fallback) {
        return exception.getMessage() == null ? fallback : exception.getMessage();
    }
}
