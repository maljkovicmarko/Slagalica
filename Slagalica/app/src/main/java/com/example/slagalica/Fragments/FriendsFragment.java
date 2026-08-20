package com.example.slagalica.Fragments;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.fragment.app.Fragment;

import com.example.slagalica.Activities.MainActivity;
import com.example.slagalica.Adapters.FriendAdapter;
import com.example.slagalica.Model.Player;
import com.example.slagalica.R;
import com.example.slagalica.Services.FriendService;
import com.example.slagalica.Services.WebSocketConfig;
import com.example.slagalica.Services.WebSocketGameClient;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class FriendsFragment extends Fragment {
    private static final String TAG = "FriendsFragment";

    private FriendService friendService;
    private WebSocketGameClient webSocketGameClient;
    private WebSocketGameClient.ListenerHandle friendInviteListenerHandle;
    private FriendAdapter friendsAdapter;
    private FriendAdapter searchAdapter;
    private EditText usernameSearchInput;
    private Button searchButton;
    private TextView friendsStateText;
    private TextView searchStateText;
    private final Set<String> friendIds = new HashSet<>();
    private String pendingInviteId;
    private String pendingInviteFriendId;

    public FriendsFragment() {
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        friendService = new FriendService();
        webSocketGameClient = WebSocketGameClient.getInstance();
    }

    @Override
    public View onCreateView(LayoutInflater inflater,
                             ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_friends, container, false);

        ImageButton menuButton = view.findViewById(R.id.menuButton);
        usernameSearchInput = view.findViewById(R.id.friendUsernameSearchInput);
        searchButton = view.findViewById(R.id.searchFriendButton);
        searchStateText = view.findViewById(R.id.friendSearchStateText);
        friendsStateText = view.findViewById(R.id.friendsStateText);
        Button scanFriendQrButton = view.findViewById(R.id.scanFriendQrButton);
        ListView searchResultsList = view.findViewById(R.id.friendSearchResultsList);
        ListView friendsList = view.findViewById(R.id.friendsList);

        friendsAdapter = new FriendAdapter(inflater, false, null);
        friendsAdapter.setChallengeFriendClickListener(this::challengeFriend);
        searchAdapter = new FriendAdapter(inflater, true, this::addFriend);
        friendsList.setAdapter(friendsAdapter);
        searchResultsList.setAdapter(searchAdapter);

        menuButton.setOnClickListener(unused -> ((MainActivity) requireActivity()).toggleNavbar());
        searchButton.setOnClickListener(unused -> searchPlayers());
        scanFriendQrButton.setOnClickListener(unused -> scanFriendQrCode());

        loadFriends();
        registerFriendInviteListener();
        return view;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        clearFriendInviteListener();
    }

    private void loadFriends() {
        friendsStateText.setText(R.string.loading_friends);
        friendService.getFriends(
                players -> {
                    if (!isAdded()) {
                        return;
                    }
                    friendIds.clear();
                    for (Player player : players) {
                        friendIds.add(player.getId());
                    }
                    friendsAdapter.submitList(players);
                    friendsStateText.setText(players.isEmpty()
                            ? R.string.no_friends
                            : R.string.friends_loaded);
                    refreshFriendStatuses(players);
                },
                this::showFriendLoadError
        );
    }

    private void searchPlayers() {
        String username = usernameSearchInput.getText().toString().trim();
        if (username.length() < 2) {
            usernameSearchInput.setError(getString(R.string.friend_search_too_short));
            return;
        }

        searchButton.setEnabled(false);
        searchStateText.setText(R.string.searching_players);
        friendService.searchPlayers(
                username,
                players -> {
                    if (!isAdded()) {
                        return;
                    }
                    searchButton.setEnabled(true);
                    List<Player> availableResults = new ArrayList<>();
                    for (Player player : players) {
                        if (!friendIds.contains(player.getId())) {
                            availableResults.add(player);
                        }
                    }
                    searchAdapter.submitList(availableResults);
                    searchStateText.setText(availableResults.isEmpty()
                            ? R.string.no_players_found
                            : R.string.search_results);
                },
                error -> {
                    if (!isAdded()) {
                        return;
                    }
                    searchButton.setEnabled(true);
                    searchStateText.setText(error);
                }
        );
    }

    private void addFriend(Player player) {
        addFriendByUid(player.getId());
    }

    private void addFriendByUid(String friendUid) {
        Log.d(TAG, "addFriendByUid: friendUid=" + friendUid);
        if (friendUid == null || friendUid.isBlank()) {
            Toast.makeText(requireContext(), R.string.qr_invalid_friend_code, Toast.LENGTH_LONG).show();
            return;
        }
        if (friendIds.contains(friendUid)) {
            Log.d(TAG, "addFriendByUid: already in local friendIds=" + friendUid);
            Toast.makeText(requireContext(), R.string.friend_added, Toast.LENGTH_SHORT).show();
            return;
        }

        friendService.addFriend(
                friendUid,
                () -> {
                    Log.d(TAG, "addFriendByUid: friendship write succeeded friendUid=" + friendUid);
                    if (!isAdded()) {
                        return;
                    }
                    Toast.makeText(requireContext(), R.string.friend_added, Toast.LENGTH_SHORT).show();
                    friendIds.add(friendUid);
                    searchAdapter.submitList(List.of());
                    searchStateText.setText("");
                    loadFriends();
                },
                error -> {
                    Log.e(TAG, "addFriendByUid: friendship write failed friendUid=" + friendUid + ", error=" + error);
                    if (!isAdded()) {
                        return;
                    }
                    Toast.makeText(requireContext(), error, Toast.LENGTH_LONG).show();
                    searchAdapter.notifyDataSetChanged();
                }
        );
    }

    private void scanFriendQrCode() {
        try {
            startActivity(new Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA));
            Toast.makeText(requireContext(), R.string.scan_friend_qr_with_camera, Toast.LENGTH_LONG).show();
        } catch (ActivityNotFoundException error) {
            Toast.makeText(requireContext(), R.string.qr_camera_not_available, Toast.LENGTH_LONG).show();
        }
    }

    private void challengeFriend(Player player) {
        if (player.getId() == null || player.getId().isBlank()) {
            return;
        }

        if (player.getId().equals(pendingInviteFriendId) && pendingInviteId != null) {
            cancelPendingInvite();
            return;
        }

        ensureSocketConnected(
                () -> webSocketGameClient.sendFriendInvite(player.getId(), new WebSocketGameClient.OnRequestResult() {
                    @Override
                    public void onSuccess(JSONObject data) {
                        if (!isAdded()) {
                            return;
                        }
                        pendingInviteId = data.optString("inviteId", null);
                        pendingInviteFriendId = player.getId();
                        friendsAdapter.setPendingInviteFriendId(pendingInviteFriendId);
                        Toast.makeText(requireContext(), R.string.friend_invite_sent, Toast.LENGTH_SHORT).show();
                    }

                    @Override
                    public void onFailure(String errorMessage) {
                        if (isAdded()) {
                            Toast.makeText(requireContext(), errorMessage, Toast.LENGTH_LONG).show();
                        }
                    }
                }),
                error -> {
                    if (isAdded()) {
                        Toast.makeText(requireContext(), error, Toast.LENGTH_LONG).show();
                    }
                }
        );
    }

    private void cancelPendingInvite() {
        if (pendingInviteId == null) {
            return;
        }

        String inviteId = pendingInviteId;
        webSocketGameClient.cancelFriendInvite(inviteId, new WebSocketGameClient.OnRequestResult() {
            @Override
            public void onSuccess(JSONObject data) {
                clearPendingInvite();
                if (isAdded()) {
                    Toast.makeText(requireContext(), R.string.friend_invite_cancelled, Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(String errorMessage) {
                clearPendingInvite();
                if (isAdded()) {
                    Toast.makeText(requireContext(), errorMessage, Toast.LENGTH_LONG).show();
                }
            }
        });
    }

    private void refreshFriendStatuses(List<Player> players) {
        if (players == null || players.isEmpty()) {
            friendsAdapter.submitAvailability(Map.of());
            return;
        }

        List<String> ids = new ArrayList<>();
        for (Player player : players) {
            if (player.getId() != null && !player.getId().isBlank()) {
                ids.add(player.getId());
            }
        }

        ensureSocketConnected(
                () -> webSocketGameClient.getFriendStatuses(ids, new WebSocketGameClient.OnRequestResult() {
                    @Override
                    public void onSuccess(JSONObject data) {
                        Map<String, Boolean> availability = new HashMap<>();
                        JSONArray statuses = data.optJSONArray("statuses");
                        Log.d(TAG, "Statuses: " + statuses);
                        if (statuses != null) {
                            for (int index = 0; index < statuses.length(); index++) {
                                JSONObject status = statuses.optJSONObject(index);
                                if (status != null) {
                                    availability.put(status.optString("uid", ""), status.optBoolean("available", false));
                                }
                            }
                        }
                        if (isAdded()) {
                            friendsAdapter.submitAvailability(availability);
                        }
                    }

                    @Override
                    public void onFailure(String errorMessage) {
                        if (isAdded()) {
                            friendsAdapter.submitAvailability(Map.of());
                        }
                    }
                }),
                error -> {
                    if (isAdded()) {
                        friendsAdapter.submitAvailability(Map.of());
                    }
                }
        );
    }

    private void registerFriendInviteListener() {
        clearFriendInviteListener();
        friendInviteListenerHandle = webSocketGameClient.addFriendInviteListener(new WebSocketGameClient.OnFriendInviteListener() {
            @Override
            public void onInviteReceived(WebSocketGameClient.FriendInvite invite) {
            }

            @Override
            public void onInviteAccepted(WebSocketGameClient.FriendInvite invite, com.example.slagalica.Services.SessionSnapshot session) {
                if (invite.getInviteId().equals(pendingInviteId)) {
                    clearPendingInvite();
                }
            }

            @Override
            public void onInviteRejected(WebSocketGameClient.FriendInvite invite) {
                if (invite.getInviteId().equals(pendingInviteId)) {
                    clearPendingInvite();
                    if (isAdded()) {
                        Toast.makeText(requireContext(), R.string.friend_invite_rejected, Toast.LENGTH_SHORT).show();
                    }
                }
            }

            @Override
            public void onInviteCancelled(WebSocketGameClient.FriendInvite invite) {
                if (invite.getInviteId().equals(pendingInviteId)) {
                    clearPendingInvite();
                }
            }

            @Override
            public void onInviteExpired(WebSocketGameClient.FriendInvite invite) {
                if (invite.getInviteId().equals(pendingInviteId)) {
                    clearPendingInvite();
                    if (isAdded()) {
                        Toast.makeText(requireContext(), R.string.friend_invite_expired, Toast.LENGTH_SHORT).show();
                    }
                }
            }

            @Override
            public void onFailure(String errorMessage) {
            }
        });
    }

    private void clearFriendInviteListener() {
        if (friendInviteListenerHandle != null) {
            friendInviteListenerHandle.remove();
            friendInviteListenerHandle = null;
        }
    }

    private void clearPendingInvite() {
        pendingInviteId = null;
        pendingInviteFriendId = null;
        if (friendsAdapter != null) {
            friendsAdapter.setPendingInviteFriendId(null);
        }
    }

    private void ensureSocketConnected(Runnable onConnected, FriendService.FailureCallback onFailure) {
        FirebaseUser firebaseUser = FirebaseAuth.getInstance().getCurrentUser();
        if (firebaseUser == null) {
            onFailure.onFailure("User is not logged in.");
            return;
        }

        webSocketGameClient.setServerUrl(WebSocketConfig.getServerUrl(requireContext()));
        webSocketGameClient.connect(firebaseUser.getUid(), new WebSocketGameClient.OnConnected() {
            @Override
            public void onConnected() {
                onConnected.run();
            }

            @Override
            public void onFailure(String errorMessage) {
                onFailure.onFailure(errorMessage);
            }
        });
    }

    private void showFriendLoadError(String error) {
        if (isAdded()) {
            friendsStateText.setText(error);
        }
    }
}
