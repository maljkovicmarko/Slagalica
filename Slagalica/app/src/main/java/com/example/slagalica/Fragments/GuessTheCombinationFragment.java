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

import com.example.slagalica.Model.GuessCombinationSymbol;
import com.example.slagalica.R;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public class GuessTheCombinationFragment extends Fragment {

    private TextView timerText;
    private TextView playerOneScoreText;
    private TextView playerTwoScoreText;

    private Button checkCombinationButton;

    private ImageButton skockoSymbolButton;
    private ImageButton squareSymbolButton;
    private ImageButton circleSymbolButton;
    private ImageButton heartSymbolButton;
    private ImageButton triangleSymbolButton;
    private ImageButton starSymbolButton;

    private int playerOneScore;
    private int playerTwoScore;

    private int currentAttempt = 0;
    private int currentSlot = 0;

    private CountDownTimer timer;

    private final List<GuessCombinationSymbol> selectedCombination = new ArrayList<>();

    private ImageView[][] attemptSlots;
    private TextView[] resultTexts;

    private List<GuessCombinationSymbol> targetCombination;

    public GuessTheCombinationFragment() {
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (getArguments() != null) {
            playerOneScore = getArguments().getInt("playerOneScore", 0);
            playerTwoScore = getArguments().getInt("playerTwoScore", 0);
        }

        targetCombination = Arrays.asList(
                new GuessCombinationSymbol("SKOCKO", R.drawable.skocko),
                new GuessCombinationSymbol("STAR", R.drawable.star),
                new GuessCombinationSymbol("HEART", R.drawable.heart),
                new GuessCombinationSymbol("CIRCLE", R.drawable.circle)
        );
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.fragment_guess_the_combination, container, false);

        timerText = view.findViewById(R.id.timerText);
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
        setupSymbolButtons();
        updateScoreText();

        checkCombinationButton.setOnClickListener(v -> checkCombination());

        startTimer();

        return view;
    }

    private void bindAttemptSlots(View view) {
        attemptSlots = new ImageView[][]{
                {
                        view.findViewById(R.id.attempt1Slot1),
                        view.findViewById(R.id.attempt1Slot2),
                        view.findViewById(R.id.attempt1Slot3),
                        view.findViewById(R.id.attempt1Slot4)
                },
                {
                        view.findViewById(R.id.attempt2Slot1),
                        view.findViewById(R.id.attempt2Slot2),
                        view.findViewById(R.id.attempt2Slot3),
                        view.findViewById(R.id.attempt2Slot4)
                },
                {
                        view.findViewById(R.id.attempt3Slot1),
                        view.findViewById(R.id.attempt3Slot2),
                        view.findViewById(R.id.attempt3Slot3),
                        view.findViewById(R.id.attempt3Slot4)
                },
                {
                        view.findViewById(R.id.attempt4Slot1),
                        view.findViewById(R.id.attempt4Slot2),
                        view.findViewById(R.id.attempt4Slot3),
                        view.findViewById(R.id.attempt4Slot4)
                },
                {
                        view.findViewById(R.id.attempt5Slot1),
                        view.findViewById(R.id.attempt5Slot2),
                        view.findViewById(R.id.attempt5Slot3),
                        view.findViewById(R.id.attempt5Slot4)
                },
                {
                        view.findViewById(R.id.attempt6Slot1),
                        view.findViewById(R.id.attempt6Slot2),
                        view.findViewById(R.id.attempt6Slot3),
                        view.findViewById(R.id.attempt6Slot4)
                }
        };

        resultTexts = new TextView[]{
                view.findViewById(R.id.attempt1Result),
                view.findViewById(R.id.attempt2Result),
                view.findViewById(R.id.attempt3Result),
                view.findViewById(R.id.attempt4Result),
                view.findViewById(R.id.attempt5Result),
                view.findViewById(R.id.attempt6Result)
        };
    }

    private void setupSymbolButtons() {
        skockoSymbolButton.setOnClickListener(v ->
                addSymbol(new GuessCombinationSymbol("SKOCKO", R.drawable.skocko)));

        squareSymbolButton.setOnClickListener(v ->
                addSymbol(new GuessCombinationSymbol("SQUARE", R.drawable.square)));

        circleSymbolButton.setOnClickListener(v ->
                addSymbol(new GuessCombinationSymbol("CIRCLE", R.drawable.circle)));

        heartSymbolButton.setOnClickListener(v ->
                addSymbol(new GuessCombinationSymbol("HEART", R.drawable.heart)));

        triangleSymbolButton.setOnClickListener(v ->
                addSymbol(new GuessCombinationSymbol("TRIANGLE", R.drawable.triangle)));

        starSymbolButton.setOnClickListener(v ->
                addSymbol(new GuessCombinationSymbol("STAR", R.drawable.star)));
    }

    private void addSymbol(GuessCombinationSymbol symbol) {
        if (currentAttempt >= 6 || currentSlot >= 4) {
            return;
        }

        selectedCombination.add(symbol);

        ImageView slot = attemptSlots[currentAttempt][currentSlot];
        slot.setImageResource(symbol.getDrawableResId());
        slot.setBackgroundColor(Color.rgb(111, 75, 179));

        currentSlot++;
    }

    private void checkCombination() {
        if (selectedCombination.size() != 4) {
            Toast.makeText(requireContext(), "Unesi 4 znaka", Toast.LENGTH_SHORT).show();
            return;
        }

        int exactMatches = countExactMatches();
        int symbolOnlyMatches = countAllSymbolMatches() - exactMatches;

        showResultDots(resultTexts[currentAttempt], exactMatches, symbolOnlyMatches);

        if (exactMatches == 4) {
            int points = calculatePointsForAttempt();
            playerOneScore += points;
            updateScoreText();

            Toast.makeText(requireContext(), "Tačna kombinacija! +" + points, Toast.LENGTH_SHORT).show();
            finishGame();
            return;
        }

        currentAttempt++;
        currentSlot = 0;
        selectedCombination.clear();

        if (currentAttempt >= 6) {
            Toast.makeText(requireContext(), "Nisi pogodio kombinaciju", Toast.LENGTH_SHORT).show();
            finishGame();
        }
    }

    private int countExactMatches() {
        int count = 0;

        for (int i = 0; i < 4; i++) {
            if (selectedCombination.get(i).getName().equals(targetCombination.get(i).getName())) {
                count++;
            }
        }

        return count;
    }

    private int countAllSymbolMatches() {
        List<String> targetNames = new ArrayList<>();
        List<String> selectedNames = new ArrayList<>();

        for (GuessCombinationSymbol symbol : targetCombination) {
            targetNames.add(symbol.getName());
        }

        for (GuessCombinationSymbol symbol : selectedCombination) {
            selectedNames.add(symbol.getName());
        }

        int count = 0;

        for (String selected : selectedNames) {
            if (targetNames.contains(selected)) {
                count++;
                targetNames.remove(selected);
            }
        }

        return count;
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

    private int calculatePointsForAttempt() {
        if (currentAttempt == 0 || currentAttempt == 1) {
            return 20;
        }

        if (currentAttempt == 2 || currentAttempt == 3) {
            return 15;
        }

        return 10;
    }

    private void startTimer() {
        stopTimer();

        timer = new CountDownTimer(30000, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                long seconds = millisUntilFinished / 1000 + 1;
                timerText.setText(String.format(Locale.getDefault(), "%ds", seconds));
            }

            @Override
            public void onFinish() {
                timerText.setText("0s");
                finishGame();
            }
        };

        timer.start();
    }

    private void stopTimer() {
        if (timer != null) {
            timer.cancel();
            timer = null;
        }
    }

    private void finishGame() {
        stopTimer();

        Bundle bundle = new Bundle();
        bundle.putInt("playerOneScore", playerOneScore);
        bundle.putInt("playerTwoScore", playerTwoScore);

        StepByStepFragment fragment = new StepByStepFragment();
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

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        stopTimer();
    }
}