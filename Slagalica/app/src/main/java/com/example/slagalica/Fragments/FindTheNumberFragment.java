package com.example.slagalica.Fragments;

import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.fragment.app.Fragment;

import com.example.slagalica.Model.FindTheNumberRound;
import com.example.slagalica.R;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Random;

public class FindTheNumberFragment extends Fragment {

    private TextView timerText, roundText, playerTurnText, targetNumberText;
    private TextView playerOneScoreText, playerTwoScoreText;
    private EditText expressionInput;

    private Button stopTargetButton, stopNumbersButton, submitExpressionButton, clearButton;
    private Button openParenthesisButton, closeParenthesisButton, plusButton, minusButton, multiplyButton, divideButton;

    private final List<Button> numberButtons = new ArrayList<>();

    private int playerOneScore = 0;
    private int playerTwoScore = 0;

    private FindTheNumberRound round;
    private CountDownTimer roundTimer;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Random random = new Random();

    private boolean targetStopped = false;
    private boolean numbersStopped = false;

    public FindTheNumberFragment() {
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (getArguments() != null) {
            playerOneScore = getArguments().getInt("playerOneScore", 0);
            playerTwoScore = getArguments().getInt("playerTwoScore", 0);
        }

        round = generateRound();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.fragment_find_the_number, container, false);

        bindViews(view);
        setupButtons();
        updateScoreText();

        roundText.setText("Runda: 1/1");
        playerTurnText.setText("Na potezu: Igrač 1");

        targetNumberText.setText("?");
        setNumbersVisible(false);

        handler.postDelayed(() -> {
            if (!targetStopped) {
                showTargetNumber();
            }
        }, 5000);

        handler.postDelayed(() -> {
            if (!numbersStopped) {
                showNumbers();
                startRoundTimer();
            }
        }, 10000);

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

        numberButtons.add(view.findViewById(R.id.numberOneButton));
        numberButtons.add(view.findViewById(R.id.numberTwoButton));
        numberButtons.add(view.findViewById(R.id.numberThreeButton));
        numberButtons.add(view.findViewById(R.id.numberFourButton));
        numberButtons.add(view.findViewById(R.id.numberFiveButton));
        numberButtons.add(view.findViewById(R.id.numberSixButton));
    }

    private void setupButtons() {
        stopTargetButton.setOnClickListener(v -> showTargetNumber());

        stopNumbersButton.setOnClickListener(v -> {
            showNumbers();
            startRoundTimer();
        });

        submitExpressionButton.setOnClickListener(v -> checkExpression());

        clearButton.setOnClickListener(v -> expressionInput.setText(""));

        openParenthesisButton.setOnClickListener(v -> appendToExpression("("));
        closeParenthesisButton.setOnClickListener(v -> appendToExpression(")"));
        plusButton.setOnClickListener(v -> appendToExpression("+"));
        minusButton.setOnClickListener(v -> appendToExpression("-"));
        multiplyButton.setOnClickListener(v -> appendToExpression("*"));
        divideButton.setOnClickListener(v -> appendToExpression("/"));
    }

    private FindTheNumberRound generateRound() {
        int target = random.nextInt(900) + 100;

        List<Integer> numbers = new ArrayList<>();

        for (int i = 0; i < 4; i++) {
            numbers.add(random.nextInt(9) + 1);
        }

        List<Integer> mediumNumbers = Arrays.asList(10, 15, 20);
        List<Integer> bigNumbers = Arrays.asList(25, 50, 75, 100);

        numbers.add(mediumNumbers.get(random.nextInt(mediumNumbers.size())));
        numbers.add(bigNumbers.get(random.nextInt(bigNumbers.size())));

        return new FindTheNumberRound(target, numbers);
    }

    private void showTargetNumber() {
        targetStopped = true;
        targetNumberText.setText(String.valueOf(round.getTargetNumber()));
        stopTargetButton.setEnabled(false);
    }

    private void showNumbers() {
        numbersStopped = true;
        stopNumbersButton.setEnabled(false);

        List<Integer> numbers = round.getNumbers();

        for (int i = 0; i < numberButtons.size(); i++) {
            Button button = numberButtons.get(i);
            String value = String.valueOf(numbers.get(i));

            button.setText(value);
            button.setEnabled(true);
            button.setOnClickListener(v -> appendToExpression(value));
        }
    }

    private void setNumbersVisible(boolean enabled) {
        for (Button button : numberButtons) {
            button.setEnabled(enabled);
            button.setText("?");
        }
    }

    private void appendToExpression(String value) {
        expressionInput.append(value);
    }

    private void startRoundTimer() {
        stopTimer();

        roundTimer = new CountDownTimer(60000, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                timerText.setText(String.format(Locale.getDefault(), "%ds", millisUntilFinished / 1000));
            }

            @Override
            public void onFinish() {
                timerText.setText("0s");
                finishGame();
            }
        };

        roundTimer.start();
    }

    private void checkExpression() {
        String expression = expressionInput.getText().toString().trim();

        if (expression.isEmpty()) {
            Toast.makeText(requireContext(), "Unesi izraz", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            double result = new ExpressionParser(expression).parse();

            if (Math.abs(result - round.getTargetNumber()) < 0.0001) {
                playerOneScore += 10;
                updateScoreText();
                Toast.makeText(requireContext(), "Tačno! +10 bodova", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(requireContext(), "Rezultat je: " + result, Toast.LENGTH_SHORT).show();
            }

            finishGame();

        } catch (Exception e) {
            Toast.makeText(requireContext(), "Neispravan izraz", Toast.LENGTH_SHORT).show();
        }
    }

    private void finishGame() {
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

    private void updateScoreText() {
        playerOneScoreText.setText("Igrač 1: " + playerOneScore + " bodova");
        playerTwoScoreText.setText("Igrač 2: " + playerTwoScore + " bodova");
    }

    private void stopTimer() {
        if (roundTimer != null) {
            roundTimer.cancel();
            roundTimer = null;
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        stopTimer();
        handler.removeCallbacksAndMessages(null);
    }

    private static class ExpressionParser {
        private final String input;
        private int pos = -1;
        private int ch;

        ExpressionParser(String input) {
            this.input = input;
        }

        double parse() {
            nextChar();
            double x = parseExpression();

            if (pos < input.length()) {
                throw new RuntimeException("Unexpected character");
            }

            return x;
        }

        private void nextChar() {
            ch = (++pos < input.length()) ? input.charAt(pos) : -1;
        }

        private boolean eat(int charToEat) {
            while (ch == ' ') {
                nextChar();
            }

            if (ch == charToEat) {
                nextChar();
                return true;
            }

            return false;
        }

        private double parseExpression() {
            double x = parseTerm();

            while (true) {
                if (eat('+')) {
                    x += parseTerm();
                } else if (eat('-')) {
                    x -= parseTerm();
                } else {
                    return x;
                }
            }
        }

        private double parseTerm() {
            double x = parseFactor();

            while (true) {
                if (eat('*')) {
                    x *= parseFactor();
                } else if (eat('/')) {
                    x /= parseFactor();
                } else {
                    return x;
                }
            }
        }

        private double parseFactor() {
            if (eat('+')) return parseFactor();
            if (eat('-')) return -parseFactor();

            double x;
            int startPos = this.pos;

            if (eat('(')) {
                x = parseExpression();

                if (!eat(')')) {
                    throw new RuntimeException("Missing parenthesis");
                }

            } else if ((ch >= '0' && ch <= '9')) {
                while (ch >= '0' && ch <= '9') {
                    nextChar();
                }

                x = Double.parseDouble(input.substring(startPos, this.pos));

            } else {
                throw new RuntimeException("Unexpected character");
            }

            return x;
        }
    }
}