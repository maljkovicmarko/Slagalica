package com.example.slagalica.Fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.fragment.app.Fragment;

import com.example.slagalica.R;
import com.example.slagalica.Services.FriendInviteService;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

public class FriendsFragment extends Fragment {

    private LinearLayout friendsContainer;
    private FirebaseFirestore db;
    private FriendInviteService friendInviteService;

    public FriendsFragment() {
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.fragment_friends, container, false);

        friendsContainer = view.findViewById(R.id.friendsContainer);
        db = FirebaseFirestore.getInstance();
        friendInviteService = new FriendInviteService();

        loadFriends();

        return view;
    }

    private void loadFriends() {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();

        if (currentUser == null) {
            Toast.makeText(requireContext(), "User is not logged in.", Toast.LENGTH_SHORT).show();
            return;
        }

        String currentUID = currentUser.getUid();

        db.collection("players")
                .document(currentUID)
                .collection("friends")
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    friendsContainer.removeAllViews();

                    if (querySnapshot.isEmpty()) {
                        TextView emptyText = new TextView(requireContext());
                        emptyText.setText("You have no friends yet.");
                        emptyText.setTextSize(18);
                        friendsContainer.addView(emptyText);
                        return;
                    }

                    for (QueryDocumentSnapshot doc : querySnapshot) {
                        String friendUID = doc.getString("friendUID");
                        String username = doc.getString("username");

                        if (friendUID == null) continue;

                        addFriendRow(currentUID, friendUID, username);
                    }
                })
                .addOnFailureListener(e ->
                        Toast.makeText(requireContext(), e.getMessage(), Toast.LENGTH_SHORT).show()
                );
    }

    private void addFriendRow(String currentUID, String friendUID, String username) {
        LinearLayout row = new LinearLayout(requireContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, 12, 0, 12);

        TextView nameText = new TextView(requireContext());
        nameText.setText(username != null ? username : friendUID);
        nameText.setTextSize(18);
        nameText.setLayoutParams(new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1
        ));

        Button inviteButton = new Button(requireContext());
        inviteButton.setText("Invite");

        inviteButton.setOnClickListener(v -> {
            friendInviteService.sendInvite(
                    currentUID,
                    friendUID,
                    new FriendInviteService.OnInviteSent() {
                        @Override
                        public void onSuccess(String inviteId) {
                            Toast.makeText(requireContext(), "Invite sent!", Toast.LENGTH_SHORT).show();
                        }

                        @Override
                        public void onFailure(String errorMessage) {
                            Toast.makeText(requireContext(), errorMessage, Toast.LENGTH_SHORT).show();
                        }
                    }
            );
        });

        row.addView(nameText);
        row.addView(inviteButton);

        friendsContainer.addView(row);
    }
}