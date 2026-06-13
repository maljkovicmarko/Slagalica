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

import com.example.slagalica.Model.StepByStepQuestion;
import com.example.slagalica.R;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public class StepByStepFragment extends Fragment {

    private TextView roundText, timerText, playerTurnText;
    private TextView playerOneScoreText, playerTwoScoreText;
    private EditText answerInput;
    private Button submitAnswerButton;

    private final List<TextView> stepViews = new ArrayList<>();
    private final List<StepByStepQuestion> questions = new ArrayList<>();

    private CountDownTimer timer;

    private int playerOneScore = 0;
    private int playerTwoScore = 0;

    private int currentStep = 0;

    private static final int ROUND_TIME = 70000;
    private static final int STEP_TIME = 10000;

    public StepByStepFragment() {
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (getArguments() != null) {
            playerOneScore = getArguments().getInt("playerOneScore", 0);
            playerTwoScore = getArguments().getInt("playerTwoScore", 0);
        }

        loadQuestions();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.fragment_step_by_step, container, false);

        bindViews(view);
        updateScoreText();
        resetStepViews();
        revealStep();

        roundText.setText("Runda: 1/1");
        playerTurnText.setText("Na potezu: Igrač 1");

        submitAnswerButton.setOnClickListener(v -> checkAnswer());

        startTimer();

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

        stepViews.add(view.findViewById(R.id.stepOneText));
        stepViews.add(view.findViewById(R.id.stepTwoText));
        stepViews.add(view.findViewById(R.id.stepThreeText));
        stepViews.add(view.findViewById(R.id.stepFourText));
        stepViews.add(view.findViewById(R.id.stepFiveText));
        stepViews.add(view.findViewById(R.id.stepSixText));
        stepViews.add(view.findViewById(R.id.stepSevenText));
    }

    private void loadQuestions() {
        questions.add(new StepByStepQuestion(
                "atom",
                Arrays.asList(
                        "Ima veze sa materijom",
                        "Ne vidi se golim okom",
                        "Može biti deo molekula",
                        "Ima jezgro",
                        "Sadrži protone",
                        "Sadrži neutrone",
                        "Sadrži elektrone"
                )
        ));
    }

    private void startTimer() {
        stopTimer();

        timer = new CountDownTimer(ROUND_TIME, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                int secondsLeft = (int) millisUntilFinished / 1000;
                timerText.setText(secondsLeft + "s");

                int expectedStep = Math.min(6, (ROUND_TIME - secondsLeft * 1000) / STEP_TIME);

                if (expectedStep > currentStep) {
                    currentStep = expectedStep;
                    revealStep();
                }
            }

            @Override
            public void onFinish() {
                finishGame();
            }
        };

        timer.start();
    }

    private void checkAnswer() {
        String userAnswer = answerInput.getText().toString().trim().toLowerCase(Locale.ROOT);
        String correctAnswer = questions.get(0).getAnswer().toLowerCase(Locale.ROOT);

        if (!userAnswer.equals(correctAnswer)) {
            Toast.makeText(requireContext(), "Netačno!", Toast.LENGTH_SHORT).show();
            return;
        }

        int points = 20 - currentStep * 2;
        playerOneScore += points;

        updateScoreText();
        revealAllStepsGreen();

        Toast.makeText(requireContext(), "Tačno! +" + points + " bodova", Toast.LENGTH_SHORT).show();

        answerInput.setEnabled(false);
        submitAnswerButton.setEnabled(false);

        timerText.postDelayed(this::finishGame, 1500);
    }

    private void revealStep() {
        StepByStepQuestion question = questions.get(0);

        for (int i = 0; i <= currentStep && i < stepViews.size(); i++) {
            stepViews.get(i).setText(question.getSteps().get(i));
            stepViews.get(i).setBackgroundColor(Color.LTGRAY);
            stepViews.get(i).setTextColor(Color.BLACK);
        }
    }

    private void revealAllStepsGreen() {
        StepByStepQuestion question = questions.get(0);

        for (int i = 0; i < stepViews.size(); i++) {
            stepViews.get(i).setText(question.getSteps().get(i));
            stepViews.get(i).setBackgroundColor(Color.rgb(76, 175, 80));
            stepViews.get(i).setTextColor(Color.WHITE);
        }
    }

    private void resetStepViews() {
        for (int i = 0; i < stepViews.size(); i++) {
            stepViews.get(i).setText("Korak " + (i + 1));
            stepViews.get(i).setBackgroundColor(Color.DKGRAY);
            stepViews.get(i).setTextColor(Color.WHITE);
        }
    }

    private void finishGame() {
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

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        stopTimer();
    }
}