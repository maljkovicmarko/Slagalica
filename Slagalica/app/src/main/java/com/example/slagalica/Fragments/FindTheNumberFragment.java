package com.example.slagalica.Fragments;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
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

public class FindTheNumberFragment extends Fragment implements SensorEventListener {
    private static final String ARG_SESSION_JSON = "sessionJson";
    private static final String GAME_TYPE_FIND_NUMBER = "find_the_number";
    private static final String PHASE_TARGET_STOP = "find_number_target_stop";
    private static final String PHASE_NUMBERS_STOP = "find_number_numbers_stop";
    private static final String PHASE_SOLVE = "find_number_solve";
    private static final String ACTION_STOP_TARGET = "find_number_stop_target";
    private static final String ACTION_STOP_NUMBERS = "find_number_stop_numbers";
    private static final String ACTION_SUBMIT = "find_number_submit";
    private static final float SHAKE_THRESHOLD = 18.0f;
    private static final long SHAKE_COOLDOWN_MS = 1200L;

    private TextView timerText;
    private TextView roundText;
    private TextView playerTurnText;
    private TextView targetNumberText;
    private TextView playerOneScoreText;
    private TextView playerTwoScoreText;
    private EditText expressionInput;
    private Button stopTargetButton;
    private Button stopNumbersButton;
    private Button submitExpressionButton;
    private Button clearButton;
    private Button openParenthesisButton;
    private Button closeParenthesisButton;
    private Button plusButton;
    private Button minusButton;
    private Button multiplyButton;
    private Button divideButton;

    private final List<Button> numberButtons = new ArrayList<>();
    private final List<Integer> currentNumbers = new ArrayList<>();

    private CountDownTimer timer;
    private WebSocketGameClient webSocketGameClient;
    private WebSocketGameClient.ListenerHandle sessionListenerHandle;
    private SensorManager sensorManager;
    private Sensor accelerometer;

    private String sessionJson;
    private String sessionId;
    private String currentUid;
    private String player1Uid;
    private String player2Uid;
    private String phaseType;
    private long currentPhaseVersion;
    private long renderedPhaseVersion;
    private long lastShakeAtMs;
    private int playerOneScore;
    private int playerTwoScore;
    private boolean canAct;
    private boolean requestInProgress;
    private boolean submitted;
    private boolean finishedNavigated;

    public FindTheNumberFragment() {
    }

    public static FindTheNumberFragment newInstance(String sessionJson) {
        FindTheNumberFragment fragment = new FindTheNumberFragment();
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
        sensorManager = (SensorManager) requireContext().getSystemService(Context.SENSOR_SERVICE);
        if (sensorManager != null) {
            accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_find_the_number, container, false);
        bindViews(view);
        setupButtons();
        updateScoreText();

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
        timerText = view.findViewById(R.id.timerText);
        roundText = view.findViewById(R.id.roundText);
        playerTurnText = view.findViewById(R.id.playerTurnText);
        targetNumberText = view.findViewById(R.id.targetNumberText);
        expressionInput = view.findViewById(R.id.expressionInput);
        stopTargetButton = view.findViewById(R.id.stopTargetButton);
        stopNumbersButton = view.findViewById(R.id.stopNumbersButton);
        submitExpressionButton = view.findViewById(R.id.submitExpressionButton);
        clearButton = view.findViewById(R.id.clearButton);
        openParenthesisButton = view.findViewById(R.id.openParenthesisButton);
        closeParenthesisButton = view.findViewById(R.id.closeParenthesisButton);
        plusButton = view.findViewById(R.id.plusButton);
        minusButton = view.findViewById(R.id.minusButton);
        multiplyButton = view.findViewById(R.id.multiplyButton);
        divideButton = view.findViewById(R.id.divideButton);
        playerOneScoreText = view.findViewById(R.id.playerOneScoreText);
        playerTwoScoreText = view.findViewById(R.id.playerTwoScoreText);

        numberButtons.clear();
        numberButtons.add(view.findViewById(R.id.numberOneButton));
        numberButtons.add(view.findViewById(R.id.numberTwoButton));
        numberButtons.add(view.findViewById(R.id.numberThreeButton));
        numberButtons.add(view.findViewById(R.id.numberFourButton));
        numberButtons.add(view.findViewById(R.id.numberFiveButton));
        numberButtons.add(view.findViewById(R.id.numberSixButton));
    }

    private void setupButtons() {
        stopTargetButton.setOnClickListener(v -> sendAction(ACTION_STOP_TARGET, new JSONObject()));
        stopNumbersButton.setOnClickListener(v -> sendAction(ACTION_STOP_NUMBERS, new JSONObject()));
        submitExpressionButton.setOnClickListener(v -> submitExpression());
        clearButton.setOnClickListener(v -> expressionInput.setText(""));
        openParenthesisButton.setOnClickListener(v -> appendToExpression("("));
        closeParenthesisButton.setOnClickListener(v -> appendToExpression(")"));
        plusButton.setOnClickListener(v -> appendToExpression("+"));
        minusButton.setOnClickListener(v -> appendToExpression("-"));
        multiplyButton.setOnClickListener(v -> appendToExpression("*"));
        divideButton.setOnClickListener(v -> appendToExpression("/"));
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
        sessionJson = snapshot.toJson().toString();
        player1Uid = snapshot.getString("player1Uid");
        player2Uid = snapshot.getString("player2Uid");
        if (isTerminalSession(snapshot)) {
            finishGame();
            return;
        }
        ActiveSessionTracker.markActiveSession(sessionId);

        String currentGame = snapshot.getString("currentGame");
        if (currentGame != null && !GAME_TYPE_FIND_NUMBER.equals(currentGame)) {
            finishGame();
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

        GamePhaseSnapshot gamePhase = snapshot.getGamePhase();
        phaseType = gamePhase == null ? null : gamePhase.getPhaseType();
        updateScoreText();
        renderTurnText(snapshot.getTurnState(), activeGameState);
        renderRound(currentRound);
        renderPhase(gamePhase, snapshot.getLong("serverNowMs"));
        updateControls();
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
            playerTurnText.setText("Čekamo kraj partije");
        } else if (PHASE_SOLVE.equals(phaseType)) {
            playerTurnText.setText("Oba igrača rešavaju");
        } else if (turnState.getActivePlayerUid().equals(currentUid)) {
            playerTurnText.setText("Tvoj potez");
        } else {
            playerTurnText.setText("Protivnik je na potezu");
        }
    }

    private void renderRound(JSONObject currentRound) {
        targetNumberText.setText(currentRound.isNull("targetNumber")
                ? "?"
                : String.valueOf(currentRound.optInt("targetNumber")));

        currentNumbers.clear();
        JSONArray numbers = currentRound.optJSONArray("numbers");
        for (int i = 0; i < numberButtons.size(); i++) {
            Button button = numberButtons.get(i);
            if (numbers != null && i < numbers.length()) {
                int number = numbers.optInt(i);
                currentNumbers.add(number);
                button.setText(String.valueOf(number));
                button.setOnClickListener(v -> appendToExpression(((Button) v).getText().toString()));
            } else {
                button.setText("?");
            }
        }

        submitted = hasCurrentUserSubmitted(currentRound);
    }

    private boolean hasCurrentUserSubmitted(JSONObject currentRound) {
        if (currentUid != null && currentUid.equals(player1Uid)) {
            return !currentRound.isNull("player1Submission");
        }
        if (currentUid != null && currentUid.equals(player2Uid)) {
            return !currentRound.isNull("player2Submission");
        }
        return false;
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
                disableControls();
            }
        };
        timer.start();
    }

    private void updateControls() {
        boolean targetStop = canAct && !requestInProgress && PHASE_TARGET_STOP.equals(phaseType);
        boolean numbersStop = canAct && !requestInProgress && PHASE_NUMBERS_STOP.equals(phaseType);
        boolean solving = canAct && !requestInProgress && PHASE_SOLVE.equals(phaseType) && !submitted;

        stopTargetButton.setEnabled(targetStop);
        stopNumbersButton.setEnabled(numbersStop);
        expressionInput.setEnabled(solving);
        submitExpressionButton.setEnabled(solving);
        clearButton.setEnabled(solving);
        openParenthesisButton.setEnabled(solving);
        closeParenthesisButton.setEnabled(solving);
        plusButton.setEnabled(solving);
        minusButton.setEnabled(solving);
        multiplyButton.setEnabled(solving);
        divideButton.setEnabled(solving);
        for (Button button : numberButtons) {
            button.setEnabled(solving && !"?".contentEquals(button.getText()));
        }
    }

    private void disableControls() {
        stopTargetButton.setEnabled(false);
        stopNumbersButton.setEnabled(false);
        expressionInput.setEnabled(false);
        submitExpressionButton.setEnabled(false);
        clearButton.setEnabled(false);
        openParenthesisButton.setEnabled(false);
        closeParenthesisButton.setEnabled(false);
        plusButton.setEnabled(false);
        minusButton.setEnabled(false);
        multiplyButton.setEnabled(false);
        divideButton.setEnabled(false);
        for (Button button : numberButtons) {
            button.setEnabled(false);
        }
    }

    private void appendToExpression(String value) {
        if (expressionInput.isEnabled()) {
            expressionInput.append(value);
        }
    }

    private void submitExpression() {
        if (!PHASE_SOLVE.equals(phaseType)) {
            return;
        }
        try {
            JSONObject data = new JSONObject();
            data.put("expression", expressionInput.getText().toString().trim());
            sendAction(ACTION_SUBMIT, data);
            expressionInput.setText("");
        } catch (JSONException e) {
            showToast("Slanje izraza nije uspelo");
        }
    }

    private void sendAction(String actionType, JSONObject data) {
        if (!canAct || requestInProgress || sessionId == null || currentPhaseVersion <= 0L) {
            return;
        }

        requestInProgress = true;
        disableControls();
        webSocketGameClient.sendGameAction(
                sessionId,
                actionType,
                currentPhaseVersion,
                data,
                new WebSocketGameClient.OnRequestResult() {
                    @Override
                    public void onSuccess(JSONObject data) {
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
    }

    private void refreshSessionAfterAction() {
        webSocketGameClient.getSession(sessionId, new WebSocketGameClient.OnRequestResult() {
            @Override
            public void onSuccess(JSONObject data) {
                JSONObject sessionState = data == null ? null : data.optJSONObject("session");
                if (sessionState == null) {
                    sessionState = data;
                }
                sessionJson = sessionState.toString();
                renderSession(new SessionSnapshot(sessionState));
            }

            @Override
            public void onFailure(String errorMessage) {
                requestInProgress = false;
                showToast(errorMessage);
            }
        });
    }

    private void showLoadingState() {
        timerText.setText("-");
        roundText.setText("Runda: -/-");
        playerTurnText.setText("Moj broj nije učitan");
        targetNumberText.setText("?");
        for (Button button : numberButtons) {
            button.setText("?");
        }
        updateScoreText();
        disableControls();
    }

    private void finishGame() {
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

    private boolean isTerminalSession(SessionSnapshot snapshot) {
        String status = snapshot.getString("status");
        return "finished".equals(status);
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
    public void onResume() {
        super.onResume();
        if (sensorManager != null && accelerometer != null) {
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_UI);
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (sensorManager != null) {
            sensorManager.unregisterListener(this);
        }
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() != Sensor.TYPE_ACCELEROMETER) {
            return;
        }

        float x = event.values[0];
        float y = event.values[1];
        float z = event.values[2];
        double acceleration = Math.sqrt(x * x + y * y + z * z);
        long now = System.currentTimeMillis();
        if (acceleration > SHAKE_THRESHOLD && now - lastShakeAtMs > SHAKE_COOLDOWN_MS) {
            lastShakeAtMs = now;
            if (PHASE_TARGET_STOP.equals(phaseType)) {
                sendAction(ACTION_STOP_TARGET, new JSONObject());
            } else if (PHASE_NUMBERS_STOP.equals(phaseType)) {
                sendAction(ACTION_STOP_NUMBERS, new JSONObject());
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
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
