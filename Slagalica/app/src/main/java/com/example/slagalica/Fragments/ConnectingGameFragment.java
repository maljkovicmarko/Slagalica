package com.example.slagalica.Fragments;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.fragment.app.Fragment;

import com.example.slagalica.Model.ConnectingPair;
import com.example.slagalica.R;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ConnectingGameFragment extends Fragment {

    private TextView timerText;
    private TextView criterionText;
    private TextView playerOneScoreText;
    private TextView playerTwoScoreText;

    private Button leftOneButton;
    private Button leftTwoButton;
    private Button leftThreeButton;
    private Button leftFourButton;
    private Button leftFiveButton;

    private Button rightOneButton;
    private Button rightTwoButton;
    private Button rightThreeButton;
    private Button rightFourButton;
    private Button rightFiveButton;

    private Button confirmConnectionButton;

    private final int DEFAULT_COLOR = Color.rgb(111, 75, 179);
    private final int SELECTED_COLOR = Color.rgb(90, 65, 140);
    private final int CORRECT_COLOR = Color.rgb(76, 175, 80);
    private final int WRONG_COLOR = Color.rgb(244, 67, 54);

    private int playerOneScore;
    private int playerTwoScore;
    private int confirmedConnections;

    private Button selectedLeftButton;
    private Button selectedRightButton;

    private CountDownTimer roundTimer;

    private List<ConnectingPair> pairs;
    private List<Button> leftButtons;
    private List<Button> rightButtons;

    public ConnectingGameFragment() {
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        playerOneScore = 0;
        playerTwoScore = 0;

        if (getArguments() != null) {
            playerOneScore = getArguments().getInt("playerOneScore", 0);
            playerTwoScore = getArguments().getInt("playerTwoScore", 0);
        }

        pairs = createDummyPairs();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.fragment_connecting_game, container, false);

        timerText = view.findViewById(R.id.timerText);
        criterionText = view.findViewById(R.id.criterionText);
        playerOneScoreText = view.findViewById(R.id.playerOneScoreText);
        playerTwoScoreText = view.findViewById(R.id.playerTwoScoreText);

        leftOneButton = view.findViewById(R.id.leftOneButton);
        leftTwoButton = view.findViewById(R.id.leftTwoButton);
        leftThreeButton = view.findViewById(R.id.leftThreeButton);
        leftFourButton = view.findViewById(R.id.leftFourButton);
        leftFiveButton = view.findViewById(R.id.leftFiveButton);

        rightOneButton = view.findViewById(R.id.rightOneButton);
        rightTwoButton = view.findViewById(R.id.rightTwoButton);
        rightThreeButton = view.findViewById(R.id.rightThreeButton);
        rightFourButton = view.findViewById(R.id.rightFourButton);
        rightFiveButton = view.findViewById(R.id.rightFiveButton);

        confirmConnectionButton = view.findViewById(R.id.confirmConnectionButton);

        leftButtons = new ArrayList<>();
        Collections.addAll(
                leftButtons,
                leftOneButton,
                leftTwoButton,
                leftThreeButton,
                leftFourButton,
                leftFiveButton
        );

        rightButtons = new ArrayList<>();
        Collections.addAll(
                rightButtons,
                rightOneButton,
                rightTwoButton,
                rightThreeButton,
                rightFourButton,
                rightFiveButton
        );

        setupGame();

        confirmConnectionButton.setOnClickListener(v -> confirmConnection());

        return view;
    }

    private void setupGame() {
        confirmedConnections = 0;
        selectedLeftButton = null;
        selectedRightButton = null;

        criterionText.setText("Poveži naučnika sa naučnom oblašću po kojoj je poznat.");

        setupButtons();
        updateScoreText();
        startTimer();
    }

    private void setupButtons() {
        List<String> rightValues = new ArrayList<>();

        for (int i = 0; i < pairs.size(); i++) {
            ConnectingPair pair = pairs.get(i);

            Button leftButton = leftButtons.get(i);
            leftButton.setText(pair.getLeft());
            leftButton.setEnabled(true);
            setButtonColor(leftButton, DEFAULT_COLOR);

            rightValues.add(pair.getRight());
        }

        Collections.shuffle(rightValues);

        for (int i = 0; i < rightButtons.size(); i++) {
            Button rightButton = rightButtons.get(i);
            rightButton.setText(rightValues.get(i));
            rightButton.setEnabled(true);
            setButtonColor(rightButton, DEFAULT_COLOR);
        }

        for (Button leftButton : leftButtons) {
            leftButton.setOnClickListener(v -> selectLeftButton((Button) v));
        }

        for (Button rightButton : rightButtons) {
            rightButton.setOnClickListener(v -> selectRightButton((Button) v));
        }
    }

    private List<ConnectingPair> createDummyPairs() {
        List<ConnectingPair> pairs = new ArrayList<>();

        pairs.add(new ConnectingPair("Nikola Tesla", "Elektrotehnika"));
        pairs.add(new ConnectingPair("Albert Ajnštajn", "Fizika"));
        pairs.add(new ConnectingPair("Čarls Darvin", "Biologija"));
        pairs.add(new ConnectingPair("Dmitrij Mendeljejev", "Hemija"));
        pairs.add(new ConnectingPair("Pitagora", "Matematika"));

        return pairs;
    }

    private void selectLeftButton(Button button) {
        if (selectedLeftButton != null && selectedLeftButton.isEnabled()) {
            setButtonColor(selectedLeftButton, DEFAULT_COLOR);
        }

        selectedLeftButton = button;
        setButtonColor(selectedLeftButton, SELECTED_COLOR);
    }

    private void selectRightButton(Button button) {
        if (selectedRightButton != null && selectedRightButton.isEnabled()) {
            setButtonColor(selectedRightButton, DEFAULT_COLOR);
        }

        selectedRightButton = button;
        setButtonColor(selectedRightButton, SELECTED_COLOR);
    }

    private void confirmConnection() {
        if (selectedLeftButton == null || selectedRightButton == null) {
            return;
        }

        String left = selectedLeftButton.getText().toString();
        String right = selectedRightButton.getText().toString();

        if (isCorrectPair(left, right)) {
            handleCorrectConnection();
        } else {
            handleWrongConnection();
        }
    }

    private void handleCorrectConnection() {
        playerOneScore += 2;
        confirmedConnections++;

        setButtonColor(selectedLeftButton, CORRECT_COLOR);
        setButtonColor(selectedRightButton, CORRECT_COLOR);

        selectedLeftButton.setEnabled(false);
        selectedRightButton.setEnabled(false);

        selectedLeftButton = null;
        selectedRightButton = null;

        updateScoreText();

        if (confirmedConnections == pairs.size()) {
            new Handler().postDelayed(this::finishGame, 800);
        }
    }

    private void handleWrongConnection() {
        Button missedLeftButton = selectedLeftButton;
        Button wrongRightButton = selectedRightButton;

        setButtonColor(missedLeftButton, WRONG_COLOR);
        setButtonColor(wrongRightButton, WRONG_COLOR);

        missedLeftButton.setEnabled(false);
        confirmedConnections++;

        selectedLeftButton = null;
        selectedRightButton = null;

        new Handler().postDelayed(() -> {
            if (wrongRightButton.isEnabled()) {
                setButtonColor(wrongRightButton, DEFAULT_COLOR);
            }

            if (confirmedConnections == pairs.size()) {
                finishGame();
            }
        }, 800);
    }

    private boolean isCorrectPair(String left, String right) {
        for (ConnectingPair pair : pairs) {
            if (pair.getLeft().equals(left) && pair.getRight().equals(right)) {
                return true;
            }
        }

        return false;
    }

    private void startTimer() {
        stopTimer();

        roundTimer = new CountDownTimer(30000, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                long seconds = millisUntilFinished / 1000 + 1;
                timerText.setText(seconds + "s");
            }

            @Override
            public void onFinish() {
                timerText.setText("0s");
                finishGame();
            }
        };

        roundTimer.start();
    }

    private void stopTimer() {
        if (roundTimer != null) {
            roundTimer.cancel();
            roundTimer = null;
        }
    }

    private void finishGame() {
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
        stopTimer();
    }
}