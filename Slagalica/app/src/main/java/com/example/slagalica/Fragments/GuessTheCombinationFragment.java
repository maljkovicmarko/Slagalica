package com.example.slagalica.Fragments;

import android.graphics.Color;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
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
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class GuessTheCombinationFragment extends Fragment {
    private static final String ARG_SESSION_JSON = "sessionJson";
    private static final String GAME_TYPE_GUESS_COMBINATION = "guess_the_combination";
    private static final String ACTION_SUBMIT = "guess_combination_submit";
    private static final String PHASE_OPPONENT_ATTEMPT = "guess_combination_opponent_attempt";
    private static final int OPPONENT_ATTEMPT_ROW = 6;

    private TextView timerText;
    private TextView playerTurnText;
    private TextView playerOneScoreText;
    private TextView playerTwoScoreText;
    private Button checkCombinationButton;
    private ImageButton skockoSymbolButton;
    private ImageButton squareSymbolButton;
    private ImageButton circleSymbolButton;
    private ImageButton heartSymbolButton;
    private ImageButton triangleSymbolButton;
    private ImageButton starSymbolButton;

    private ImageView[][] attemptSlots;
    private TextView[] resultTexts;

    private final Map<String, Integer> drawableBySymbol = new HashMap<>();
    private final List<String> selectedCombination = new ArrayList<>();

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
    private int currentInputRow;

    public GuessTheCombinationFragment() {
    }

    public static GuessTheCombinationFragment newInstance(String sessionJson) {
        GuessTheCombinationFragment fragment = new GuessTheCombinationFragment();
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
        setupDrawableMap();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_guess_the_combination, container, false);
        bindViews(view);
        setupSymbolButtons();
        checkCombinationButton.setOnClickListener(v -> submitCombination());
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

    private void setupDrawableMap() {
        drawableBySymbol.put("SKOCKO", R.drawable.skocko);
        drawableBySymbol.put("SQUARE", R.drawable.square);
        drawableBySymbol.put("CIRCLE", R.drawable.circle);
        drawableBySymbol.put("HEART", R.drawable.heart);
        drawableBySymbol.put("TRIANGLE", R.drawable.triangle);
        drawableBySymbol.put("STAR", R.drawable.star);
    }

    private void bindViews(View view) {
        timerText = view.findViewById(R.id.timerText);
        playerTurnText = view.findViewById(R.id.playerTurnText);
        playerOneScoreText = view.findViewById(R.id.playerOneScoreText);
        playerTwoScoreText = view.findViewById(R.id.playerTwoScoreText);
        checkCombinationButton = view.findViewById(R.id.checkCombinationButton);
        skockoSymbolButton = view.findViewById(R.id.skockoSymbolButton);
        squareSymbolButton = view.findViewById(R.id.squareSymbolButton);
        circleSymbolButton = view.findViewById(R.id.circleSymbolButton);
        heartSymbolButton = view.findViewById(R.id.heartSymbolButton);
        triangleSymbolButton = view.findViewById(R.id.triangleSymbolButton);
        starSymbolButton = view.findViewById(R.id.starSymbolButton);
        bindAttemptSlots(view);
    }

    private void bindAttemptSlots(View view) {
        attemptSlots = new ImageView[][]{
                {view.findViewById(R.id.attempt1Slot1), view.findViewById(R.id.attempt1Slot2), view.findViewById(R.id.attempt1Slot3), view.findViewById(R.id.attempt1Slot4)},
                {view.findViewById(R.id.attempt2Slot1), view.findViewById(R.id.attempt2Slot2), view.findViewById(R.id.attempt2Slot3), view.findViewById(R.id.attempt2Slot4)},
                {view.findViewById(R.id.attempt3Slot1), view.findViewById(R.id.attempt3Slot2), view.findViewById(R.id.attempt3Slot3), view.findViewById(R.id.attempt3Slot4)},
                {view.findViewById(R.id.attempt4Slot1), view.findViewById(R.id.attempt4Slot2), view.findViewById(R.id.attempt4Slot3), view.findViewById(R.id.attempt4Slot4)},
                {view.findViewById(R.id.attempt5Slot1), view.findViewById(R.id.attempt5Slot2), view.findViewById(R.id.attempt5Slot3), view.findViewById(R.id.attempt5Slot4)},
                {view.findViewById(R.id.attempt6Slot1), view.findViewById(R.id.attempt6Slot2), view.findViewById(R.id.attempt6Slot3), view.findViewById(R.id.attempt6Slot4)},
                {view.findViewById(R.id.attempt7Slot1), view.findViewById(R.id.attempt7Slot2), view.findViewById(R.id.attempt7Slot3), view.findViewById(R.id.attempt7Slot4)}
        };

        resultTexts = new TextView[]{
                view.findViewById(R.id.attempt1Result),
                view.findViewById(R.id.attempt2Result),
                view.findViewById(R.id.attempt3Result),
                view.findViewById(R.id.attempt4Result),
                view.findViewById(R.id.attempt5Result),
                view.findViewById(R.id.attempt6Result),
                view.findViewById(R.id.attempt7Result)
        };
    }

    private void setupSymbolButtons() {
        skockoSymbolButton.setOnClickListener(v -> addSymbol("SKOCKO"));
        squareSymbolButton.setOnClickListener(v -> addSymbol("SQUARE"));
        circleSymbolButton.setOnClickListener(v -> addSymbol("CIRCLE"));
        heartSymbolButton.setOnClickListener(v -> addSymbol("HEART"));
        triangleSymbolButton.setOnClickListener(v -> addSymbol("TRIANGLE"));
        starSymbolButton.setOnClickListener(v -> addSymbol("STAR"));
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
            finishGame();
            return;
        }
        ActiveSessionTracker.markActiveSession(sessionId);

        String currentGame = snapshot.getString("currentGame");
        if (currentGame != null && !GAME_TYPE_GUESS_COMBINATION.equals(currentGame)) {
            openStepByStep(snapshot.toJson().toString());
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
        selectedCombination.clear();

        updateScoreText();
        renderTurnText(snapshot.getTurnState(), activeGameState);
        renderAttempts(currentRound, snapshot.getGamePhase());
        renderPhase(snapshot.getGamePhase(), snapshot.getLong("serverNowMs"));
        updateInputAvailability();
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

    private void renderAttempts(JSONObject currentRound, GamePhaseSnapshot gamePhase) {
        clearAttemptGrid();
        JSONArray ownerAttempts = currentRound.optJSONArray("ownerAttempts");
        int ownerAttemptCount = ownerAttempts == null ? 0 : ownerAttempts.length();
        for (int i = 0; i < ownerAttemptCount && i < attemptSlots.length; i++) {
            JSONObject attempt = ownerAttempts.optJSONObject(i);
            renderAttemptRow(i, attempt);
        }

        JSONObject opponentAttempt = currentRound.optJSONObject("opponentAttempt");
        if (opponentAttempt != null) {
            renderAttemptRow(OPPONENT_ATTEMPT_ROW, opponentAttempt);
            return;
        }

        if (gamePhase != null
                && PHASE_OPPONENT_ATTEMPT.equals(gamePhase.getPhaseType())) {
            currentInputRow = OPPONENT_ATTEMPT_ROW;
            renderSelectedCombination(currentInputRow);
        } else if (ownerAttemptCount < attemptSlots.length) {
            currentInputRow = ownerAttemptCount;
            renderSelectedCombination(currentInputRow);
        }
    }

    private void clearAttemptGrid() {
        for (int row = 0; row < attemptSlots.length; row++) {
            for (int column = 0; column < attemptSlots[row].length; column++) {
                attemptSlots[row][column].setImageDrawable(null);
                attemptSlots[row][column].setBackgroundColor(Color.DKGRAY);
            }
            resultTexts[row].setText("");
        }
    }

    private void renderAttemptRow(int rowIndex, JSONObject attempt) {
        if (attempt == null || rowIndex < 0 || rowIndex >= attemptSlots.length) {
            return;
        }
        JSONArray symbols = attempt.optJSONArray("symbols");
        for (int i = 0; symbols != null && i < symbols.length() && i < 4; i++) {
            setSlotSymbol(attemptSlots[rowIndex][i], symbols.optString(i, null));
        }
        showResultDots(
                resultTexts[rowIndex],
                attempt.optInt("exactMatches", 0),
                attempt.optInt("symbolOnlyMatches", 0)
        );
    }

    private void renderSelectedCombination(int rowIndex) {
        if (rowIndex < 0 || rowIndex >= attemptSlots.length) {
            return;
        }
        for (int i = 0; i < selectedCombination.size() && i < 4; i++) {
            setSlotSymbol(attemptSlots[rowIndex][i], selectedCombination.get(i));
        }
    }

    private void setSlotSymbol(ImageView slot, String symbol) {
        Integer drawable = drawableBySymbol.get(symbol);
        if (drawable == null) {
            return;
        }
        slot.setImageResource(drawable);
        slot.setBackgroundColor(Color.rgb(111, 75, 179));
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

    private void addSymbol(String symbol) {
        if (!canAct || requestInProgress || selectedCombination.size() >= 4) {
            return;
        }

        selectedCombination.add(symbol);
        renderSelectedCombination(currentInputRow);
        updateInputAvailability();
    }

    private void submitCombination() {
        if (!canAct || requestInProgress || selectedCombination.size() != 4 || sessionId == null || currentPhaseVersion <= 0L) {
            return;
        }

        requestInProgress = true;
        disableInput();
        try {
            JSONObject data = new JSONObject();
            JSONArray symbols = new JSONArray();
            for (String symbol : selectedCombination) {
                symbols.put(symbol);
            }
            data.put("symbols", symbols);
            webSocketGameClient.sendGameAction(
                    sessionId,
                    ACTION_SUBMIT,
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
        } catch (JSONException e) {
            requestInProgress = false;
            showToast("Slanje kombinacije nije uspelo");
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
        setSymbolButtonsEnabled(enabled && selectedCombination.size() < 4);
        checkCombinationButton.setEnabled(enabled && selectedCombination.size() == 4);
    }

    private void setSymbolButtonsEnabled(boolean enabled) {
        skockoSymbolButton.setEnabled(enabled);
        squareSymbolButton.setEnabled(enabled);
        circleSymbolButton.setEnabled(enabled);
        heartSymbolButton.setEnabled(enabled);
        triangleSymbolButton.setEnabled(enabled);
        starSymbolButton.setEnabled(enabled);
    }

    private void disableInput() {
        setSymbolButtonsEnabled(false);
        checkCombinationButton.setEnabled(false);
    }

    private void showLoadingState() {
        timerText.setText("-");
        playerTurnText.setText("Skočko nije učitan");
        updateScoreText();
        clearAttemptGrid();
        disableInput();
    }

    private void showResultDots(TextView resultText, int exactMatches, int symbolOnlyMatches) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < exactMatches; i++) {
            builder.append("●");
        }
        for (int i = 0; i < symbolOnlyMatches; i++) {
            builder.append("●");
        }

        SpannableString spannableString = new SpannableString(builder.toString());
        int index = 0;
        for (int i = 0; i < exactMatches; i++) {
            spannableString.setSpan(
                    new ForegroundColorSpan(Color.RED),
                    index,
                    index + 1,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            );
            index++;
        }
        for (int i = 0; i < symbolOnlyMatches; i++) {
            spannableString.setSpan(
                    new ForegroundColorSpan(Color.YELLOW),
                    index,
                    index + 1,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            );
            index++;
        }

        resultText.setText(spannableString);
        resultText.setTextSize(24);
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

    private void openStepByStep(String nextSessionJson) {
        if (finishedNavigated) {
            return;
        }
        finishedNavigated = true;
        stopTimer();

        requireActivity()
                .getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragmentContainer, StepByStepFragment.newInstance(nextSessionJson))
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
    public void onDestroyView() {
        super.onDestroyView();
        if (sessionListenerHandle != null) {
            sessionListenerHandle.remove();
            sessionListenerHandle = null;
        }
        stopTimer();
    }
}
