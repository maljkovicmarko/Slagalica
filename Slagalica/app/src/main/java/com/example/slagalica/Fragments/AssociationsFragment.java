package com.example.slagalica.Fragments;

import android.content.res.ColorStateList;
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

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class AssociationsFragment extends Fragment {
    private static final String ARG_SESSION_JSON = "sessionJson";
    private static final String GAME_TYPE_ASSOCIATIONS = "associations";
    private static final String TURN_MODE_OPEN_FIELD = "open_field";
    private static final String TURN_MODE_GUESS_OR_PASS = "guess_or_pass";
    private static final String ACTION_OPEN_FIELD = "associations_open_field";
    private static final String ACTION_GUESS_COLUMN = "associations_guess_column";
    private static final String ACTION_GUESS_FINAL = "associations_guess_final";
    private static final String ACTION_PASS = "associations_pass";

    private TextView timerText;
    private TextView playerTurnText;
    private TextView playerOneScoreText;
    private TextView playerTwoScoreText;

    private EditText columnAInput;
    private EditText columnBInput;
    private EditText columnCInput;
    private EditText columnDInput;
    private EditText finalSolutionInput;

    private Button guessFinalSolutionButton;
    private Button submitColumnSolutionButton;
    private Button passTurnButton;
    private Button a1Button, a2Button, a3Button, a4Button, columnASolutionButton;
    private Button b1Button, b2Button, b3Button, b4Button, columnBSolutionButton;
    private Button c1Button, c2Button, c3Button, c4Button, columnCSolutionButton;
    private Button d1Button, d2Button, d3Button, d4Button, columnDSolutionButton;

    private final int DEFAULT_COLOR = Color.rgb(111, 75, 179);
    private final int REVEALED_COLOR = Color.rgb(180, 180, 180);
    private final int CORRECT_COLOR = Color.rgb(76, 175, 80);

    private final Map<String, Button[]> fieldButtonsByColumnId = new HashMap<>();
    private final Map<String, Button> solutionButtonsByColumnId = new HashMap<>();
    private final Map<String, EditText> inputsByColumnId = new HashMap<>();

    private CountDownTimer timer;
    private WebSocketGameClient webSocketGameClient;
    private WebSocketGameClient.ListenerHandle sessionListenerHandle;

    private String sessionJson;
    private String sessionId;
    private String currentUid;
    private String focusedColumnId;
    private String turnMode;
    private long currentPhaseVersion;
    private long renderedPhaseVersion;
    private int playerOneScore;
    private int playerTwoScore;
    private boolean canAct;
    private boolean requestInProgress;
    private boolean finishedNavigated;

    public AssociationsFragment() {
    }

    public static AssociationsFragment newInstance(String sessionJson) {
        AssociationsFragment fragment = new AssociationsFragment();
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
        View view = inflater.inflate(R.layout.fragment_associations, container, false);
        bindViews(view);
        setupMaps();
        setupClickListeners();
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
        playerTurnText = view.findViewById(R.id.playerTurnText);
        playerOneScoreText = view.findViewById(R.id.playerOneScoreText);
        playerTwoScoreText = view.findViewById(R.id.playerTwoScoreText);

        columnAInput = view.findViewById(R.id.columnAInput);
        columnBInput = view.findViewById(R.id.columnBInput);
        columnCInput = view.findViewById(R.id.columnCInput);
        columnDInput = view.findViewById(R.id.columnDInput);
        finalSolutionInput = view.findViewById(R.id.finalSolutionInput);
        guessFinalSolutionButton = view.findViewById(R.id.guessFinalSolutionButton);
        submitColumnSolutionButton = view.findViewById(R.id.submitColumnSolutionButton);
        passTurnButton = view.findViewById(R.id.passTurnButton);

        a1Button = view.findViewById(R.id.a1Button);
        a2Button = view.findViewById(R.id.a2Button);
        a3Button = view.findViewById(R.id.a3Button);
        a4Button = view.findViewById(R.id.a4Button);
        columnASolutionButton = view.findViewById(R.id.columnASolutionButton);
        b1Button = view.findViewById(R.id.b1Button);
        b2Button = view.findViewById(R.id.b2Button);
        b3Button = view.findViewById(R.id.b3Button);
        b4Button = view.findViewById(R.id.b4Button);
        columnBSolutionButton = view.findViewById(R.id.columnBSolutionButton);
        c1Button = view.findViewById(R.id.c1Button);
        c2Button = view.findViewById(R.id.c2Button);
        c3Button = view.findViewById(R.id.c3Button);
        c4Button = view.findViewById(R.id.c4Button);
        columnCSolutionButton = view.findViewById(R.id.columnCSolutionButton);
        d1Button = view.findViewById(R.id.d1Button);
        d2Button = view.findViewById(R.id.d2Button);
        d3Button = view.findViewById(R.id.d3Button);
        d4Button = view.findViewById(R.id.d4Button);
        columnDSolutionButton = view.findViewById(R.id.columnDSolutionButton);
    }

    private void setupMaps() {
        fieldButtonsByColumnId.put("A", new Button[]{a1Button, a2Button, a3Button, a4Button});
        fieldButtonsByColumnId.put("B", new Button[]{b1Button, b2Button, b3Button, b4Button});
        fieldButtonsByColumnId.put("C", new Button[]{c1Button, c2Button, c3Button, c4Button});
        fieldButtonsByColumnId.put("D", new Button[]{d1Button, d2Button, d3Button, d4Button});

        solutionButtonsByColumnId.put("A", columnASolutionButton);
        solutionButtonsByColumnId.put("B", columnBSolutionButton);
        solutionButtonsByColumnId.put("C", columnCSolutionButton);
        solutionButtonsByColumnId.put("D", columnDSolutionButton);

        inputsByColumnId.put("A", columnAInput);
        inputsByColumnId.put("B", columnBInput);
        inputsByColumnId.put("C", columnCInput);
        inputsByColumnId.put("D", columnDInput);
    }

    private void setupClickListeners() {
        setupColumnFieldClicks("A");
        setupColumnFieldClicks("B");
        setupColumnFieldClicks("C");
        setupColumnFieldClicks("D");

        setupColumnInputFocus("A");
        setupColumnInputFocus("B");
        setupColumnInputFocus("C");
        setupColumnInputFocus("D");
        submitColumnSolutionButton.setOnClickListener(v -> {
            if (focusedColumnId != null) {
                guessColumn(focusedColumnId);
            }
        });
        guessFinalSolutionButton.setOnClickListener(v -> guessFinal());
        passTurnButton.setOnClickListener(v -> sendPass());
    }

    private void setupColumnInputFocus(String columnId) {
        EditText input = inputsByColumnId.get(columnId);
        if (input == null) {
            return;
        }

        input.setOnFocusChangeListener((view, hasFocus) -> {
            if (hasFocus) {
                focusedColumnId = columnId;
            }
            updateSubmitColumnSolutionButton();
        });
    }

    private void setupColumnFieldClicks(String columnId) {
        Button[] buttons = fieldButtonsByColumnId.get(columnId);
        if (buttons == null) {
            return;
        }
        for (int i = 0; i < buttons.length; i++) {
            final int fieldIndex = i;
            buttons[i].setOnClickListener(v -> openField(columnId, fieldIndex));
        }
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
        if (currentGame != null && !GAME_TYPE_ASSOCIATIONS.equals(currentGame)) {
            openGuessCombination(snapshot.toJson().toString());
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
        turnMode = activeGameState.optString("turnMode", TURN_MODE_OPEN_FIELD);
        requestInProgress = false;

        updateScoreText();
        renderTurnText(snapshot.getTurnState(), activeGameState);
        renderRound(currentRound);
        renderPhase(snapshot.getGamePhase(), snapshot.getLong("serverNowMs"));
    }

    private void renderTurnText(TurnSnapshot turnState, JSONObject activeGameState) {
        canAct = turnState != null && turnState.canAct(currentUid);
        String roundText = String.format(
                Locale.getDefault(),
                "Runda %d/%d · ",
                activeGameState.optInt("roundNumber", 1),
                activeGameState.optInt("totalRounds", 2)
        );

        if (turnState == null || turnState.getActivePlayerUid() == null) {
            playerTurnText.setText(roundText + "Čekamo sledeću rundu");
        } else if (turnState.getActivePlayerUid().equals(currentUid)) {
            playerTurnText.setText(roundText + "Tvoj potez");
        } else {
            playerTurnText.setText(roundText + "Protivnik je na potezu");
        }
    }

    private void renderRound(JSONObject currentRound) {
        JSONArray columns = currentRound.optJSONArray("columns");
        if (columns == null) {
            return;
        }

        for (int i = 0; i < columns.length(); i++) {
            JSONObject column = columns.optJSONObject(i);
            if (column == null) {
                continue;
            }
            renderColumn(column);
        }

        boolean canGuess = canAct && !requestInProgress;
        boolean canOpen = canGuess && TURN_MODE_OPEN_FIELD.equals(turnMode);
        boolean canGuessOrPass = canGuess && TURN_MODE_GUESS_OR_PASS.equals(turnMode);

        setFieldsEnabled(canOpen);
        setInputsEnabled(canGuessOrPass);
        updateSubmitColumnSolutionButton();
        guessFinalSolutionButton.setEnabled(canGuessOrPass);
        guessFinalSolutionButton.setText(canGuessOrPass ? "Pogodi konačno rešenje" : "Čekaj potez");
        passTurnButton.setEnabled(canGuessOrPass);
    }

    private void renderColumn(JSONObject column) {
        String columnId = column.optString("id", null);
        if (columnId == null) {
            return;
        }

        boolean solved = column.optBoolean("solved", false);
        Button solutionButton = solutionButtonsByColumnId.get(columnId);
        EditText input = inputsByColumnId.get(columnId);
        if (solutionButton != null) {
            if (solved) {
                solutionButton.setText(column.optString("solution", "Rešenje " + columnId));
                setButtonColor(solutionButton, CORRECT_COLOR);
                solutionButton.setEnabled(false);
            } else {
                solutionButton.setText("Rešenje " + columnId);
                setButtonColor(solutionButton, DEFAULT_COLOR);
                solutionButton.setEnabled(canAct && TURN_MODE_GUESS_OR_PASS.equals(turnMode) && !requestInProgress);
            }
            solutionButton.setVisibility(View.GONE);
        }
        if (input != null) {
            input.setEnabled(!solved && canAct && TURN_MODE_GUESS_OR_PASS.equals(turnMode) && !requestInProgress);
            if (solved) {
                input.setText("");
                input.setHint(column.optString("solution", "Rešenje " + columnId));
            }
        }

        JSONArray fields = column.optJSONArray("fields");
        Button[] fieldButtons = fieldButtonsByColumnId.get(columnId);
        if (fields == null || fieldButtons == null) {
            return;
        }

        for (int i = 0; i < fieldButtons.length; i++) {
            Button button = fieldButtons[i];
            JSONObject field = i < fields.length() ? fields.optJSONObject(i) : null;
            if (field == null) {
                button.setText(columnId + (i + 1));
                button.setEnabled(false);
                continue;
            }

            boolean opened = field.optBoolean("opened", false);
            button.setText(opened ? field.optString("value", "-") : field.optString("label", columnId + (i + 1)));
            setButtonColor(button, opened ? REVEALED_COLOR : DEFAULT_COLOR);
            button.setEnabled(!opened && canAct && TURN_MODE_OPEN_FIELD.equals(turnMode) && !requestInProgress);
        }
    }

    private void setFieldsEnabled(boolean enabled) {
        for (Button[] buttons : fieldButtonsByColumnId.values()) {
            for (Button button : buttons) {
                button.setEnabled(enabled && button.getText() != null && button.getText().toString().matches("[A-D][1-4]"));
            }
        }
    }

    private void setInputsEnabled(boolean enabled) {
        for (Map.Entry<String, EditText> entry : inputsByColumnId.entrySet()) {
            Button solutionButton = solutionButtonsByColumnId.get(entry.getKey());
            boolean solved = solutionButton != null && !solutionButton.isEnabled() && !solutionButton.getText().toString().startsWith("Rešenje");
            entry.getValue().setEnabled(enabled && !solved);
            if (solutionButton != null && !solved) {
                solutionButton.setEnabled(enabled);
            }
        }
        finalSolutionInput.setEnabled(enabled);
    }

    private void updateSubmitColumnSolutionButton() {
        boolean visible = focusedColumnId != null
                && canAct
                && !requestInProgress
                && TURN_MODE_GUESS_OR_PASS.equals(turnMode);
        submitColumnSolutionButton.setVisibility(visible ? View.VISIBLE : View.GONE);
        submitColumnSolutionButton.setEnabled(visible);
        if (visible) {
            submitColumnSolutionButton.setText("Potvrdi rešenje kolone " + focusedColumnId);
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
                disableAllInputs();
            }
        };
        timer.start();
    }

    private void openField(String columnId, int fieldIndex) {
        if (!canAct || requestInProgress || !TURN_MODE_OPEN_FIELD.equals(turnMode)) {
            return;
        }

        try {
            JSONObject data = new JSONObject();
            data.put("columnId", columnId);
            data.put("fieldIndex", fieldIndex);
            sendAction(ACTION_OPEN_FIELD, data);
        } catch (JSONException e) {
            showToast("Otvaranje polja nije uspelo");
        }
    }

    private void guessColumn(String columnId) {
        if (!canAct || requestInProgress || !TURN_MODE_GUESS_OR_PASS.equals(turnMode)) {
            return;
        }

        EditText input = inputsByColumnId.get(columnId);
        String guess = input == null ? "" : input.getText().toString().trim();
        if (guess.isEmpty()) {
            if (input != null) {
                input.setError("Unesi rešenje");
            }
            return;
        }

        try {
            JSONObject data = new JSONObject();
            data.put("columnId", columnId);
            data.put("guess", guess);
            sendAction(ACTION_GUESS_COLUMN, data);
            if (input != null) {
                input.setText("");
            }
        } catch (JSONException e) {
            showToast("Slanje rešenja kolone nije uspelo");
        }
    }

    private void guessFinal() {
        if (!canAct || requestInProgress || !TURN_MODE_GUESS_OR_PASS.equals(turnMode)) {
            return;
        }

        String guess = finalSolutionInput.getText().toString().trim();
        if (guess.isEmpty()) {
            finalSolutionInput.setError("Unesi konačno rešenje");
            return;
        }

        try {
            JSONObject data = new JSONObject();
            data.put("guess", guess);
            sendAction(ACTION_GUESS_FINAL, data);
            finalSolutionInput.setText("");
        } catch (JSONException e) {
            showToast("Slanje konačnog rešenja nije uspelo");
        }
    }

    private void sendPass() {
        sendAction(ACTION_PASS, new JSONObject());
    }

    private void sendAction(String actionType, JSONObject data) {
        if (sessionId == null || currentPhaseVersion <= 0L) {
            return;
        }

        requestInProgress = true;
        disableAllInputs();
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
        playerTurnText.setText("Asocijacije nisu učitane");
        updateScoreText();
        disableAllInputs();
    }

    private void disableAllInputs() {
        for (Button[] buttons : fieldButtonsByColumnId.values()) {
            for (Button button : buttons) {
                button.setEnabled(false);
            }
        }
        for (Button button : solutionButtonsByColumnId.values()) {
            button.setEnabled(false);
        }
        for (EditText input : inputsByColumnId.values()) {
            input.setEnabled(false);
        }
        finalSolutionInput.setEnabled(false);
        submitColumnSolutionButton.setEnabled(false);
        submitColumnSolutionButton.setVisibility(View.GONE);
        guessFinalSolutionButton.setEnabled(false);
        passTurnButton.setEnabled(false);
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

        GuessTheCombinationFragment fragment = new GuessTheCombinationFragment();
        fragment.setArguments(bundle);

        requireActivity()
                .getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragmentContainer, fragment)
                .commit();
    }

    private void openGuessCombination(String nextSessionJson) {
        if (finishedNavigated) {
            return;
        }
        finishedNavigated = true;
        stopTimer();

        requireActivity()
                .getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragmentContainer, GuessTheCombinationFragment.newInstance(nextSessionJson))
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
