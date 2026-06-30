package com.example.slagalica.Fragments;

import android.app.AlertDialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.Toast;

import androidx.fragment.app.Fragment;

import com.example.slagalica.Activities.MainActivity;
import com.example.slagalica.R;
import com.example.slagalica.Services.FriendInviteService;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.ArrayList;
import java.util.List;

public class NotificationsFragment extends Fragment {

    private ImageButton menuButton;
    private ListView notificationsListView;

    private FriendInviteService friendInviteService;
    private ListenerRegistration inviteListener;

    private final List<FriendInviteItem> pendingInvites = new ArrayList<>();

    public NotificationsFragment() {
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        friendInviteService = new FriendInviteService();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.fragment_notifications, container, false);

        menuButton = view.findViewById(R.id.menuButton);
        notificationsListView = view.findViewById(R.id.notificationsListView);

        menuButton.setVisibility(View.VISIBLE);

        menuButton.setOnClickListener(v -> {
            ((MainActivity) requireActivity()).toggleNavbar();
        });

        notificationsListView.setOnItemClickListener((parent, itemView, position, id) -> {
            FriendInviteItem invite = pendingInvites.get(position);
            showInviteDialog(invite);
        });

        listenForFriendInvites();

        return view;
    }

    private void listenForFriendInvites() {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();

        if (currentUser == null) {
            Toast.makeText(requireContext(), "User is not logged in.", Toast.LENGTH_SHORT).show();
            return;
        }

        String currentUID = currentUser.getUid();

        inviteListener = friendInviteService.listenForIncomingInvites(
                currentUID,
                new FriendInviteService.OnIncomingInvite() {
                    @Override
                    public void onInviteReceived(String inviteId, String fromUID) {
                        addInviteToList(inviteId, fromUID, currentUID);
                    }

                    @Override
                    public void onFailure(String errorMessage) {
                        Toast.makeText(requireContext(), errorMessage, Toast.LENGTH_LONG).show();
                    }
                }
        );
    }

    private void addInviteToList(String inviteId, String fromUID, String currentUID) {
        for (FriendInviteItem item : pendingInvites) {
            if (item.inviteId.equals(inviteId)) {
                return;
            }
        }

        pendingInvites.add(new FriendInviteItem(inviteId, fromUID, currentUID));
        refreshList();
    }

    private void refreshList() {
        List<String> displayItems = new ArrayList<>();

        if (pendingInvites.isEmpty()) {
            displayItems.add("No pending friend game invites.");
        } else {
            for (FriendInviteItem invite : pendingInvites) {
                displayItems.add("Friendly game invite from: " + invite.fromUID);
            }
        }

        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                requireContext(),
                android.R.layout.simple_list_item_1,
                displayItems
        );

        notificationsListView.setAdapter(adapter);
    }

    private void showInviteDialog(FriendInviteItem invite) {
        new AlertDialog.Builder(requireContext())
                .setTitle("Friendly game invite")
                .setMessage("Accept invite from " + invite.fromUID + "?")
                .setPositiveButton("Accept", (dialog, which) -> acceptInvite(invite))
                .setNegativeButton("Decline", (dialog, which) -> declineInvite(invite))
                .show();
    }

    private void acceptInvite(FriendInviteItem invite) {
        friendInviteService.acceptInvite(
                invite.inviteId,
                invite.fromUID,
                invite.toUID,
                new FriendInviteService.OnInviteAccepted() {
                    @Override
                    public void onSuccess(String sessionId) {
                        Toast.makeText(requireContext(), "Invite accepted!", Toast.LENGTH_SHORT).show();
                        openFirstGame(sessionId);
                    }

                    @Override
                    public void onFailure(String errorMessage) {
                        Toast.makeText(requireContext(), errorMessage, Toast.LENGTH_LONG).show();
                    }
                }
        );
    }

    private void declineInvite(FriendInviteItem invite) {
        friendInviteService.declineInvite(
                invite.inviteId,
                new FriendInviteService.OnInviteDeclined() {
                    @Override
                    public void onSuccess() {
                        pendingInvites.remove(invite);
                        refreshList();
                        Toast.makeText(requireContext(), "Invite declined.", Toast.LENGTH_SHORT).show();
                    }

                    @Override
                    public void onFailure(String errorMessage) {
                        Toast.makeText(requireContext(), errorMessage, Toast.LENGTH_LONG).show();
                    }
                }
        );
    }

    private void openFirstGame(String sessionId) {
        Bundle bundle = new Bundle();
        bundle.putString("sessionId", sessionId);

        GeneralKnowledgeFragment fragment = new GeneralKnowledgeFragment();
        fragment.setArguments(bundle);

        requireActivity()
                .getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragmentContainer, fragment)
                .commit();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();

        if (inviteListener != null) {
            inviteListener.remove();
            inviteListener = null;
        }
    }

    private static class FriendInviteItem {
        String inviteId;
        String fromUID;
        String toUID;

        FriendInviteItem(String inviteId, String fromUID, String toUID) {
            this.inviteId = inviteId;
            this.fromUID = fromUID;
            this.toUID = toUID;
        }
    }
}