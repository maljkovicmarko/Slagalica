package com.example.slagalica.Fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.fragment.app.Fragment;

import com.example.slagalica.Activities.MainActivity;
import com.example.slagalica.R;
import com.example.slagalica.Services.MatchmakingService;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.ListenerRegistration;

public class HomeFragment extends Fragment {

    private Button profileButton;
    private Button playGameButton;
    private Button playFriendlyGameButton;
    private Button notificationsButton;
    private ImageButton menuButton;

    private MatchmakingService matchmakingService;

    private ListenerRegistration matchmakingListener;

    public HomeFragment() {
    }

    public static HomeFragment newInstance(String param1, String param2) {
        HomeFragment fragment = new HomeFragment();
        Bundle args = new Bundle();
        args.putString("param1", param1);
        args.putString("param2", param2);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.fragment_home, container, false);

        profileButton = view.findViewById(R.id.profileButton);
        playGameButton = view.findViewById(R.id.playGameButton);
        playFriendlyGameButton = view.findViewById(R.id.playFriendlyGameButton);
        notificationsButton = view.findViewById(R.id.notificationsButton);
        menuButton = view.findViewById(R.id.menuButton);

        matchmakingService = new MatchmakingService();

        menuButton.setVisibility(View.VISIBLE);

        profileButton.setOnClickListener(v -> {
            requireActivity()
                    .getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.fragmentContainer, new ProfileFragment())
                    .addToBackStack(null)
                    .commit();
        });

        playGameButton.setOnClickListener(v -> startRandomMatchmaking());

        playFriendlyGameButton.setOnClickListener(v -> {
            requireActivity()
                    .getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.fragmentContainer, new FriendsFragment())
                    .addToBackStack(null)
                    .commit();
        });

        menuButton.setOnClickListener(v -> {
            ((MainActivity) requireActivity()).toggleNavbar();
        });

        notificationsButton.setOnClickListener(v -> {
            requireActivity()
                    .getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.fragmentContainer, new NotificationsFragment())
                    .addToBackStack(null)
                    .commit();
        });

        return view;
    }

    private void startRandomMatchmaking() {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();

        if (currentUser == null) {
            Toast.makeText(requireContext(), "User is not logged in.", Toast.LENGTH_SHORT).show();
            return;
        }

        String currentPlayerUID = currentUser.getUid();

        matchmakingService.findRandomOpponent(
                currentPlayerUID,
                new MatchmakingService.OnMatchmakingResult() {
                    @Override
                    public void onMatched(String sessionId) {
                        Toast.makeText(requireContext(), "Match found!", Toast.LENGTH_SHORT).show();
                        openFirstGame(sessionId);
                    }

                    @Override
                    public void onWaiting() {
                        Toast.makeText(requireContext(), "Waiting for opponent...", Toast.LENGTH_SHORT).show();

                        matchmakingListener = matchmakingService.listenForMatch(
                                currentPlayerUID,
                                new MatchmakingService.OnMatchmakingResult() {
                                    @Override
                                    public void onMatched(String sessionId) {
                                        Toast.makeText(requireContext(), "Match found!", Toast.LENGTH_SHORT).show();
                                        openFirstGame(sessionId);
                                    }

                                    @Override
                                    public void onWaiting() {
                                    }

                                    @Override
                                    public void onFailure(String errorMessage) {
                                        Toast.makeText(requireContext(), errorMessage, Toast.LENGTH_SHORT).show();
                                    }
                                }
                        );
                    }

                    @Override
                    public void onFailure(String errorMessage) {
                        Toast.makeText(requireContext(), errorMessage, Toast.LENGTH_SHORT).show();
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

        if (matchmakingListener != null) {
            matchmakingListener.remove();
            matchmakingListener = null;
        }
    }
}