package com.example.slagalica.Fragments;

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

import com.example.slagalica.Model.Question;
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

public class GeneralKnowledgeFragment extends Fragment {
    private static final String ARG_SESSION_JSON = "sessionJson";
    private static final String ACTION_ANSWER = "general_knowledge_answer";
    private static final String GAME_TYPE_GENERAL_KNOWLEDGE = "general_knowledge";
    private static final String PHASE_QUESTION_OPEN = "general_knowledge_question_open";
    private static final String PHASE_ANSWER_REVEAL = "general_knowledge_answer_reveal";

    private TextView timerText;
    private TextView questionCounterText;
    private TextView questionText;
    private TextView playerOneScoreText;
    private TextView playerTwoScoreText;

    private Button answerAButton;
    private Button answerBButton;
    private Button answerCButton;
    private Button answerDButton;
    private Button nextQuestionButton;

    private final int CORRECT_COLOR = Color.rgb(76, 175, 80);
    private final int WRONG_COLOR = Color.rgb(244, 67, 54);

    private final int DEFAULT_COLOR = Color.rgb(111, 75, 179);

    private List<Question> questions;
    private CountDownTimer questionTimer;
    private String sessionJson;
    private String sessionId;
    private String currentUid;
    private long currentPhaseVersion;
    private long renderedPhaseVersion;
    private int playerOneScore;
    private int playerTwoScore;
    private boolean finishedNavigated;
    private WebSocketGameClient webSocketGameClient;
    private WebSocketGameClient.ListenerHandle sessionListenerHandle;

    public GeneralKnowledgeFragment() {
    }

    public static GeneralKnowledgeFragment newInstance(String sessionJson) {
        GeneralKnowledgeFragment fragment = new GeneralKnowledgeFragment();
        Bundle args = new Bundle();
        args.putString(ARG_SESSION_JSON, sessionJson);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        questions = new ArrayList<>();
        webSocketGameClient = WebSocketGameClient.getInstance();
        FirebaseUser firebaseUser = FirebaseAuth.getInstance().getCurrentUser();
        currentUid = firebaseUser == null ? null : firebaseUser.getUid();
        if (getArguments() != null) {
            sessionJson = getArguments().getString(ARG_SESSION_JSON);
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.fragment_general_knowledge, container, false);

        timerText = view.findViewById(R.id.timerText);
        questionCounterText = view.findViewById(R.id.questionCounterText);
        questionText = view.findViewById(R.id.questionText);

        answerAButton = view.findViewById(R.id.answerAButton);
        answerBButton = view.findViewById(R.id.answerBButton);
        answerCButton = view.findViewById(R.id.answerCButton);
        answerDButton = view.findViewById(R.id.answerDButton);

        nextQuestionButton = view.findViewById(R.id.nextQuestionButton);

        playerOneScoreText = view.findViewById(R.id.playerOneScoreText);
        playerTwoScoreText = view.findViewById(R.id.playerTwoScoreText);

        answerAButton.setOnClickListener(v -> sendAnswer(1));
        answerBButton.setOnClickListener(v -> sendAnswer(2));
        answerCButton.setOnClickListener(v -> sendAnswer(3));
        answerDButton.setOnClickListener(v -> sendAnswer(4));

        nextQuestionButton.setVisibility(View.GONE);

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
        if (!isAdded() || questionText == null || snapshot == null) {
            return;
        }

        sessionId = snapshot.getString("sessionId");
        if (isTerminalSession(snapshot)) {
            finishMatch();
            return;
        }
        ActiveSessionTracker.markActiveSession(sessionId);

        String currentGame = snapshot.getString("currentGame");
        if (currentGame != null && !GAME_TYPE_GENERAL_KNOWLEDGE.equals(currentGame)) {
            openConnectingGame(snapshot.toJson().toString());
            return;
        }

        JSONObject session = snapshot.toJson();
        JSONObject activeGameState = session.optJSONObject("activeGameState");
        if (activeGameState == null) {
            showLoadingState();
            return;
        }

        parseQuestions(activeGameState, session);

        int currentQuestionIndex = activeGameState.optInt("currentQuestionIndex", 0);
        playerOneScore = activeGameState.optInt("player1Score", 0);
        playerTwoScore = activeGameState.optInt("player2Score", 0);
        boolean answerRevealed = activeGameState.optBoolean("answerRevealed", false);
        boolean finished = activeGameState.optBoolean("finished", false);
        Integer selectedAnswer = activeGameState.isNull("selectedAnswer")
                ? null
                : activeGameState.optInt("selectedAnswer");

        updateScoreText();

        if (finished) {
            finishGame();
            return;
        }

        if (questions.isEmpty() || currentQuestionIndex >= questions.size()) {
            showLoadingState();
            return;
        }

        Question question = questions.get(currentQuestionIndex);
        renderQuestion(currentQuestionIndex, question);
        resetAnswerButtons();

        if (answerRevealed) {
            renderRevealedAnswer(question, selectedAnswer);
            disableAnswerButtons();
        } else if (canCurrentUserAnswer(snapshot)) {
            enableAnswerButtons();
        } else {
            disableAnswerButtons();
        }

        renderPhase(snapshot.getGamePhase(), snapshot.getLong("serverNowMs"));
    }

    private void parseQuestions(JSONObject activeGameState, JSONObject session) {
        if (!questions.isEmpty()) {
            return;
        }

        JSONArray questionsJson = activeGameState.optJSONArray("questions");
        if (questionsJson == null) {
            questionsJson = session.optJSONArray("generalKnowledgeQuestions");
        }
        if (questionsJson == null) {
            return;
        }

        for (int i = 0; i < questionsJson.length(); i++) {
            JSONObject questionJson = questionsJson.optJSONObject(i);
            if (questionJson == null) {
                continue;
            }

            questions.add(new Question(
                    questionJson.optString("question", ""),
                    questionJson.optString("answer1", ""),
                    questionJson.optString("answer2", ""),
                    questionJson.optString("answer3", ""),
                    questionJson.optString("answer4", ""),
                    questionJson.optInt("correctAnswer", 0)
            ));
        }
    }

    private boolean canCurrentUserAnswer(SessionSnapshot snapshot) {
        GamePhaseSnapshot gamePhase = snapshot.getGamePhase();
        if (gamePhase == null || !PHASE_QUESTION_OPEN.equals(gamePhase.getPhaseType())) {
            return false;
        }

        TurnSnapshot turnState = snapshot.getTurnState();
        return turnState != null && turnState.canAct(currentUid);
    }

    private void renderQuestion(int currentQuestionIndex, Question question) {
        questionCounterText.setText(String.format(
                Locale.getDefault(),
                "Pitanje %d / %d",
                currentQuestionIndex + 1,
                questions.size()
        ));

        questionText.setText(question.getQuestion());
        answerAButton.setText(question.getAnswer1());
        answerBButton.setText(question.getAnswer2());
        answerCButton.setText(question.getAnswer3());
        answerDButton.setText(question.getAnswer4());
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

        long deviceNowMs = System.currentTimeMillis();
        long referenceNowMs = serverNowMs == null ? deviceNowMs : serverNowMs;
        long remainingMs = gamePhase.getRemainingMs(referenceNowMs);
        if (PHASE_ANSWER_REVEAL.equals(gamePhase.getPhaseType())) {
            timerText.setText("Prikaz odgovora");
            return;
        }

        questionTimer = new CountDownTimer(Math.max(remainingMs, 1L), 250L) {
            @Override
            public void onTick(long millisUntilFinished) {
                long seconds = (long) Math.ceil(millisUntilFinished / 1000.0);
                timerText.setText(seconds + "s");
            }

            @Override
            public void onFinish() {
                timerText.setText("0s");
                disableAnswerButtons();
            }
        };

        questionTimer.start();
    }

    private void sendAnswer(int selectedAnswer) {
        if (sessionId == null || sessionId.isBlank() || currentPhaseVersion <= 0L) {
            return;
        }

        disableAnswerButtons();
        try {
            JSONObject data = new JSONObject();
            data.put("answer", selectedAnswer);
            webSocketGameClient.sendGameAction(
                    sessionId,
                    ACTION_ANSWER,
                    currentPhaseVersion,
                    data,
                    new WebSocketGameClient.OnRequestResult() {
                        @Override
                        public void onSuccess(JSONObject data) {
                        }

                        @Override
                        public void onFailure(String errorMessage) {
                            if (isAdded()) {
                                Toast.makeText(requireContext(), errorMessage, Toast.LENGTH_SHORT).show();
                            }
                        }
                    }
            );
        } catch (JSONException e) {
            if (isAdded()) {
                Toast.makeText(requireContext(), "Slanje odgovora nije uspelo", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void showLoadingState() {
        timerText.setText("-");
        questionCounterText.setText("Pitanje 0 / 0");
        questionText.setText("Pitanja nisu učitana");
        answerAButton.setText("-");
        answerBButton.setText("-");
        answerCButton.setText("-");
        answerDButton.setText("-");
        disableAnswerButtons();
    }

    private void stopTimer() {
        if (questionTimer != null) {
            questionTimer.cancel();
            questionTimer = null;
        }
    }

    private void renderRevealedAnswer(Question question, Integer selectedAnswer) {
        Button correctButton = getButtonByAnswerNumber(question.getCorrectAnswer());
        correctButton.setBackgroundColor(CORRECT_COLOR);
        if (selectedAnswer != null && selectedAnswer != question.getCorrectAnswer()) {
            Button selectedButton = getButtonByAnswerNumber(selectedAnswer);
            selectedButton.setBackgroundColor(WRONG_COLOR);
            correctButton.setBackgroundColor(CORRECT_COLOR);
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

        ConnectingGameFragment fragment = new ConnectingGameFragment();
        fragment.setArguments(bundle);

        requireActivity()
                .getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragmentContainer, fragment)
                .commit();
    }

    private void openConnectingGame(String nextSessionJson) {
        if (finishedNavigated) {
            return;
        }
        finishedNavigated = true;
        stopTimer();

        requireActivity()
                .getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragmentContainer, ConnectingGameFragment.newInstance(nextSessionJson))
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

    private void resetAnswerButtons() {
        answerAButton.setBackgroundColor(DEFAULT_COLOR);
        answerBButton.setBackgroundColor(DEFAULT_COLOR);
        answerCButton.setBackgroundColor(DEFAULT_COLOR);
        answerDButton.setBackgroundColor(DEFAULT_COLOR);

        answerAButton.setEnabled(true);
        answerBButton.setEnabled(true);
        answerCButton.setEnabled(true);
        answerDButton.setEnabled(true);
    }

    private void enableAnswerButtons() {
        answerAButton.setEnabled(true);
        answerBButton.setEnabled(true);
        answerCButton.setEnabled(true);
        answerDButton.setEnabled(true);
    }

    private void disableAnswerButtons() {
        answerAButton.setEnabled(false);
        answerBButton.setEnabled(false);
        answerCButton.setEnabled(false);
        answerDButton.setEnabled(false);
    }

    private Button getButtonByAnswerNumber(int answerNumber) {
        switch (answerNumber) {
            case 1:
                return answerAButton;
            case 2:
                return answerBButton;
            case 3:
                return answerCButton;
            case 4:
                return answerDButton;
            default:
                return answerAButton;
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
