package com.example.slagalica.Services;

import com.example.slagalica.Model.SystemNotification;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.List;

public class NotificationService {
    private static final String GENERAL_NOTIFICATIONS_COLLECTION = "notifications";
    private static final String REWARD_NOTIFICATIONS_COLLECTION = "rewardNotifications";

    public interface NotificationsCallback {
        void onSuccess(List<SystemNotification> notifications);
    }

    public interface FailureCallback {
        void onFailure(String errorMessage);
    }

    private final FirebaseAuth auth;
    private final FirebaseFirestore db;

    public NotificationService() {
        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
    }

    public void getNotifications(String filter,
                                 NotificationsCallback onSuccess,
                                 FailureCallback onFailure) {
        FirebaseUser user = auth.getCurrentUser();

        if (user == null) {
            onFailure.onFailure("User is not logged in");
            return;
        }

        db.collection("players")
                .document(user.getUid())
                .collection(GENERAL_NOTIFICATIONS_COLLECTION)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    List<SystemNotification> result = new ArrayList<>();

                    for (DocumentSnapshot document : querySnapshot.getDocuments()) {
                        addIfMatchesFilter(result, mapGeneralNotification(document), filter);
                    }

                    loadRewardNotifications(user.getUid(), filter, result, onSuccess, onFailure);
                })
                .addOnFailureListener(e -> onFailure.onFailure(e.getMessage()));
    }

    private void loadRewardNotifications(String uid,
                                         String filter,
                                         List<SystemNotification> result,
                                         NotificationsCallback onSuccess,
                                         FailureCallback onFailure) {
        db.collection("players")
                .document(uid)
                .collection(REWARD_NOTIFICATIONS_COLLECTION)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    for (DocumentSnapshot document : querySnapshot.getDocuments()) {
                        addIfMatchesFilter(result, mapRewardNotification(document), filter);
                    }

                    result.sort((first, second) -> Long.compare(second.getCreatedAt(), first.getCreatedAt()));
                    onSuccess.onSuccess(result);
                })
                .addOnFailureListener(e -> onFailure.onFailure(e.getMessage()));
    }

    public void markAsRead(String notificationId,
                           Runnable onSuccess,
                           FailureCallback onFailure) {
        FirebaseUser user = auth.getCurrentUser();

        if (user == null) {
            onFailure.onFailure("User is not logged in");
            return;
        }

        if (notificationId != null && notificationId.startsWith(SystemNotification.REWARD_ID_PREFIX)) {
            String rewardId = notificationId.substring(SystemNotification.REWARD_ID_PREFIX.length());
            db.collection("players")
                    .document(user.getUid())
                    .collection(REWARD_NOTIFICATIONS_COLLECTION)
                    .document(rewardId)
                    .update("seen", true, "seenAtMs", System.currentTimeMillis())
                    .addOnSuccessListener(unused -> onSuccess.run())
                    .addOnFailureListener(e -> onFailure.onFailure(e.getMessage()));
            return;
        }

        db.collection("players")
                .document(user.getUid())
                .collection(GENERAL_NOTIFICATIONS_COLLECTION)
                .document(notificationId)
                .update("read", true)
                .addOnSuccessListener(unused -> onSuccess.run())
                .addOnFailureListener(e -> onFailure.onFailure(e.getMessage()));
    }

    private SystemNotification mapGeneralNotification(DocumentSnapshot document) {
        SystemNotification notification = document.toObject(SystemNotification.class);
        if (notification == null) {
            return null;
        }
        notification.setId(document.getId());
        return notification;
    }

    private SystemNotification mapRewardNotification(DocumentSnapshot document) {
        String cycleType = stringValue(document, "cycleType");
        int rank = intValue(document, "rank");
        int tokens = intValue(document, "tokens");
        long createdAt = longValue(document, "createdAtMs");

        String title = "Nagrada rang liste";
        String message = ("monthly".equalsIgnoreCase(cycleType)
                ? "Mesečna rang lista"
                : "Nedeljna rang lista")
                + ": osvojeno " + rank + ". mesto i " + tokens + " tokena.";

        return new SystemNotification(
                SystemNotification.REWARD_ID_PREFIX + document.getId(),
                title,
                message,
                "REWARD",
                Boolean.TRUE.equals(document.getBoolean("seen")),
                createdAt,
                "OPEN_REWARDS"
        );
    }

    private void addIfMatchesFilter(List<SystemNotification> result,
                                    SystemNotification notification,
                                    String filter) {
        if (notification == null) {
            return;
        }
        if ("READ".equals(filter) && !notification.isRead()) {
            return;
        }
        if ("UNREAD".equals(filter) && notification.isRead()) {
            return;
        }
        result.add(notification);
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
}
