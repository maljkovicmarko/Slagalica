package com.example.slagalica.Fragments;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.fragment.app.Fragment;

import com.example.slagalica.R;
import com.example.slagalica.Services.ActiveSessionTracker;
import com.example.slagalica.Services.GamePhaseSnapshot;
import com.example.slagalica.Services.SessionSnapshot;
import com.example.slagalica.Services.TurnSnapshot;
import com.example.slagalica.Services.WebSocketGameClient;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class ConnectingGameFragment extends Fragment {
    private static final String ARG_SESSION_JSON = "sessionJson";
    private static final String ACTION_SUBMIT_PAIR = "connections_submit_pair";
    private static final String GAME_TYPE_CONNECTIONS = "connections";

    private TextView timerText;
    private TextView roundText;
    private TextView playerTurnText;
    private TextView criterionText;
    private TextView playerOneScoreText;
    private TextView playerTwoScoreText;

    private final int DEFAULT_COLOR = Color.rgb(111, 75, 179);
    private final int SELECTED_COLOR = Color.rgb(90, 65, 140);
    private final int CORRECT_COLOR = Color.rgb(76, 175, 80);
    private final int WRONG_COLOR = Color.rgb(244, 67, 54);

    private final List<Button> leftButtons = new ArrayList<>();
    private final List<Button> rightButtons = new ArrayList<>();
    private final Map<String, JSONObject> leftItemsById = new HashMap<>();
    private final Map<String, JSONObject> rightItemsById = new HashMap<>();

    private CountDownTimer roundTimer;
    private WebSocketGameClient webSocketGameClient;
    private WebSocketGameClient.ListenerHandle sessionListenerHandle;

    private String sessionJson;
    private String sessionId;
    private String currentUid;
    private String selectedLeftId;
    private String selectedRightId;
    private long currentPhaseVersion;
    private long renderedPhaseVersion;
    private int playerOneScore;
    private int playerTwoScore;
    private boolean finishedNavigated;
    private boolean canAct;
    private boolean submitInProgress;

    public ConnectingGameFragment() {
    }

    public static ConnectingGameFragment newInstance(String sessionJson) {
        ConnectingGameFragment fragment = new ConnectingGameFragment();
        Bundle args = new Bundle();
        args.putString(ARG_SESSION_JSON, sessionJson);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        webSocketGameClient = WebSocketGameClient.getInstance();
        FirebaseUser firebaseUser = FirebaseAuth.getInstance().getCurrentUser();
        currentUid = firebaseUser == null ? null : firebaseUser.getUid();
        if (getArguments() != null) {
            sessionJson = getArguments().getString(ARG_SESSION_JSON);
            playerOneScore = getArguments().getInt("playerOneScore", 0);
            playerTwoScore = getArguments().getInt("playerTwoScore", 0);
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_connecting_game, container, false);

        timerText = view.findViewById(R.id.timerText);
        roundText = view.findViewById(R.id.roundText);
        playerTurnText = view.findViewById(R.id.playerTurnText);
        criterionText = view.findViewById(R.id.criterionText);
        playerOneScoreText = view.findViewById(R.id.playerOneScoreText);
        playerTwoScoreText = view.findViewById(R.id.playerTwoScoreText);
        leftButtons.clear();
        leftButtons.add(view.findViewById(R.id.leftOneButton));
        leftButtons.add(view.findViewById(R.id.leftTwoButton));
        leftButtons.add(view.findViewById(R.id.leftThreeButton));
        leftButtons.add(view.findViewById(R.id.leftFourButton));
        leftButtons.add(view.findViewById(R.id.leftFiveButton));

        rightButtons.clear();
        rightButtons.add(view.findViewById(R.id.rightOneButton));
        rightButtons.add(view.findViewById(R.id.rightTwoButton));
        rightButtons.add(view.findViewById(R.id.rightThreeButton));
        rightButtons.add(view.findViewById(R.id.rightFourButton));
        rightButtons.add(view.findViewById(R.id.rightFiveButton));

        for (Button button : leftButtons) {
            button.setOnClickListener(v -> selectLeft((Button) v));
        }
        for (Button button : rightButtons) {
            button.setOnClickListener(v -> selectRight((Button) v));
        }
        if (sessionJson == null || sessionJson.isBlank()) {
            showLoadingState();
        } else try {
            renderSession(new SessionSnapshot(new JSONObject(sessionJson)));
            subscribeSessionUpdates();
        } catch (Exception ignored) {
            showLoadingState();
        }

        return view;
    }

    private void subscribeSessionUpdates() {
        if (sessionId == null || sessionId.isBlank() || sessionListenerHandle != null) {
            return;
        }

        sessionListenerHandle = webSocketGameClient.subscribeSession(sessionId, new WebSocketGameClient.OnSessionListener() {
            @Override
            public void onSessionState(SessionSnapshot snapshot) {
                renderSession(snapshot);
            }

            @Override
            public void onFailure(String errorMessage) {
                if (isAdded()) {
                    Toast.makeText(requireContext(), errorMessage, Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    private void renderSession(SessionSnapshot snapshot) {
        if (!isAdded() || criterionText == null || snapshot == null) {
            return;
        }

        sessionId = snapshot.getString("sessionId");
        if (isTerminalSession(snapshot)) {
            finishMatch();
            return;
        }
        ActiveSessionTracker.markActiveSession(sessionId);

        String currentGame = snapshot.getString("currentGame");
        if (currentGame != null && !GAME_TYPE_CONNECTIONS.equals(currentGame)) {
            openAssociations(snapshot.toJson().toString());
            return;
        }

        JSONObject activeGameState = snapshot.getObject("activeGameState");
        if (activeGameState == null || activeGameState.optBoolean("finished", false)) {
            finishGame();
            return;
        }

        JSONObject currentRound = activeGameState.optJSONObject("currentRound");
        if (currentRound == null) {
            showLoadingState();
            return;
        }

        playerOneScore = activeGameState.optInt("player1Score", playerOneScore);
        playerTwoScore = activeGameState.optInt("player2Score", playerTwoScore);
        updateScoreText();

        roundText.setText(String.format(
                Locale.getDefault(),
                "Runda: %d/%d",
                activeGameState.optInt("roundNumber", 1),
                activeGameState.optInt("totalRounds", 2)
        ));
        criterionText.setText(currentRound.optString("title", "-"));

        TurnSnapshot turnState = snapshot.getTurnState();
        canAct = turnState != null && turnState.canAct(currentUid);
        renderTurnText(turnState);

        submitInProgress = false;
        renderItems(currentRound);
        renderPhase(snapshot.getGamePhase(), snapshot.getLong("serverNowMs"));
    }

    private void renderTurnText(TurnSnapshot turnState) {
        if (turnState == null || turnState.getActivePlayerUid() == null) {
            playerTurnText.setText("Čekamo sledeću rundu");
        } else if (turnState.getActivePlayerUid().equals(currentUid)) {
            playerTurnText.setText("Tvoj potez");
        } else {
            playerTurnText.setText("Protivnik je na potezu");
        }
    }

    private void renderItems(JSONObject currentRound) {
        leftItemsById.clear();
        rightItemsById.clear();

        Set<String> resolvedLeftIds = parseStringSet(currentRound.optJSONArray("resolvedLeftIds"));
        Set<String> attemptedLeftIds = parseStringSet(currentRound.optJSONArray("attemptedLeftIds"));
        Set<String> resolvedRightIds = new HashSet<>();
        String latestWrongRightId = parseLatestWrongRightId(currentRound.optJSONArray("attemptResults"));

        JSONArray leftItems = currentRound.optJSONArray("leftItems");
        JSONArray rightItems = currentRound.optJSONArray("rightItems");
        if (leftItems == null || rightItems == null) {
            return;
        }

        for (int i = 0; i < leftItems.length(); i++) {
            JSONObject leftItem = leftItems.optJSONObject(i);
            if (leftItem != null && resolvedLeftIds.contains(leftItem.optString("id", null))) {
                resolvedRightIds.add(leftItem.optString("matchId", null));
            }
        }

        for (int i = 0; i < leftButtons.size(); i++) {
            Button button = leftButtons.get(i);
            JSONObject item = i < leftItems.length() ? leftItems.optJSONObject(i) : null;
            if (item == null) {
                button.setText("-");
                button.setEnabled(false);
                button.setTag(null);
                continue;
            }

            String id = item.optString("id", null);
            leftItemsById.put(id, item);
            button.setText(item.optString("text", "-"));
            button.setTag(id);

            if (resolvedLeftIds.contains(id)) {
                setButtonColor(button, CORRECT_COLOR);
                button.setEnabled(false);
            } else if (attemptedLeftIds.contains(id)) {
                setButtonColor(button, WRONG_COLOR);
                button.setEnabled(false);
            } else {
                setButtonColor(button, id != null && id.equals(selectedLeftId) ? SELECTED_COLOR : DEFAULT_COLOR);
                button.setEnabled(canAct && !submitInProgress);
            }
        }

        for (int i = 0; i < rightButtons.size(); i++) {
            Button button = rightButtons.get(i);
            JSONObject item = i < rightItems.length() ? rightItems.optJSONObject(i) : null;
            if (item == null) {
                button.setText("-");
                button.setEnabled(false);
                button.setTag(null);
                continue;
            }

            String id = item.optString("id", null);
            rightItemsById.put(id, item);
            button.setText(item.optString("text", "-"));
            button.setTag(id);

            if (resolvedRightIds.contains(id)) {
                setButtonColor(button, CORRECT_COLOR);
                button.setEnabled(false);
            } else if (id != null && id.equals(latestWrongRightId)) {
                setButtonColor(button, id != null && id.equals(selectedRightId) ? SELECTED_COLOR : WRONG_COLOR);
                button.setEnabled(canAct && !submitInProgress);
            } else {
                setButtonColor(button, id != null && id.equals(selectedRightId) ? SELECTED_COLOR : DEFAULT_COLOR);
                button.setEnabled(canAct && !submitInProgress);
            }
        }

        if (selectedLeftId != null && (!leftItemsById.containsKey(selectedLeftId)
                || resolvedLeftIds.contains(selectedLeftId)
                || attemptedLeftIds.contains(selectedLeftId))) {
            selectedLeftId = null;
        }
        if (selectedRightId != null && (!rightItemsById.containsKey(selectedRightId)
                || resolvedRightIds.contains(selectedRightId))) {
            selectedRightId = null;
        }
    }

    private String parseLatestWrongRightId(JSONArray attemptResults) {
        if (attemptResults == null) {
            return null;
        }

        for (int i = attemptResults.length() - 1; i >= 0; i--) {
            JSONObject attemptResult = attemptResults.optJSONObject(i);
            if (attemptResult == null || attemptResult.optBoolean("correct", false)) {
                continue;
            }

            String rightId = attemptResult.optString("rightId", null);
            if (rightId != null && !rightId.isBlank()) {
                return rightId;
            }
        }
        return null;
    }

    private Set<String> parseStringSet(JSONArray jsonArray) {
        Set<String> values = new HashSet<>();
        if (jsonArray == null) {
            return values;
        }

        for (int i = 0; i < jsonArray.length(); i++) {
            String value = jsonArray.optString(i, null);
            if (value != null && !value.isBlank()) {
                values.add(value);
            }
        }
        return values;
    }

    private void renderPhase(GamePhaseSnapshot gamePhase, Long serverNowMs) {
        if (gamePhase == null) {
            stopTimer();
            timerText.setText("-");
            currentPhaseVersion = 0L;
            renderedPhaseVersion = 0L;
            return;
        }

        currentPhaseVersion = gamePhase.getPhaseVersion();
        if (renderedPhaseVersion == currentPhaseVersion) {
            return;
        }

        renderedPhaseVersion = currentPhaseVersion;
        stopTimer();

        long referenceNowMs = serverNowMs == null ? System.currentTimeMillis() : serverNowMs;
        long remainingMs = gamePhase.getRemainingMs(referenceNowMs);
        roundTimer = new CountDownTimer(Math.max(remainingMs, 1L), 250L) {
            @Override
            public void onTick(long millisUntilFinished) {
                long seconds = (long) Math.ceil(millisUntilFinished / 1000.0);
                timerText.setText(seconds + "s");
            }

            @Override
            public void onFinish() {
                timerText.setText("0s");
                disableAllButtons();
            }
        };

        roundTimer.start();
    }

    private void selectLeft(Button button) {
        if (!canAct || button.getTag() == null) {
            return;
        }
        selectedLeftId = button.getTag().toString();
        refreshSelectionColors();
        submitIfPairSelected();
    }

    private void selectRight(Button button) {
        if (!canAct || button.getTag() == null) {
            return;
        }
        selectedRightId = button.getTag().toString();
        refreshSelectionColors();
        submitIfPairSelected();
    }

    private void refreshSelectionColors() {
        for (Button button : leftButtons) {
            if (button.isEnabled() && button.getTag() != null) {
                setButtonColor(button, button.getTag().equals(selectedLeftId) ? SELECTED_COLOR : DEFAULT_COLOR);
            }
        }
        for (Button button : rightButtons) {
            if (button.isEnabled() && button.getTag() != null) {
                setButtonColor(button, button.getTag().equals(selectedRightId) ? SELECTED_COLOR : DEFAULT_COLOR);
            }
        }
    }

    private void submitIfPairSelected() {
        if (selectedLeftId != null && selectedRightId != null) {
            submitSelectedPair();
        }
    }

    private void submitSelectedPair() {
        if (submitInProgress || !canAct || sessionId == null || currentPhaseVersion <= 0L || selectedLeftId == null || selectedRightId == null) {
            return;
        }

        submitInProgress = true;
        disableAllButtons();
        try {
            JSONObject data = new JSONObject();
            data.put("leftId", selectedLeftId);
            data.put("rightId", selectedRightId);
            webSocketGameClient.sendGameAction(
                    sessionId,
                    ACTION_SUBMIT_PAIR,
                    currentPhaseVersion,
                    data,
                    new WebSocketGameClient.OnRequestResult() {
                        @Override
                        public void onSuccess(JSONObject data) {
                            selectedLeftId = null;
                            selectedRightId = null;
                            refreshSessionAfterSubmit();
                        }

                        @Override
                        public void onFailure(String errorMessage) {
                            if (isAdded()) {
                                Toast.makeText(requireContext(), errorMessage, Toast.LENGTH_SHORT).show();
                            }
                            submitInProgress = false;
                            renderSelectableButtonsAfterFailure();
                        }
                    }
            );
        } catch (JSONException e) {
            Toast.makeText(requireContext(), "Slanje spojnice nije uspelo", Toast.LENGTH_SHORT).show();
            submitInProgress = false;
            renderSelectableButtonsAfterFailure();
        }
    }

    private void refreshSessionAfterSubmit() {
        webSocketGameClient.getSession(sessionId, new WebSocketGameClient.OnRequestResult() {
            @Override
            public void onSuccess(JSONObject data) {
                JSONObject sessionState = data == null ? null : data.optJSONObject("session");
                if (sessionState == null) {
                    sessionState = data;
                }
                renderSession(new SessionSnapshot(sessionState));
            }

            @Override
            public void onFailure(String errorMessage) {
                submitInProgress = false;
                renderSelectableButtonsAfterFailure();
            }
        });
    }

    private void renderSelectableButtonsAfterFailure() {
        for (Button button : leftButtons) {
            if (button.getTag() != null) {
                button.setEnabled(canAct);
            }
        }
        for (Button button : rightButtons) {
            if (button.getTag() != null) {
                button.setEnabled(canAct);
            }
        }
    }

    private void showLoadingState() {
        timerText.setText("-");
        roundText.setText("Runda: -/-");
        playerTurnText.setText("-");
        criterionText.setText("Spojnice nisu učitane");
        updateScoreText();
        disableAllButtons();
    }

    private void disableAllButtons() {
        for (Button button : leftButtons) {
            button.setEnabled(false);
        }
        for (Button button : rightButtons) {
            button.setEnabled(false);
        }
    }

    private void stopTimer() {
        if (roundTimer != null) {
            roundTimer.cancel();
            roundTimer = null;
        }
    }

    private void finishGame() {
        if (finishedNavigated) {
            return;
        }
        finishedNavigated = true;
        stopTimer();

        Bundle bundle = new Bundle();
        bundle.putInt("playerOneScore", playerOneScore);
        bundle.putInt("playerTwoScore", playerTwoScore);

        AssociationsFragment fragment = new AssociationsFragment();
        fragment.setArguments(bundle);

        requireActivity()
                .getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragmentContainer, fragment)
                .commit();
    }

    private void openAssociations(String nextSessionJson) {
        if (finishedNavigated) {
            return;
        }
        finishedNavigated = true;
        stopTimer();

        requireActivity()
                .getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragmentContainer, AssociationsFragment.newInstance(nextSessionJson))
                .commit();
    }

    private boolean isTerminalSession(SessionSnapshot snapshot) {
        String status = snapshot.getString("status");
        return "finished".equals(status);
    }

    private void finishMatch() {
        if (finishedNavigated) {
            return;
        }
        finishedNavigated = true;
        stopTimer();
        ActiveSessionTracker.clearActiveSession(sessionId);

        requireActivity()
                .getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragmentContainer, new HomeFragment())
                .commit();
    }

    private void updateScoreText() {
        playerOneScoreText.setText("Igrač 1: " + playerOneScore + " bodova");
        playerTwoScoreText.setText("Igrač 2: " + playerTwoScore + " bodova");
    }

    private void setButtonColor(Button button, int color) {
        button.setBackgroundTintList(ColorStateList.valueOf(color));
        button.setTextColor(Color.WHITE);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (sessionListenerHandle != null) {
            sessionListenerHandle.remove();
            sessionListenerHandle = null;
        }
        stopTimer();
    }
}
