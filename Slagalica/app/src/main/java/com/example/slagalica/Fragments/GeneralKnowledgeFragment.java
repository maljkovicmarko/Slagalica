package com.example.slagalica.Fragments;

import android.graphics.Color;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.fragment.app.Fragment;

import com.example.slagalica.Model.Question;
import com.example.slagalica.R;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class GeneralKnowledgeFragment extends Fragment {

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

    private int currentQuestionIndex;
    private int score;

    private List<Question> questions;
    private CountDownTimer questionTimer;

    public GeneralKnowledgeFragment() {
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        currentQuestionIndex = 0;
        score = 0;
        questions = createDummyQuestions();
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

        answerAButton.setOnClickListener(v -> handleAnswer(1, answerAButton));
        answerBButton.setOnClickListener(v -> handleAnswer(2, answerBButton));
        answerCButton.setOnClickListener(v -> handleAnswer(3, answerCButton));
        answerDButton.setOnClickListener(v -> handleAnswer(4, answerDButton));

        nextQuestionButton.setVisibility(View.GONE);
        nextQuestionButton.setOnClickListener(v -> moveToNextQuestion());

        updateScoreText();
        loadQuestion();

        return view;
    }

    private List<Question> createDummyQuestions() {
        List<Question> questions = new ArrayList<>();

        questions.add(new Question(
                "Koji bend je objavio album 'Abbey Road'?",
                "Queen",
                "The Beatles",
                "Pink Floyd",
                "The Rolling Stones",
                2
        ));

        questions.add(new Question(
                "Koliko igrača jedan fudbalski tim ima na terenu na početku utakmice?",
                "9",
                "10",
                "11",
                "12",
                3
        ));

        questions.add(new Question(
                "Koje godine je završen Drugi svetski rat?",
                "1943",
                "1944",
                "1945",
                "1946",
                3
        ));

        questions.add(new Question(
                "Ko je komponovao delo 'Četiri godišnja doba'?",
                "Mocart",
                "Bah",
                "Betoven",
                "Vivaldi",
                4
        ));

        questions.add(new Question(
                "Ko je bio prvi čovek koji je kročio na Mesec?",
                "Jurij Gagarin",
                "Baz Oldrin",
                "Nil Armstrong",
                "Džon Glen",
                3
        ));

        return questions;
    }

    private void loadQuestion() {
        if (currentQuestionIndex >= questions.size()) {
            finishGame();
            return;
        }

        resetAnswerButtons();

        Question question = questions.get(currentQuestionIndex);

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

        startTimer();
    }

    private void startTimer() {
        stopTimer();

        questionTimer = new CountDownTimer(5000, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                long seconds = millisUntilFinished / 1000 + 1;
                timerText.setText(seconds + "s");
            }

            @Override
            public void onFinish() {
                timerText.setText("0s");
                handleTimeout();
            }
        };

        questionTimer.start();
    }

    private void stopTimer() {
        if (questionTimer != null) {
            questionTimer.cancel();
            questionTimer = null;
        }
    }

    private void handleAnswer(int selectedAnswer, Button selectedButton) {
        stopTimer();
        disableAnswerButtons();

        Question question = questions.get(currentQuestionIndex);
        Button correctButton = getButtonByAnswerNumber(question.getCorrectAnswer());

        if (selectedAnswer == question.getCorrectAnswer()) {
            score += 10;
            selectedButton.setBackgroundColor(CORRECT_COLOR);
        } else {
            score -= 5;
            selectedButton.setBackgroundColor(WRONG_COLOR);
            correctButton.setBackgroundColor(CORRECT_COLOR);
        }

        updateScoreText();

        new Handler().postDelayed(this::moveToNextQuestion, 1000);
    }

    private void handleTimeout() {
        disableAnswerButtons();

        Question question = questions.get(currentQuestionIndex);
        Button correctButton = getButtonByAnswerNumber(question.getCorrectAnswer());
        correctButton.setBackgroundColor(CORRECT_COLOR);

        Toast.makeText(requireContext(), "Vreme je isteklo", Toast.LENGTH_SHORT).show();

        new Handler().postDelayed(this::moveToNextQuestion, 1000);
    }

    private void moveToNextQuestion() {
        currentQuestionIndex++;
        loadQuestion();
    }

    private void finishGame() {
        stopTimer();

        Bundle bundle = new Bundle();
        bundle.putInt("playerOneScore", score);
        bundle.putInt("playerTwoScore", 0);

        ConnectingGameFragment fragment = new ConnectingGameFragment();
        fragment.setArguments(bundle);

        requireActivity()
                .getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragmentContainer, fragment)
                .commit();
    }

    private void updateScoreText() {
        playerOneScoreText.setText("Igrač 1: " + score + " bodova");
        playerTwoScoreText.setText("Igrač 2: 0 bodova");
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
        stopTimer();
    }
}