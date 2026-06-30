package com.example.slagalica.Services;

import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public class FriendInviteService {

    private final FirebaseFirestore db;
    private final GameSessionService gameSessionService;

    public FriendInviteService() {
        db = FirebaseFirestore.getInstance();
        gameSessionService = new GameSessionService();
    }

    public void sendInvite(String fromUID, String toUID, OnInviteSent callback) {
        Map<String, Object> invite = new HashMap<>();
        invite.put("fromUID", fromUID);
        invite.put("toUID", toUID);
        invite.put("status", "pending");
        invite.put("createdAt", new Date());
        invite.put("sessionId", null);

        db.collection("friend_invites")
                .add(invite)
                .addOnSuccessListener(documentReference ->
                        callback.onSuccess(documentReference.getId())
                )
                .addOnFailureListener(e ->
                        callback.onFailure(e.getMessage())
                );
    }

    public ListenerRegistration listenForIncomingInvites(
            String currentPlayerUID,
            OnIncomingInvite callback
    ) {
        return db.collection("friend_invites")
                .whereEqualTo("toUID", currentPlayerUID)
                .whereEqualTo("status", "pending")
                .addSnapshotListener((snapshots, error) -> {
                    if (error != null) {
                        callback.onFailure(error.getMessage());
                        return;
                    }

                    if (snapshots == null || snapshots.isEmpty()) {
                        return;
                    }

                    for (QueryDocumentSnapshot doc : snapshots) {
                        String inviteId = doc.getId();
                        String fromUID = doc.getString("fromUID");

                        if (fromUID != null) {
                            callback.onInviteReceived(inviteId, fromUID);
                        }
                    }
                });
    }

    public void acceptInvite(String inviteId, String fromUID, String toUID, OnInviteAccepted callback) {
        gameSessionService.createSession(
                fromUID,
                toUID,
                "friendly",
                new GameSessionService.OnSessionCreated() {
                    @Override
                    public void onSuccess(String sessionId) {
                        db.collection("friend_invites")
                                .document(inviteId)
                                .update(
                                        "status", "accepted",
                                        "sessionId", sessionId
                                )
                                .addOnSuccessListener(unused ->
                                        callback.onSuccess(sessionId)
                                )
                                .addOnFailureListener(e ->
                                        callback.onFailure(e.getMessage())
                                );
                    }

                    @Override
                    public void onFailure(String errorMessage) {
                        callback.onFailure(errorMessage);
                    }
                }
        );
    }

    public void declineInvite(String inviteId, OnInviteDeclined callback) {
        db.collection("friend_invites")
                .document(inviteId)
                .update("status", "declined")
                .addOnSuccessListener(unused -> callback.onSuccess())
                .addOnFailureListener(e -> callback.onFailure(e.getMessage()));
    }

    public interface OnInviteSent {
        void onSuccess(String inviteId);
        void onFailure(String errorMessage);
    }

    public interface OnIncomingInvite {
        void onInviteReceived(String inviteId, String fromUID);
        void onFailure(String errorMessage);
    }

    public interface OnInviteAccepted {
        void onSuccess(String sessionId);
        void onFailure(String errorMessage);
    }

    public interface OnInviteDeclined {
        void onSuccess();
        void onFailure(String errorMessage);
    }
}