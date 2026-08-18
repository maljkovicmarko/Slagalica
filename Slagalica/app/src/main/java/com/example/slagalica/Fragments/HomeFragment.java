package com.example.slagalica.Fragments;

import android.app.AlertDialog;
import android.os.Bundle;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.fragment.app.Fragment;

import com.example.slagalica.Activities.MainActivity;
import com.example.slagalica.R;
import com.example.slagalica.Services.SessionSnapshot;
import com.example.slagalica.Services.WebSocketConfig;
import com.example.slagalica.Services.WebSocketGameClient;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

/**
 * A simple {@link Fragment} subclass.
 * Use the {@link HomeFragment#newInstance} factory method to
 * create an instance of this fragment.
 */
public class HomeFragment extends Fragment {

    // TODO: Rename parameter arguments, choose names that match
    // the fragment initialization parameters, e.g. ARG_ITEM_NUMBER
    private static final String ARG_PARAM1 = "param1";
    private static final String ARG_PARAM2 = "param2";

    // TODO: Rename and change types of parameters
    private String mParam1;
    private String mParam2;

    private Button profileButton;
    private Button playGameButton;
    private Button notificationsButton;

    private ImageButton menuButton;
    private WebSocketGameClient webSocketGameClient;
    private WebSocketGameClient.ListenerHandle matchmakingListenerHandle;
    private boolean matchmakingInProgress;

    public HomeFragment() {
        // Required empty public constructor
    }

    /**
     * Use this factory method to create a new instance of
     * this fragment using the provided parameters.
     *
     * @param param1 Parameter 1.
     * @param param2 Parameter 2.
     * @return A new instance of fragment HomeFragment.
     */
    // TODO: Rename and change types and number of parameters
    public static HomeFragment newInstance(String param1, String param2) {
        HomeFragment fragment = new HomeFragment();
        Bundle args = new Bundle();
        args.putString(ARG_PARAM1, param1);
        args.putString(ARG_PARAM2, param2);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        webSocketGameClient = WebSocketGameClient.getInstance();
        if (getArguments() != null) {
            mParam1 = getArguments().getString(ARG_PARAM1);
            mParam2 = getArguments().getString(ARG_PARAM2);
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_home, container, false);
        profileButton = view.findViewById(R.id.profileButton);
        playGameButton = view.findViewById(R.id.playGameButton);
        notificationsButton = view.findViewById(R.id.notificationsButton);
        menuButton = view.findViewById(R.id.menuButton);
        menuButton.setVisibility(View.VISIBLE);

        profileButton.setOnClickListener(v -> {
            requireActivity()
                    .getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.fragmentContainer, new ProfileFragment())
                    .addToBackStack(null)
                    .commit();
        });
        playGameButton.setOnClickListener(v -> startMatchmaking());
        playGameButton.setOnLongClickListener(v -> {
            showServerUrlDialog();
            return true;
        });
        menuButton.setOnClickListener(v -> {
            System.out.println("Listener entered");
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

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        clearMatchmakingListener();
    }

    private void startMatchmaking() {
        if (matchmakingInProgress) {
            return;
        }

        FirebaseUser firebaseUser = FirebaseAuth.getInstance().getCurrentUser();
        if (firebaseUser == null) {
            Toast.makeText(requireContext(), "User is not logged in", Toast.LENGTH_LONG).show();
            return;
        }

        matchmakingInProgress = true;
        playGameButton.setEnabled(false);
        registerMatchmakingListener();
        showWaitingForMatchFragment();
        webSocketGameClient.setServerUrl(WebSocketConfig.getServerUrl(requireContext()));

        webSocketGameClient.connect(firebaseUser.getUid(), new WebSocketGameClient.OnConnected() {
            @Override
            public void onConnected() {
                webSocketGameClient.joinRankedQueue(new WebSocketGameClient.OnRequestResult() {
                    @Override
                    public void onSuccess(org.json.JSONObject data) {
                    }

                    @Override
                    public void onFailure(String errorMessage) {
                        handleMatchmakingFailure(errorMessage);
                    }
                });
            }

            @Override
            public void onFailure(String errorMessage) {
                handleMatchmakingFailure(errorMessage);
            }
        });
    }

    private void registerMatchmakingListener() {
        clearMatchmakingListener();
        matchmakingListenerHandle = webSocketGameClient.addMatchmakingListener(new WebSocketGameClient.OnMatchmakingListener() {
            @Override
            public void onQueueState(boolean queued, int queueSize) {
            }

            @Override
            public void onMatchFound(SessionSnapshot session) {
                handleMatchFound(session);
            }

            @Override
            public void onFailure(String errorMessage) {
                handleMatchmakingFailure(errorMessage);
            }
        });
    }

    private void showWaitingForMatchFragment() {
        if (!isAdded()) {
            return;
        }

        WaitingForMatchFragment existingFragment = (WaitingForMatchFragment)
                getParentFragmentManager().findFragmentByTag(WaitingForMatchFragment.TAG);

        if (existingFragment != null) {
            return;
        }

        WaitingForMatchFragment waitingForMatchFragment = WaitingForMatchFragment.newInstance();
        waitingForMatchFragment.setOnCancelSearchListener(this::cancelMatchmaking);
        waitingForMatchFragment.show(getParentFragmentManager(), WaitingForMatchFragment.TAG);
    }

    private void cancelMatchmaking() {
        webSocketGameClient.leaveRankedQueue(new WebSocketGameClient.OnRequestResult() {
            @Override
            public void onSuccess(org.json.JSONObject data) {
                resetMatchmakingState();
            }

            @Override
            public void onFailure(String errorMessage) {
                resetMatchmakingState();
                if (isAdded()) {
                    Toast.makeText(requireContext(), errorMessage, Toast.LENGTH_LONG).show();
                }
            }
        });
    }

    private void handleMatchFound(SessionSnapshot session) {
        if (!isAdded()) {
            return;
        }

        dismissWaitingForMatchFragment();
        resetMatchmakingState();

        requireActivity()
                .getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragmentContainer, GeneralKnowledgeFragment.newInstance(session.toJson().toString()))
                .commit();
    }

    private void handleMatchmakingFailure(String errorMessage) {
        dismissWaitingForMatchFragment();
        resetMatchmakingState();
        if (isAdded()) {
            Toast.makeText(requireContext(), errorMessage, Toast.LENGTH_LONG).show();
        }
    }

    private void resetMatchmakingState() {
        matchmakingInProgress = false;
        if (playGameButton != null) {
            playGameButton.setEnabled(true);
        }
        clearMatchmakingListener();
    }

    private void clearMatchmakingListener() {
        if (matchmakingListenerHandle != null) {
            matchmakingListenerHandle.remove();
            matchmakingListenerHandle = null;
        }
    }

    private void dismissWaitingForMatchFragment() {
        Fragment fragment = getParentFragmentManager().findFragmentByTag(WaitingForMatchFragment.TAG);
        if (fragment instanceof WaitingForMatchFragment) {
            ((WaitingForMatchFragment) fragment).dismissAllowingStateLoss();
        }
    }

    private void showServerUrlDialog() {
        if (!isAdded()) {
            return;
        }

        EditText serverUrlInput = new EditText(requireContext());
        serverUrlInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        serverUrlInput.setHint(getString(R.string.server_url_hint));
        serverUrlInput.setText(WebSocketConfig.getServerUrl(requireContext()));
        int horizontalPadding = getResources().getDimensionPixelSize(R.dimen.dialog_horizontal_padding);
        int verticalPadding = getResources().getDimensionPixelSize(R.dimen.dialog_vertical_padding);
        serverUrlInput.setPadding(horizontalPadding, verticalPadding, horizontalPadding, verticalPadding);

        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.server_url_title)
                .setMessage(R.string.server_url_message)
                .setView(serverUrlInput)
                .setPositiveButton(R.string.save_server_url, (dialog, which) -> {
                    String serverUrl = serverUrlInput.getText().toString();
                    WebSocketConfig.saveServerUrl(requireContext(), serverUrl);
                    webSocketGameClient.setServerUrl(WebSocketConfig.getServerUrl(requireContext()));
                    Toast.makeText(requireContext(), R.string.server_url_saved, Toast.LENGTH_SHORT).show();
                })
                .setNeutralButton(R.string.reset_server_url, (dialog, which) -> {
                    WebSocketConfig.resetServerUrl(requireContext());
                    webSocketGameClient.setServerUrl(WebSocketConfig.getServerUrl(requireContext()));
                    Toast.makeText(requireContext(), R.string.server_url_reset, Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }
}
