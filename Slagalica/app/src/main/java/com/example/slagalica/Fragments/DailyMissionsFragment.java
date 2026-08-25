package com.example.slagalica.Fragments;

import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.fragment.app.Fragment;

import com.example.slagalica.Activities.MainActivity;
import com.example.slagalica.R;
import com.example.slagalica.Services.WebSocketConfig;
import com.example.slagalica.Services.WebSocketGameClient;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import org.json.JSONArray;
import org.json.JSONObject;

public final class DailyMissionsFragment extends Fragment {
    private WebSocketGameClient webSocketGameClient;
    private WebSocketGameClient.ListenerHandle listenerHandle;
    private LinearLayout missionsContainer;
    private TextView statusText;
    private TextView progressText;
    private TextView bonusText;
    private ProgressBar progressBar;
    private String currentUid;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        webSocketGameClient = WebSocketGameClient.getInstance();
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        currentUid = user == null ? null : user.getUid();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_daily_missions, container, false);
        ImageButton menuButton = view.findViewById(R.id.menuButton);
        missionsContainer = view.findViewById(R.id.dailyMissionsContainer);
        statusText = view.findViewById(R.id.dailyMissionsStatus);
        progressText = view.findViewById(R.id.dailyMissionsProgress);
        bonusText = view.findViewById(R.id.dailyMissionsBonus);
        progressBar = view.findViewById(R.id.dailyMissionsProgressBar);

        menuButton.setOnClickListener(v -> ((MainActivity) requireActivity()).toggleNavbar());
        registerListener();
        loadDailyMissions();
        return view;
    }

    @Override
    public void onDestroyView() {
        if (listenerHandle != null) {
            listenerHandle.remove();
            listenerHandle = null;
        }
        super.onDestroyView();
    }

    private void registerListener() {
        listenerHandle = webSocketGameClient.addDailyMissionsListener(new WebSocketGameClient.OnDailyMissionsListener() {
            @Override
            public void onDailyMissionsUpdated(JSONObject state) {
                render(state);
            }

            @Override
            public void onFailure(String errorMessage) {
                showFailure(errorMessage);
            }
        });
    }

    private void loadDailyMissions() {
        if (currentUid == null) {
            statusText.setText(R.string.daily_missions_login_required);
            return;
        }
        statusText.setText(R.string.daily_missions_loading);
        webSocketGameClient.setServerUrl(WebSocketConfig.getServerUrl(requireContext()));
        webSocketGameClient.connect(currentUid, new WebSocketGameClient.OnConnected() {
            @Override
            public void onConnected() {
                webSocketGameClient.getDailyMissions(new WebSocketGameClient.OnRequestResult() {
                    @Override
                    public void onSuccess(JSONObject data) {
                        render(data);
                    }

                    @Override
                    public void onFailure(String errorMessage) {
                        showFailure(errorMessage);
                    }
                });
            }

            @Override
            public void onFailure(String errorMessage) {
                showFailure(errorMessage);
            }
        });
    }

    private void render(JSONObject state) {
        if (!isAdded() || state == null) {
            return;
        }
        missionsContainer.removeAllViews();
        JSONArray missions = state.optJSONArray("missions");
        int missionCount = state.optInt("missionCount", missions == null ? 0 : missions.length());
        int completedCount = state.optInt("completedCount", 0);
        progressBar.setMax(Math.max(1, missionCount));
        progressBar.setProgress(Math.min(completedCount, missionCount));
        progressText.setText(getString(R.string.daily_missions_progress, completedCount, missionCount));
        statusText.setText(getString(R.string.daily_missions_reset_info, state.optString("date", "")));

        if (missions != null) {
            for (int index = 0; index < missions.length(); index++) {
                JSONObject mission = missions.optJSONObject(index);
                if (mission != null) {
                    missionsContainer.addView(createMissionRow(mission));
                }
            }
        }

        int bonusStars = state.optInt("bonusStars", 3);
        int bonusTokens = state.optInt("bonusTokens", 2);
        boolean bonusApplied = state.optBoolean("bonusApplied", false);
        bonusText.setText(getString(
                bonusApplied ? R.string.daily_missions_bonus_completed : R.string.daily_missions_bonus_pending,
                bonusTokens,
                bonusStars
        ));
    }

    private View createMissionRow(JSONObject mission) {
        LinearLayout row = new LinearLayout(requireContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        row.setPadding(24, 20, 24, 20);
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        rowParams.setMargins(0, 0, 0, 14);
        row.setLayoutParams(rowParams);
        row.setBackgroundColor(Color.rgb(245, 245, 245));

        TextView checkText = new TextView(requireContext());
        checkText.setText(mission.optBoolean("completed", false) ? "✓" : "○");
        checkText.setTextSize(24);
        checkText.setTextColor(mission.optBoolean("completed", false) ? Color.rgb(46, 125, 50) : Color.DKGRAY);
        row.addView(checkText, new LinearLayout.LayoutParams(48, LinearLayout.LayoutParams.WRAP_CONTENT));

        LinearLayout textColumn = new LinearLayout(requireContext());
        textColumn.setOrientation(LinearLayout.VERTICAL);
        TextView title = new TextView(requireContext());
        title.setText(mission.optString("title", ""));
        title.setTextSize(16);
        title.setTextColor(Color.BLACK);
        TextView reward = new TextView(requireContext());
        reward.setText(getString(R.string.daily_missions_mission_reward, mission.optInt("rewardStars", 3)));
        reward.setTextSize(13);
        reward.setTextColor(Color.DKGRAY);
        textColumn.addView(title);
        textColumn.addView(reward);
        row.addView(textColumn, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        return row;
    }

    private void showFailure(String errorMessage) {
        if (!isAdded()) {
            return;
        }
        statusText.setText(R.string.daily_missions_load_failed);
        Toast.makeText(requireContext(), errorMessage, Toast.LENGTH_LONG).show();
    }
}
