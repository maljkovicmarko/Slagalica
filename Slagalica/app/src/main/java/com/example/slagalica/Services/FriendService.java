package com.example.slagalica.Services;

import com.example.slagalica.Model.Player;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;

import android.util.Log;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class FriendService {
    private static final String TAG = "FriendService";

    public interface PlayersCallback {
        void onSuccess(List<Player> players);
    }

    public interface FailureCallback {
        void onFailure(String errorMessage);
    }

    private final FirebaseAuth auth;
    private final FirebaseFirestore db;

    public FriendService() {
        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
    }

    public void getFriends(PlayersCallback onSuccess, FailureCallback onFailure) {
        FirebaseUser currentUser = requireCurrentUser(onFailure);
        if (currentUser == null) {
            return;
        }

        String currentUid = currentUser.getUid();
        Log.d(TAG, "getFriends: querying friendships for uid=" + currentUid);
        db.collection("friendships")
                .whereArrayContains("memberIds", currentUid)
                .get()
                .addOnSuccessListener(snapshot -> {
                    Log.d(TAG, "getFriends: friendship documents=" + snapshot.size());
                    List<String> friendIds = new ArrayList<>();
                    snapshot.getDocuments().forEach(document -> {
                        Object rawMembers = document.get("memberIds");
                        if (!(rawMembers instanceof List<?>)) {
                            return;
                        }
                        for (Object rawMember : (List<?>) rawMembers) {
                            if (rawMember instanceof String && !currentUid.equals(rawMember)) {
                                friendIds.add((String) rawMember);
                            }
                        }
                    });
                    Log.d(TAG, "getFriends: extracted friendIds=" + friendIds);
                    loadPlayers(friendIds, onSuccess, onFailure);
                })
                .addOnFailureListener(error -> handleFailure("getFriends friendships query uid=" + currentUid, error, onFailure));
    }

    public void searchPlayers(String username,
                              PlayersCallback onSuccess,
                              FailureCallback onFailure) {
        FirebaseUser currentUser = requireCurrentUser(onFailure);
        if (currentUser == null) {
            return;
        }

        String normalized = normalizeUsername(username);
        Log.d(TAG, "searchPlayers: username=" + username + ", normalized=" + normalized);
        if (normalized.isEmpty()) {
            onSuccess.onSuccess(Collections.emptyList());
            return;
        }

        db.collection("players")
                .orderBy("usernameNormalized")
                .startAt(normalized)
                .endAt(normalized + "\uf8ff")
                .limit(20)
                .get()
                .addOnSuccessListener(snapshot -> {
                    List<Player> players = mapPlayers(snapshot.getDocuments(), currentUser.getUid());
                    if (!players.isEmpty()) {
                        onSuccess.onSuccess(players);
                        return;
                    }
                    searchLegacyExactUsername(username.trim(), currentUser.getUid(), onSuccess, onFailure);
                })
                .addOnFailureListener(error -> handleFailure("searchPlayers normalized query=" + normalized, error, onFailure));
    }

    public void addFriend(String friendUid, Runnable onSuccess, FailureCallback onFailure) {
        FirebaseUser currentUser = requireCurrentUser(onFailure);
        if (currentUser == null) {
            return;
        }

        String currentUid = currentUser.getUid();
        if (friendUid == null || friendUid.isBlank() || currentUid.equals(friendUid)) {
            onFailure.onFailure("Invalid friend.");
            return;
        }

        List<String> memberIds = new ArrayList<>(Arrays.asList(currentUid, friendUid));
        Collections.sort(memberIds);
        String firstUid = memberIds.get(0);
        String friendshipId = firstUid.length() + "_" + firstUid + memberIds.get(1);

        Map<String, Object> friendship = new HashMap<>();
        friendship.put("memberIds", memberIds);
        friendship.put("createdBy", currentUid);
        friendship.put("createdAt", FieldValue.serverTimestamp());

        DocumentReference friendshipReference = db.collection("friendships").document(friendshipId);
        Log.d(TAG, "addFriend: writing " + friendshipReference.getPath() + " memberIds=" + memberIds);
        friendshipReference
                .set(friendship)
                .addOnSuccessListener(unused -> onSuccess.run())
                .addOnFailureListener(error -> handleFailure("addFriend write " + friendshipReference.getPath(), error, onFailure));
    }

    private void loadPlayers(List<String> playerIds,
                             PlayersCallback onSuccess,
                             FailureCallback onFailure) {
        if (playerIds.isEmpty()) {
            onSuccess.onSuccess(Collections.emptyList());
            return;
        }

        List<Task<DocumentSnapshot>> tasks = new ArrayList<>();
        for (String playerId : playerIds) {
            Log.d(TAG, "loadPlayers: reading players/" + playerId);
            tasks.add(db.collection("players").document(playerId).get());
        }

        com.google.android.gms.tasks.Tasks.whenAllSuccess(tasks)
                .addOnSuccessListener(results -> {
                    List<Player> players = new ArrayList<>();
                    for (Object result : results) {
                        if (!(result instanceof DocumentSnapshot)) {
                            continue;
                        }
                        DocumentSnapshot document = (DocumentSnapshot) result;
                        Player player = document.toObject(Player.class);
                        if (player != null) {
                            player.setId(document.getId());
                            players.add(player);
                        }
                    }
                    sortPlayers(players);
                    onSuccess.onSuccess(players);
                })
                .addOnFailureListener(error -> handleFailure("loadPlayers ids=" + playerIds, error, onFailure));
    }

    private void searchLegacyExactUsername(String username,
                                           String currentUid,
                                           PlayersCallback onSuccess,
                                           FailureCallback onFailure) {
        db.collection("players")
                .whereEqualTo("username", username)
                .limit(20)
                .get()
                .addOnSuccessListener(snapshot -> onSuccess.onSuccess(
                        mapPlayers(snapshot.getDocuments(), currentUid)
                ))
                .addOnFailureListener(error -> handleFailure("searchLegacyExactUsername username=" + username, error, onFailure));
    }

    private List<Player> mapPlayers(List<DocumentSnapshot> documents, String excludedUid) {
        List<Player> players = new ArrayList<>();
        for (DocumentSnapshot document : documents) {
            if (document.getId().equals(excludedUid)) {
                continue;
            }
            Player player = document.toObject(Player.class);
            if (player != null) {
                player.setId(document.getId());
                players.add(player);
            }
        }
        sortPlayers(players);
        return players;
    }

    private void sortPlayers(List<Player> players) {
        players.sort((first, second) -> safeUsername(first).compareToIgnoreCase(safeUsername(second)));
    }

    private String safeUsername(Player player) {
        return player.getUsername() == null ? "" : player.getUsername();
    }

    private FirebaseUser requireCurrentUser(FailureCallback onFailure) {
        FirebaseUser currentUser = auth.getCurrentUser();
        if (currentUser == null) {
            onFailure.onFailure("User is not logged in.");
        }
        return currentUser;
    }

    private String normalizeUsername(String username) {
        return username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
    }

    private String messageOf(Exception error) {
        return error.getMessage() == null ? "Firestore request failed." : error.getMessage();
    }

    private void handleFailure(String operation, Exception error, FailureCallback onFailure) {
        String message = messageOf(error);
        if (error instanceof FirebaseFirestoreException) {
            FirebaseFirestoreException firestoreException = (FirebaseFirestoreException) error;
            Log.e(TAG, operation
                    + " failed. code=" + firestoreException.getCode()
                    + ", message=" + message,
                    error);
        } else {
            Log.e(TAG, operation
                    + " failed. type=" + error.getClass().getName()
                    + ", message=" + message,
                    error);
        }
        onFailure.onFailure(message);
    }
}
