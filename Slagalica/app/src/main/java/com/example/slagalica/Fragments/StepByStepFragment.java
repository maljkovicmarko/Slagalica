package com.example.slagalica.Fragments;

import android.graphics.Color;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
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
import java.util.List;
import java.util.Locale;

public class StepByStepFragment extends Fragment {
    private static final String ARG_SESSION_JSON = "sessionJson";
    private static final String GAME_TYPE_STEP_BY_STEP = "step_by_step";
    private static final String ACTION_GUESS = "step_by_step_guess";

    private TextView roundText;
    private TextView timerText;
    private TextView playerTurnText;
    private TextView playerOneScoreText;
    private TextView playerTwoScoreText;
    private EditText answerInput;
    private Button submitAnswerButton;

    private final List<TextView> stepViews = new ArrayList<>();

    private CountDownTimer timer;
    private WebSocketGameClient webSocketGameClient;
    private WebSocketGameClient.ListenerHandle sessionListenerHandle;

    private String sessionJson;
    private String sessionId;
    private String currentUid;
    private long currentPhaseVersion;
    private long renderedPhaseVersion;
    private int playerOneScore;
    private int playerTwoScore;
    private boolean canAct;
    private boolean requestInProgress;
    private boolean finishedNavigated;

    public StepByStepFragment() {
    }

    public static StepByStepFragment newInstance(String sessionJson) {
        StepByStepFragment fragment = new StepByStepFragment();
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
        View view = inflater.inflate(R.layout.fragment_step_by_step, container, false);
        bindViews(view);
        updateScoreText();
        resetStepViews();
        submitAnswerButton.setOnClickListener(v -> submitAnswer());

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

    private void bindViews(View view) {
        roundText = view.findViewById(R.id.roundText);
        timerText = view.findViewById(R.id.timerText);
        playerTurnText = view.findViewById(R.id.playerTurnText);
        answerInput = view.findViewById(R.id.answerInput);
        submitAnswerButton = view.findViewById(R.id.submitAnswerButton);
        playerOneScoreText = view.findViewById(R.id.playerOneScoreText);
        playerTwoScoreText = view.findViewById(R.id.playerTwoScoreText);

        stepViews.clear();
        stepViews.add(view.findViewById(R.id.stepOneText));
        stepViews.add(view.findViewById(R.id.stepTwoText));
        stepViews.add(view.findViewById(R.id.stepThreeText));
        stepViews.add(view.findViewById(R.id.stepFourText));
        stepViews.add(view.findViewById(R.id.stepFiveText));
        stepViews.add(view.findViewById(R.id.stepSixText));
        stepViews.add(view.findViewById(R.id.stepSevenText));
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
                showToast(errorMessage);
            }
        });
    }

    private void renderSession(SessionSnapshot snapshot) {
        if (!isAdded() || timerText == null || snapshot == null) {
            return;
        }

        sessionId = snapshot.getString("sessionId");
        if (isTerminalSession(snapshot)) {
            finishMatch();
            return;
        }
        ActiveSessionTracker.markActiveSession(sessionId);

        String currentGame = snapshot.getString("currentGame");
        if (currentGame != null && !GAME_TYPE_STEP_BY_STEP.equals(currentGame)) {
            openFindTheNumber(snapshot.toJson().toString());
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
        requestInProgress = false;

        updateScoreText();
        renderTurnText(snapshot.getTurnState(), activeGameState);
        renderSteps(currentRound);
        renderAnswer(currentRound);
        renderPhase(snapshot.getGamePhase(), snapshot.getLong("serverNowMs"));
        updateInputAvailability();
    }

    private void renderTurnText(TurnSnapshot turnState, JSONObject activeGameState) {
        canAct = turnState != null && turnState.canAct(currentUid);
        roundText.setText(String.format(
                Locale.getDefault(),
                "Runda: %d/%d",
                activeGameState.optInt("roundNumber", 1),
                activeGameState.optInt("totalRounds", 2)
        ));

        if (turnState == null || turnState.getActivePlayerUid() == null) {
            playerTurnText.setText("Čekamo sledeću rundu");
        } else if (turnState.getActivePlayerUid().equals(currentUid)) {
            playerTurnText.setText("Tvoj potez");
        } else {
            playerTurnText.setText("Protivnik je na potezu");
        }
    }

    private void renderSteps(JSONObject currentRound) {
        resetStepViews();
        JSONArray steps = currentRound.optJSONArray("steps");
        if (steps == null) {
            return;
        }

        for (int i = 0; i < steps.length() && i < stepViews.size(); i++) {
            JSONObject step = steps.optJSONObject(i);
            if (step == null) {
                continue;
            }
            TextView stepView = stepViews.get(i);
            if (step.optBoolean("opened", false)) {
                stepView.setText(step.optString("value", "-"));
                stepView.setBackgroundColor(Color.LTGRAY);
                stepView.setTextColor(Color.BLACK);
            }
        }
    }

    private void renderAnswer(JSONObject currentRound) {
        if (currentRound.optBoolean("answerRevealed", false)) {
            playerTurnText.setText("Rešenje: " + currentRound.optString("answer", "-"));
            for (TextView stepView : stepViews) {
                stepView.setBackgroundColor(Color.rgb(76, 175, 80));
                stepView.setTextColor(Color.WHITE);
            }
        }
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
        timer = new CountDownTimer(Math.max(remainingMs, 1L), 250L) {
            @Override
            public void onTick(long millisUntilFinished) {
                long seconds = (long) Math.ceil(millisUntilFinished / 1000.0);
                timerText.setText(seconds + "s");
            }

            @Override
            public void onFinish() {
                timerText.setText("0s");
                disableInput();
            }
        };
        timer.start();
    }

    private void submitAnswer() {
        if (!canAct || requestInProgress || sessionId == null || currentPhaseVersion <= 0L) {
            return;
        }

        String guess = answerInput.getText().toString().trim();
        if (guess.isEmpty()) {
            answerInput.setError("Unesi odgovor");
            return;
        }

        requestInProgress = true;
        disableInput();
        try {
            JSONObject data = new JSONObject();
            data.put("guess", guess);
            webSocketGameClient.sendGameAction(
                    sessionId,
                    ACTION_GUESS,
                    currentPhaseVersion,
                    data,
                    new WebSocketGameClient.OnRequestResult() {
                        @Override
                        public void onSuccess(JSONObject data) {
                            answerInput.setText("");
                            refreshSessionAfterAction();
                        }

                        @Override
                        public void onFailure(String errorMessage) {
                            requestInProgress = false;
                            showToast(errorMessage);
                            refreshSessionAfterAction();
                        }
                    }
            );
        } catch (JSONException e) {
            requestInProgress = false;
            showToast("Slanje odgovora nije uspelo");
            updateInputAvailability();
        }
    }

    private void refreshSessionAfterAction() {
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
                requestInProgress = false;
                showToast(errorMessage);
            }
        });
    }

    private void updateInputAvailability() {
        boolean enabled = canAct && !requestInProgress;
        answerInput.setEnabled(enabled);
        submitAnswerButton.setEnabled(enabled);
    }

    private void disableInput() {
        answerInput.setEnabled(false);
        submitAnswerButton.setEnabled(false);
    }

    private void showLoadingState() {
        timerText.setText("-");
        roundText.setText("Runda: -/-");
        playerTurnText.setText("Korak po korak nije učitan");
        resetStepViews();
        updateScoreText();
        disableInput();
    }

    private void resetStepViews() {
        for (int i = 0; i < stepViews.size(); i++) {
            TextView stepView = stepViews.get(i);
            stepView.setText("Korak " + (i + 1));
            stepView.setBackgroundColor(Color.DKGRAY);
            stepView.setTextColor(Color.WHITE);
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

        FindTheNumberFragment fragment = new FindTheNumberFragment();
        fragment.setArguments(bundle);

        requireActivity()
                .getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragmentContainer, fragment)
                .commit();
    }

    private void openFindTheNumber(String nextSessionJson) {
        if (finishedNavigated) {
            return;
        }
        finishedNavigated = true;
        stopTimer();

        requireActivity()
                .getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragmentContainer, FindTheNumberFragment.newInstance(nextSessionJson))
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

    private void stopTimer() {
        if (timer != null) {
            timer.cancel();
            timer = null;
        }
    }

    private void showToast(String message) {
        if (isAdded() && message != null && !message.isBlank()) {
            Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
        }
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
