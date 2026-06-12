package com.example.slagalica.Services;

import com.example.slagalica.Model.SystemNotification;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.List;

public class NotificationService {

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

    public void createDummyNotifications(Runnable onSuccess, FailureCallback onFailure) {
        FirebaseUser user = auth.getCurrentUser();

        if (user == null) {
            onFailure.onFailure("User is not logged in");
            return;
        }

        String uid = user.getUid();
        long now = System.currentTimeMillis();

        List<SystemNotification> notifications = new ArrayList<>();
        notifications.add(new SystemNotification(null, "Ranking update", "You moved up on the ranking list.", "RANKING", false, now, "OPEN_RANKING"));
        notifications.add(new SystemNotification(null, "Reward received", "You received 20 tokens.", "REWARD", false, now - 1000, "OPEN_REWARDS"));
        notifications.add(new SystemNotification(null, "Friend invite", "A player invited you to be friends.", "OTHER", true, now - 2000, "OPEN_FRIENDS"));
        notifications.add(new SystemNotification(null, "Chat message", "You received a new chat message.", "CHAT", false, now - 3000, "OPEN_CHAT"));

        saveDummyNotification(uid, notifications, 0, onSuccess, onFailure);
    }

    private void saveDummyNotification(String uid,
                                       List<SystemNotification> notifications,
                                       int index,
                                       Runnable onSuccess,
                                       FailureCallback onFailure) {
        if (index >= notifications.size()) {
            onSuccess.run();
            return;
        }

        SystemNotification notification = notifications.get(index);

        db.collection("players")
                .document(uid)
                .collection("notifications")
                .add(notification)
                .addOnSuccessListener(documentReference -> {
                    documentReference.update("id", documentReference.getId())
                            .addOnSuccessListener(unused ->
                                    saveDummyNotification(uid, notifications, index + 1, onSuccess, onFailure))
                            .addOnFailureListener(e -> onFailure.onFailure(e.getMessage()));
                })
                .addOnFailureListener(e -> onFailure.onFailure(e.getMessage()));
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
                .collection("notifications")
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    List<SystemNotification> result = new ArrayList<>();

                    for (var document : querySnapshot.getDocuments()) {
                        SystemNotification notification = document.toObject(SystemNotification.class);

                        if (notification == null) {
                            continue;
                        }

                        notification.setId(document.getId());

                        if ("READ".equals(filter) && !notification.isRead()) {
                            continue;
                        }

                        if ("UNREAD".equals(filter) && notification.isRead()) {
                            continue;
                        }

                        result.add(notification);
                    }

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

        db.collection("players")
                .document(user.getUid())
                .collection("notifications")
                .document(notificationId)
                .update("read", true)
                .addOnSuccessListener(unused -> onSuccess.run())
                .addOnFailureListener(e -> onFailure.onFailure(e.getMessage()));
    }
}