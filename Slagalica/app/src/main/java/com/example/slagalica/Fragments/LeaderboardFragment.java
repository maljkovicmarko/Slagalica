package com.example.slagalica.Fragments;

import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.fragment.app.Fragment;

import com.example.slagalica.Activities.MainActivity;
import com.example.slagalica.Model.LeaderboardEntry;
import com.example.slagalica.Model.LeaderboardCycle;
import com.example.slagalica.R;
import com.example.slagalica.Services.LeaderboardService;
import com.example.slagalica.Util.LeagueIconResolver;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

public class LeaderboardFragment extends Fragment {
    private static final long REFRESH_INTERVAL_MS = 120_000L;

    private final Handler refreshHandler = new Handler(Looper.getMainLooper());
    private final DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy.", Locale.ROOT);
    private LeaderboardService leaderboardService;
    private LeaderboardCycle.Type selectedCycle = LeaderboardCycle.Type.WEEKLY;
    private TextView cycleRangeText;
    private TextView stateText;
    private LinearLayout rowsContainer;
    private Button weeklyButton;
    private Button monthlyButton;

    private final Runnable refreshRunnable = new Runnable() {
        @Override
        public void run() {
            loadLeaderboard();
            refreshHandler.postDelayed(this, REFRESH_INTERVAL_MS);
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        leaderboardService = new LeaderboardService();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_leaderboard, container, false);
        ImageButton menuButton = view.findViewById(R.id.menuButton);
        cycleRangeText = view.findViewById(R.id.cycleRangeText);
        stateText = view.findViewById(R.id.leaderboardStateText);
        rowsContainer = view.findViewById(R.id.leaderboardRows);
        weeklyButton = view.findViewById(R.id.weeklyButton);
        monthlyButton = view.findViewById(R.id.monthlyButton);

        menuButton.setOnClickListener(v -> ((MainActivity) requireActivity()).toggleNavbar());
        weeklyButton.setOnClickListener(v -> selectCycle(LeaderboardCycle.Type.WEEKLY));
        monthlyButton.setOnClickListener(v -> selectCycle(LeaderboardCycle.Type.MONTHLY));

        updateCycleButtons();
        loadLeaderboard();
        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        refreshHandler.removeCallbacks(refreshRunnable);
        refreshHandler.postDelayed(refreshRunnable, REFRESH_INTERVAL_MS);
    }

    @Override
    public void onPause() {
        refreshHandler.removeCallbacks(refreshRunnable);
        super.onPause();
    }

    private void selectCycle(LeaderboardCycle.Type cycleType) {
        selectedCycle = cycleType;
        updateCycleButtons();
        loadLeaderboard();
    }

    private void updateCycleButtons() {
        weeklyButton.setEnabled(selectedCycle != LeaderboardCycle.Type.WEEKLY);
        monthlyButton.setEnabled(selectedCycle != LeaderboardCycle.Type.MONTHLY);
    }

    private void loadLeaderboard() {
        if (!isAdded()) {
            return;
        }
        stateText.setText(R.string.loading_leaderboard);
        leaderboardService.loadPlayerLeaderboard(
                selectedCycle,
                (cycleInfo, entries) -> {
                    if (!isAdded()) {
                        return;
                    }
                    cycleRangeText.setText(getString(
                            R.string.leaderboard_cycle_range,
                            cycleInfo.getStartDate().format(dateFormatter),
                            cycleInfo.getEndDate().format(dateFormatter)
                    ));
                    renderEntries(entries);
                },
                errorMessage -> {
                    if (isAdded()) {
                        Toast.makeText(requireContext(), errorMessage, Toast.LENGTH_LONG).show();
                        stateText.setText(R.string.leaderboard_load_failed);
                    }
                }
        );
    }

    private void renderEntries(List<LeaderboardEntry> entries) {
        rowsContainer.removeAllViews();
        if (entries.isEmpty()) {
            stateText.setText(R.string.empty_leaderboard);
            return;
        }

        stateText.setText(getString(R.string.leaderboard_count, entries.size()));
        for (LeaderboardEntry entry : entries) {
            rowsContainer.addView(createEntryRow(entry));
        }
    }

    private View createEntryRow(LeaderboardEntry entry) {
        LinearLayout row = new LinearLayout(requireContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        row.setPadding(16, 14, 16, 14);
        row.setBackgroundColor(entry.isCurrentUser() ? Color.rgb(255, 248, 225) : Color.TRANSPARENT);

        TextView rank = createText("#" + entry.getRank(), 46, true);
        ImageView leagueIcon = createLeagueIcon(entry.getLeagueName());
        TextView player = createText(entry.getUsername() + "\n" + entry.getLeagueName(), 0, entry.isCurrentUser());
        TextView stars = createText(entry.getStars() + " ★", 72, true);

        row.addView(rank);
        row.addView(leagueIcon);
        row.addView(player);
        row.addView(stars);
        return row;
    }

    private ImageView createLeagueIcon(String leagueName) {
        ImageView icon = new ImageView(requireContext());
        icon.setImageResource(LeagueIconResolver.iconFor(leagueName));
        icon.setContentDescription(leagueName);
        icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(32), dp(32));
        params.setMarginEnd(dp(8));
        icon.setLayoutParams(params);
        return icon;
    }

    private TextView createText(String text, int widthDp, boolean bold) {
        TextView textView = new TextView(requireContext());
        textView.setText(text);
        textView.setTextSize(15);
        textView.setTextColor(Color.rgb(33, 33, 33));
        if (bold) {
            textView.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        }

        int width = widthDp <= 0 ? 0 : dp(widthDp);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                widthDp <= 0 ? 0 : width,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.weight = widthDp <= 0 ? 1 : 0;
        textView.setLayoutParams(params);
        return textView;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density);
    }
}
