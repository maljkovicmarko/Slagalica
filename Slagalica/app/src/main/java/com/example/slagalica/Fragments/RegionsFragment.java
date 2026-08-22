package com.example.slagalica.Fragments;

import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.fragment.app.Fragment;

import com.example.slagalica.Activities.MainActivity;
import com.example.slagalica.Model.RegionLeaderboardEntry;
import com.example.slagalica.R;
import com.example.slagalica.Services.LeaderboardService;
import com.example.slagalica.Util.SerbiaRegionMapView;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

public class RegionsFragment extends Fragment {
    private static final long REFRESH_INTERVAL_MS = 120_000L;

    private final Handler refreshHandler = new Handler(Looper.getMainLooper());
    private final DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy.", Locale.ROOT);
    private LeaderboardService leaderboardService;
    private TextView cycleRangeText;
    private TextView stateText;
    private LinearLayout rowsContainer;
    private SerbiaRegionMapView mapView;

    private final Runnable refreshRunnable = new Runnable() {
        @Override
        public void run() {
            loadRegions();
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
        View view = inflater.inflate(R.layout.fragment_regions, container, false);
        ImageButton menuButton = view.findViewById(R.id.menuButton);
        cycleRangeText = view.findViewById(R.id.regionCycleRangeText);
        stateText = view.findViewById(R.id.regionsStateText);
        rowsContainer = view.findViewById(R.id.regionRows);
        FrameLayout mapContainer = view.findViewById(R.id.regionMapContainer);
        mapView = new SerbiaRegionMapView(requireContext());
        mapContainer.addView(mapView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        ));

        menuButton.setOnClickListener(v -> ((MainActivity) requireActivity()).toggleNavbar());
        loadRegions();
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

    private void loadRegions() {
        if (!isAdded()) {
            return;
        }
        stateText.setText(R.string.loading_regions);
        leaderboardService.loadRegionLeaderboard(
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
                        stateText.setText(R.string.regions_load_failed);
                    }
                }
        );
    }

    private void renderEntries(List<RegionLeaderboardEntry> entries) {
        rowsContainer.removeAllViews();
        mapView.setRegions(entries);
        if (entries.isEmpty()) {
            stateText.setText(R.string.empty_regions);
            return;
        }

        stateText.setText(getString(R.string.regions_monthly_count, entries.size()));
        for (RegionLeaderboardEntry entry : entries) {
            rowsContainer.addView(createRegionRow(entry));
        }
    }

    private View createRegionRow(RegionLeaderboardEntry entry) {
        LinearLayout row = new LinearLayout(requireContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        row.setPadding(16, 14, 16, 14);
        row.setBackgroundColor(entry.isCurrentUserRegion() ? Color.rgb(255, 248, 225) : Color.TRANSPARENT);
        row.setOnClickListener(v -> showRegionStats(entry));

        TextView rank = createText("#" + entry.getRank(), 46, true);
        ImageView icon = createIcon(entry);
        TextView region = createText(entry.getRegion(), 0, entry.isCurrentUserRegion());
        TextView stars = createText(entry.getMonthlyStars() + " ★", 76, true);

        row.addView(rank);
        row.addView(icon);
        row.addView(region);
        row.addView(stars);
        return row;
    }

    private void showRegionStats(RegionLeaderboardEntry entry) {
        String message = getString(
                R.string.region_stats_message,
                entry.getFirstPlaceCount(),
                entry.getSecondPlaceCount(),
                entry.getThirdPlaceCount(),
                entry.getMonthlyStars(),
                entry.getActivePlayers(),
                entry.getRegisteredPlayers()
        );
        new AlertDialog.Builder(requireContext())
                .setTitle(entry.getRegion())
                .setIcon(entry.getIconResId())
                .setMessage(message)
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    private ImageView createIcon(RegionLeaderboardEntry entry) {
        ImageView imageView = new ImageView(requireContext());
        imageView.setImageResource(entry.getIconResId());
        imageView.setContentDescription(entry.getRegion());
        int size = (int) (36 * getResources().getDisplayMetrics().density);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(size, size);
        params.setMargins(0, 0, (int) (12 * getResources().getDisplayMetrics().density), 0);
        imageView.setLayoutParams(params);
        return imageView;
    }

    private TextView createText(String text, int widthDp, boolean bold) {
        TextView textView = new TextView(requireContext());
        textView.setText(text);
        textView.setTextSize(15);
        textView.setTextColor(Color.rgb(33, 33, 33));
        if (bold) {
            textView.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        }

        int width = widthDp <= 0 ? 0 : (int) (widthDp * getResources().getDisplayMetrics().density);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                widthDp <= 0 ? 0 : width,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.weight = widthDp <= 0 ? 1 : 0;
        textView.setLayoutParams(params);
        return textView;
    }
}
