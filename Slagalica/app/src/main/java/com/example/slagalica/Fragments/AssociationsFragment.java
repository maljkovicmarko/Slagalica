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

import com.example.slagalica.Model.Association;
import com.example.slagalica.Model.AssociationColumn;
import com.example.slagalica.R;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class AssociationsFragment extends Fragment {

    private TextView timerText;
    private TextView playerOneScoreText;
    private TextView playerTwoScoreText;

    private EditText columnAInput;
    private EditText columnBInput;
    private EditText columnCInput;
    private EditText columnDInput;
    private EditText finalSolutionInput;

    private Button guessFinalSolutionButton;

    private Button a1Button, a2Button, a3Button, a4Button, columnASolutionButton;
    private Button b1Button, b2Button, b3Button, b4Button, columnBSolutionButton;
    private Button c1Button, c2Button, c3Button, c4Button, columnCSolutionButton;
    private Button d1Button, d2Button, d3Button, d4Button, columnDSolutionButton;

    private final int DEFAULT_COLOR = Color.rgb(111, 75, 179);
    private final int REVEALED_COLOR = Color.rgb(180, 180, 180);
    private final int CORRECT_COLOR = Color.rgb(76, 175, 80);

    private int playerOneScore;
    private int playerTwoScore;

    private CountDownTimer timer;
    private Association association;

    private final Map<Button, String> hiddenValues = new HashMap<>();
    private final Map<String, Integer> openedFieldsByColumn = new HashMap<>();

    public AssociationsFragment() {
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

        association = createDummyAssociation();

        openedFieldsByColumn.put("A", 0);
        openedFieldsByColumn.put("B", 0);
        openedFieldsByColumn.put("C", 0);
        openedFieldsByColumn.put("D", 0);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.fragment_associations, container, false);

        bindViews(view);
        setupAssociationData();
        setupHiddenButtons();
        setupSolutionButtons();

        updateScoreText();
        startTimer();

        guessFinalSolutionButton.setOnClickListener(v -> guessFinalSolution());

        return view;
    }

    private Association createDummyAssociation() {
        return new Association(
                new AssociationColumn(
                        Arrays.asList("Tesla", "Edison", "Struja", "Napon"),
                        "Elektrotehnika"
                ),
                new AssociationColumn(
                        Arrays.asList("Ajnštajn", "Njutn", "Kvant", "Sila"),
                        "Fizika"
                ),
                new AssociationColumn(
                        Arrays.asList("Darvin", "Vrsta", "Evolucija", "Genetika"),
                        "Biologija"
                ),
                new AssociationColumn(
                        Arrays.asList("Mendeljejev", "Element", "Periodni sistem", "Atom"),
                        "Hemija"
                ),
                "Nauka"
        );
    }

    private void bindViews(View view) {
        timerText = view.findViewById(R.id.timerText);
        playerOneScoreText = view.findViewById(R.id.playerOneScoreText);
        playerTwoScoreText = view.findViewById(R.id.playerTwoScoreText);

        columnAInput = view.findViewById(R.id.columnAInput);
        columnBInput = view.findViewById(R.id.columnBInput);
        columnCInput = view.findViewById(R.id.columnCInput);
        columnDInput = view.findViewById(R.id.columnDInput);

        finalSolutionInput = view.findViewById(R.id.finalSolutionInput);
        guessFinalSolutionButton = view.findViewById(R.id.guessFinalSolutionButton);

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

    private void setupAssociationData() {
        setupColumn("A", association.getColumnA(),
                a1Button, a2Button, a3Button, a4Button, columnASolutionButton);

        setupColumn("B", association.getColumnB(),
                b1Button, b2Button, b3Button, b4Button, columnBSolutionButton);

        setupColumn("C", association.getColumnC(),
                c1Button, c2Button, c3Button, c4Button, columnCSolutionButton);

        setupColumn("D", association.getColumnD(),
                d1Button, d2Button, d3Button, d4Button, columnDSolutionButton);
    }

    private void setupColumn(String column,
                             AssociationColumn associationColumn,
                             Button firstButton,
                             Button secondButton,
                             Button thirdButton,
                             Button fourthButton,
                             Button solutionButton) {

        hiddenValues.put(firstButton, associationColumn.getClues().get(0));
        hiddenValues.put(secondButton, associationColumn.getClues().get(1));
        hiddenValues.put(thirdButton, associationColumn.getClues().get(2));
        hiddenValues.put(fourthButton, associationColumn.getClues().get(3));

        hideSolutionButton(solutionButton, column);
    }

    private void setupHiddenButtons() {
        for (Button button : hiddenValues.keySet()) {
            setButtonColor(button, DEFAULT_COLOR);

            button.setOnClickListener(v -> {
                Button clickedButton = (Button) v;

                if (!clickedButton.isEnabled()) {
                    return;
                }

                revealField(clickedButton);
            });
        }
    }

    private void setupSolutionButtons() {
        columnASolutionButton.setOnClickListener(v ->
                guessColumnSolution("A", columnAInput, columnASolutionButton, association.getColumnA()));

        columnBSolutionButton.setOnClickListener(v ->
                guessColumnSolution("B", columnBInput, columnBSolutionButton, association.getColumnB()));

        columnCSolutionButton.setOnClickListener(v ->
                guessColumnSolution("C", columnCInput, columnCSolutionButton, association.getColumnC()));

        columnDSolutionButton.setOnClickListener(v ->
                guessColumnSolution("D", columnDInput, columnDSolutionButton, association.getColumnD()));
    }

    private void revealField(Button button) {
        String value = hiddenValues.get(button);
        String column = getColumnForButton(button);

        button.setText(value);
        button.setEnabled(false);
        setButtonColor(button, REVEALED_COLOR);

        openedFieldsByColumn.put(column, openedFieldsByColumn.get(column) + 1);
    }

    private void guessColumnSolution(String column,
                                     EditText input,
                                     Button solutionButton,
                                     AssociationColumn associationColumn) {

        String guess = input.getText().toString().trim();

        if (guess.isEmpty()) {
            input.setError("Unesi rešenje");
            return;
        }

        if (!guess.equalsIgnoreCase(associationColumn.getSolution())) {
            input.setError("Netačno");
            input.setText("");
            return;
        }

        solutionButton.setText(associationColumn.getSolution());
        solutionButton.setEnabled(false);
        input.setEnabled(false);
        setButtonColor(solutionButton, CORRECT_COLOR);
        revealColumnFields(column);

        int opened = openedFieldsByColumn.get(column);
        int unopened = 4 - opened;

        int points = 2 + unopened;
        playerOneScore += points;

        updateScoreText();

        Toast.makeText(
                requireContext(),
                "Tačno rešenje kolone! +" + points,
                Toast.LENGTH_SHORT
        ).show();
    }

    private void guessFinalSolution() {
        String guess = finalSolutionInput.getText().toString().trim();

        if (guess.isEmpty()) {
            finalSolutionInput.setError("Unesi konačno rešenje");
            return;
        }

        if (guess.equalsIgnoreCase(association.getFinalSolution())) {
            int points = calculateFinalSolutionPoints();
            playerOneScore += points;
            updateScoreText();

            Toast.makeText(
                    requireContext(),
                    "Tačno konačno rešenje! +" + points,
                    Toast.LENGTH_SHORT
            ).show();

            finishGame();
        } else {
            Toast.makeText(
                    requireContext(),
                    "Netačno konačno rešenje",
                    Toast.LENGTH_SHORT
            ).show();

            finalSolutionInput.setText("");
        }
    }

    private int calculateFinalSolutionPoints() {
        int points = 7;

        points += calculateColumnFinalBonus("A", columnASolutionButton);
        points += calculateColumnFinalBonus("B", columnBSolutionButton);
        points += calculateColumnFinalBonus("C", columnCSolutionButton);
        points += calculateColumnFinalBonus("D", columnDSolutionButton);

        return points;
    }

    private int calculateColumnFinalBonus(String column, Button solutionButton) {
        if (!solutionButton.isEnabled()) {
            return 0;
        }

        int opened = openedFieldsByColumn.get(column);

        if (opened == 0) {
            return 6;
        }

        return 2 + (4 - opened);
    }

    private String getColumnForButton(Button button) {
        if (button == a1Button || button == a2Button || button == a3Button || button == a4Button) {
            return "A";
        }

        if (button == b1Button || button == b2Button || button == b3Button || button == b4Button) {
            return "B";
        }

        if (button == c1Button || button == c2Button || button == c3Button || button == c4Button) {
            return "C";
        }

        return "D";
    }

    private void hideSolutionButton(Button button, String label) {
        button.setText("Rešenje " + label);
        button.setEnabled(true);
        setButtonColor(button, DEFAULT_COLOR);
    }

    private void startTimer() {
        stopTimer();

        timer = new CountDownTimer(120000, 1000) {
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

        GuessTheCombinationFragment fragment = new GuessTheCombinationFragment();
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

    private void revealColumnFields(String column) {
        if ("A".equals(column)) {
            revealFieldIfHidden(a1Button);
            revealFieldIfHidden(a2Button);
            revealFieldIfHidden(a3Button);
            revealFieldIfHidden(a4Button);
        } else if ("B".equals(column)) {
            revealFieldIfHidden(b1Button);
            revealFieldIfHidden(b2Button);
            revealFieldIfHidden(b3Button);
            revealFieldIfHidden(b4Button);
        } else if ("C".equals(column)) {
            revealFieldIfHidden(c1Button);
            revealFieldIfHidden(c2Button);
            revealFieldIfHidden(c3Button);
            revealFieldIfHidden(c4Button);
        } else {
            revealFieldIfHidden(d1Button);
            revealFieldIfHidden(d2Button);
            revealFieldIfHidden(d3Button);
            revealFieldIfHidden(d4Button);
        }
    }

    private void revealFieldIfHidden(Button button) {
        String value = hiddenValues.get(button);

        if (value == null) {
            return;
        }

        button.setText(value);
        button.setEnabled(false);
        setButtonColor(button, REVEALED_COLOR);
    }

    private void revealAllColumns() {
        revealColumnFields("A");
        revealColumnFields("B");
        revealColumnFields("C");
        revealColumnFields("D");

        revealSolvedColumnButton(columnASolutionButton, association.getColumnA());
        revealSolvedColumnButton(columnBSolutionButton, association.getColumnB());
        revealSolvedColumnButton(columnCSolutionButton, association.getColumnC());
        revealSolvedColumnButton(columnDSolutionButton, association.getColumnD());
    }

    private void revealSolvedColumnButton(Button button, AssociationColumn column) {
        button.setText(column.getSolution());
        button.setEnabled(false);
        setButtonColor(button, CORRECT_COLOR);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        stopTimer();
    }
}