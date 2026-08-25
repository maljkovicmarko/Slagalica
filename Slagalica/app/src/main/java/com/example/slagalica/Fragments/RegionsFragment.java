package com.example.slagalica.Fragments;

import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.NumberPicker;
import android.widget.TextView;
import android.widget.Toast;

import androidx.fragment.app.Fragment;

import com.example.slagalica.Activities.MainActivity;
import com.example.slagalica.Model.RegionLeaderboardEntry;
import com.example.slagalica.R;
import com.example.slagalica.Services.LeaderboardService;
import com.example.slagalica.Services.SessionSnapshot;
import com.example.slagalica.Services.WebSocketConfig;
import com.example.slagalica.Services.WebSocketGameClient;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.GeoPoint;

import org.json.JSONArray;
import org.json.JSONObject;

import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.XYTileSource;
import org.osmdroid.util.BoundingBox;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.Polygon;

import java.io.File;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class RegionsFragment extends Fragment {
    private static final long REFRESH_INTERVAL_MS = 120_000L;
    private static final long CHALLENGE_REFRESH_INTERVAL_MS = 10_000L;
    private static final XYTileSource OSM_TILE_SOURCE = new XYTileSource(
            "OpenStreetMap",
            0,
            19,
            256,
            ".png",
            new String[]{"https://tile.openstreetmap.org/"}
    );

    private final Handler refreshHandler = new Handler(Looper.getMainLooper());
    private final DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy.", Locale.ROOT);
    private LeaderboardService leaderboardService;
    private TextView cycleRangeText;
    private TextView stateText;
    private LinearLayout rowsContainer;
    private MapView mapView;
    private TextView challengesStateText;
    private LinearLayout challengeRows;
    private Button createChallengeButton;
    private WebSocketGameClient webSocketGameClient;
    private WebSocketGameClient.ListenerHandle challengeListenerHandle;
    private String currentUid;
    private final Map<String, List<org.osmdroid.util.GeoPoint>> regionPolygons = createRegionPolygons();

    private final Runnable refreshRunnable = new Runnable() {
        @Override
        public void run() {
            loadRegions();
            refreshHandler.postDelayed(this, REFRESH_INTERVAL_MS);
        }
    };

    private final Runnable challengeRefreshRunnable = new Runnable() {
        @Override
        public void run() {
            loadChallenges();
            refreshHandler.postDelayed(this, CHALLENGE_REFRESH_INTERVAL_MS);
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        leaderboardService = new LeaderboardService();
        webSocketGameClient = WebSocketGameClient.getInstance();
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        currentUid = user == null ? null : user.getUid();
        configureOsmDroid(requireContext());
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_regions, container, false);
        ImageButton menuButton = view.findViewById(R.id.menuButton);
        cycleRangeText = view.findViewById(R.id.regionCycleRangeText);
        stateText = view.findViewById(R.id.regionsStateText);
        rowsContainer = view.findViewById(R.id.regionRows);
        challengesStateText = view.findViewById(R.id.challengesStateText);
        challengeRows = view.findViewById(R.id.challengeRows);
        createChallengeButton = view.findViewById(R.id.createChallengeButton);
        FrameLayout mapContainer = view.findViewById(R.id.regionMapContainer);
        mapView = new MapView(requireContext());
        configureMapView();
        mapContainer.addView(mapView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        ));

        menuButton.setOnClickListener(v -> ((MainActivity) requireActivity()).toggleNavbar());
        createChallengeButton.setOnClickListener(v -> showCreateChallengeDialog());
        loadRegions();
        connectAndLoadChallenges();
        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (mapView != null) {
            mapView.onResume();
        }
        refreshHandler.removeCallbacks(refreshRunnable);
        refreshHandler.postDelayed(refreshRunnable, REFRESH_INTERVAL_MS);
        refreshHandler.removeCallbacks(challengeRefreshRunnable);
        refreshHandler.postDelayed(challengeRefreshRunnable, CHALLENGE_REFRESH_INTERVAL_MS);
    }

    @Override
    public void onPause() {
        refreshHandler.removeCallbacks(refreshRunnable);
        refreshHandler.removeCallbacks(challengeRefreshRunnable);
        if (mapView != null) {
            mapView.onPause();
        }
        super.onPause();
    }

    @Override
    public void onDestroyView() {
        if (challengeListenerHandle != null) {
            challengeListenerHandle.remove();
            challengeListenerHandle = null;
        }
        super.onDestroyView();
    }

    private void connectAndLoadChallenges() {
        if (currentUid == null) {
            challengesStateText.setText("Prijavi se da bi video izazove.");
            createChallengeButton.setEnabled(false);
            return;
        }
        webSocketGameClient.setServerUrl(WebSocketConfig.getServerUrl(requireContext()));
        webSocketGameClient.connect(currentUid, new WebSocketGameClient.OnConnected() {
            @Override
            public void onConnected() {
                registerChallengeListener();
                loadChallenges();
            }

            @Override
            public void onFailure(String errorMessage) {
                if (isAdded() && challengesStateText != null) {
                    challengesStateText.setText("Server izazova nije dostupan.");
                }
            }
        });
    }

    private void registerChallengeListener() {
        if (challengeListenerHandle != null) {
            challengeListenerHandle.remove();
        }
        challengeListenerHandle = webSocketGameClient.addRegionalChallengeListener(
                new WebSocketGameClient.OnRegionalChallengeListener() {
                    @Override
                    public void onChallengeUpdated(JSONObject challenge) {
                        loadChallenges();
                    }

                    @Override
                    public void onFailure(String errorMessage) {
                    }
                }
        );
    }

    private void loadChallenges() {
        if (!isAdded() || challengesStateText == null || currentUid == null) {
            return;
        }
        webSocketGameClient.listRegionalChallenges(new WebSocketGameClient.OnRequestResult() {
            @Override
            public void onSuccess(JSONObject data) {
                if (isAdded() && challengeRows != null) {
                    renderChallenges(data.optJSONArray("challenges"));
                }
            }

            @Override
            public void onFailure(String errorMessage) {
                if (isAdded() && challengesStateText != null) {
                    challengesStateText.setText(errorMessage);
                }
            }
        });
    }

    private void renderChallenges(JSONArray challenges) {
        challengeRows.removeAllViews();
        if (challenges == null || challenges.length() == 0) {
            challengesStateText.setText("Trenutno nema izazova.");
            return;
        }
        challengesStateText.setText("Najbolji rezultat je privremen dok se prijave ne zatvore.");
        for (int index = 0; index < challenges.length(); index++) {
            JSONObject challenge = challenges.optJSONObject(index);
            if (challenge != null) {
                challengeRows.addView(createChallengeCard(challenge));
            }
        }
    }

    private View createChallengeCard(JSONObject challenge) {
        LinearLayout card = new LinearLayout(requireContext());
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(12), dp(14), dp(12));
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(dp(300), LinearLayout.LayoutParams.WRAP_CONTENT);
        cardParams.setMargins(0, dp(6), dp(10), dp(10));
        card.setLayoutParams(cardParams);
        GradientDrawable background = new GradientDrawable();
        background.setColor(Color.rgb(250, 250, 250));
        background.setCornerRadius(dp(12));
        background.setStroke(dp(1), Color.rgb(210, 210, 210));
        card.setBackground(background);

        String status = challenge.optString("status", "open");
        int participantCount = challenge.optInt("participantCount", 0);
        String creator = challenge.optString("creatorUsername", "Igrač");
        TextView title = challengeText(creator + " • " + participantCount + "/4", 17, true);
        card.addView(title);
        card.addView(challengeText(
                "Ulog: " + challenge.optInt("stakeStars", 0) + " ★ i "
                        + challenge.optInt("stakeTokens", 0) + " tokena",
                14,
                false
        ));
        card.addView(challengeText(challengeStatusText(challenge), 13, false));

        JSONArray participants = challenge.optJSONArray("participants");
        if (participants != null) {
            for (int index = 0; index < participants.length(); index++) {
                JSONObject participant = participants.optJSONObject(index);
                if (participant == null) {
                    continue;
                }
                String participantStatus = participant.optString("status", "ready");
                String scoreText = "finished".equals(participantStatus) || "abandoned".equals(participantStatus)
                        ? " — " + participant.optInt("score", 0) + " poena"
                        : " — " + runStatusLabel(participantStatus);
                int rewardStars = participant.optInt("rewardStars", 0);
                int rewardTokens = participant.optInt("rewardTokens", 0);
                if ("finished".equals(status) && (rewardStars > 0 || rewardTokens > 0)) {
                    scoreText += "  (nagrada " + rewardStars + " ★, " + rewardTokens + " tokena)";
                }
                String prefix = participant.optString("uid", "").equals(challenge.optString("provisionalWinnerUid", ""))
                        ? "🏆 "
                        : participant.optString("uid", "").equals(challenge.optString("provisionalRunnerUpUid", "")) ? "② " : "• ";
                card.addView(challengeText(prefix + participant.optString("username", "Igrač") + scoreText, 13, false));
            }
        }

        LinearLayout actions = new LinearLayout(requireContext());
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setPadding(0, dp(6), 0, 0);
        boolean viewerParticipant = challenge.optBoolean("viewerParticipant", false);
        String viewerRunStatus = challenge.optString("viewerRunStatus", "");
        if ("open".equals(status) && !viewerParticipant && participantCount < 4) {
            actions.addView(actionButton("Prihvati", view -> acceptChallenge(challenge.optString("challengeId"))));
        }
        if (viewerParticipant && ("ready".equals(viewerRunStatus) || "running".equals(viewerRunStatus))) {
            actions.addView(actionButton("Igraj", view -> startChallengeRun(challenge.optString("challengeId"))));
        }
        if (currentUid != null && currentUid.equals(challenge.optString("creatorUid")) && "open".equals(status)) {
            if (participantCount >= 2) {
                actions.addView(actionButton("Zatvori prijave", view -> closeChallenge(challenge.optString("challengeId"))));
            } else {
                actions.addView(actionButton("Otkaži", view -> cancelChallenge(challenge.optString("challengeId"))));
            }
        }
        if (actions.getChildCount() > 0) {
            card.addView(actions);
        }
        return card;
    }

    private String challengeStatusText(JSONObject challenge) {
        String status = challenge.optString("status", "open");
        if ("finished".equals(status)) {
            return "Završen — nagrade su isplaćene";
        }
        if ("cancelled".equals(status)) {
            return "Otkazan";
        }
        String winner = challenge.optString("provisionalWinnerUid", "");
        if (!winner.isBlank()) {
            return "Privremena nagrada: " + challenge.optInt("provisionalWinnerStars", 0)
                    + " ★ i " + challenge.optInt("provisionalWinnerTokens", 0) + " tokena";
        }
        return "closed".equals(status) ? "Prijave zatvorene — čekaju se rezultati" : "Prijave su otvorene";
    }

    private String runStatusLabel(String status) {
        if ("running".equals(status)) {
            return "igra";
        }
        return "čeka partiju";
    }

    private TextView challengeText(String value, int sizeSp, boolean bold) {
        TextView textView = new TextView(requireContext());
        textView.setText(value);
        textView.setTextSize(sizeSp);
        textView.setTextColor(Color.rgb(45, 45, 45));
        if (bold) {
            textView.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        }
        return textView;
    }

    private Button actionButton(String label, View.OnClickListener listener) {
        Button button = new Button(requireContext());
        button.setText(label);
        button.setTextSize(12);
        button.setOnClickListener(listener);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        params.setMargins(0, 0, dp(4), 0);
        button.setLayoutParams(params);
        return button;
    }

    private void showCreateChallengeDialog() {
        LinearLayout content = new LinearLayout(requireContext());
        content.setOrientation(LinearLayout.HORIZONTAL);
        content.setPadding(dp(24), dp(8), dp(24), 0);
        NumberPicker stars = new NumberPicker(requireContext());
        stars.setMinValue(0);
        stars.setMaxValue(10);
        stars.setValue(5);
        NumberPicker tokens = new NumberPicker(requireContext());
        tokens.setMinValue(0);
        tokens.setMaxValue(2);
        tokens.setValue(1);
        content.addView(stars, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        content.addView(tokens, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        new AlertDialog.Builder(requireContext())
                .setTitle("Ulog: zvezde / tokeni")
                .setView(content)
                .setPositiveButton("Postavi", (dialog, which) -> {
                    if (stars.getValue() == 0 && tokens.getValue() == 0) {
                        Toast.makeText(requireContext(), "Izaberi najmanje jedan ulog.", Toast.LENGTH_SHORT).show();
                    } else {
                        createChallenge(stars.getValue(), tokens.getValue());
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void createChallenge(int stars, int tokens) {
        webSocketGameClient.createRegionalChallenge(stars, tokens, refreshAfterChallengeCommand());
    }

    private void acceptChallenge(String challengeId) {
        webSocketGameClient.acceptRegionalChallenge(challengeId, refreshAfterChallengeCommand());
    }

    private void closeChallenge(String challengeId) {
        webSocketGameClient.closeRegionalChallenge(challengeId, refreshAfterChallengeCommand());
    }

    private void cancelChallenge(String challengeId) {
        webSocketGameClient.cancelRegionalChallenge(challengeId, refreshAfterChallengeCommand());
    }

    private WebSocketGameClient.OnRequestResult refreshAfterChallengeCommand() {
        return new WebSocketGameClient.OnRequestResult() {
            @Override
            public void onSuccess(JSONObject data) {
                loadChallenges();
            }

            @Override
            public void onFailure(String errorMessage) {
                if (isAdded()) {
                    Toast.makeText(requireContext(), errorMessage, Toast.LENGTH_LONG).show();
                }
            }
        };
    }

    private void startChallengeRun(String challengeId) {
        webSocketGameClient.startChallengeRun(challengeId, new WebSocketGameClient.OnRequestResult() {
            @Override
            public void onSuccess(JSONObject data) {
                JSONObject session = data.optJSONObject("session");
                if (session == null || !isAdded()) {
                    return;
                }
                requireActivity().getSupportFragmentManager()
                        .beginTransaction()
                        .replace(R.id.fragmentContainer, GeneralKnowledgeFragment.newInstance(session.toString()))
                        .commit();
            }

            @Override
            public void onFailure(String errorMessage) {
                if (isAdded()) {
                    Toast.makeText(requireContext(), errorMessage, Toast.LENGTH_LONG).show();
                }
            }
        });
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density);
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
        updateMapMarkers(entries);
        if (entries.isEmpty()) {
            stateText.setText(R.string.empty_regions);
            return;
        }

        stateText.setText(getString(R.string.regions_monthly_count, entries.size()));
        for (RegionLeaderboardEntry entry : entries) {
            rowsContainer.addView(createRegionRow(entry));
        }
    }

    private void configureMapView() {
        mapView.setTileSource(OSM_TILE_SOURCE);
        mapView.setMultiTouchControls(true);
        mapView.setMinZoomLevel(6.0);
        mapView.setMaxZoomLevel(15.0);
        mapView.getController().setZoom(7.0);
        mapView.getController().setCenter(new org.osmdroid.util.GeoPoint(44.0, 20.8));
        mapView.zoomToBoundingBox(new BoundingBox(46.3, 23.1, 42.0, 18.8), false);
    }

    private void configureOsmDroid(Context context) {
        Context appContext = context.getApplicationContext();
        Configuration.getInstance().load(
                appContext,
                PreferenceManager.getDefaultSharedPreferences(appContext)
        );
        Configuration.getInstance().setUserAgentValue(
                "Slagalica/1.0 Android (com.example.slagalica; contact: markomaljak@gmail.com)"
        );
        Configuration.getInstance().setTileDownloadThreads((short) 1);
        Configuration.getInstance().setTileDownloadMaxQueueSize((short) 8);
        Configuration.getInstance().setOsmdroidBasePath(new File(appContext.getCacheDir(), "osmdroid"));
        Configuration.getInstance().setOsmdroidTileCache(new File(appContext.getCacheDir(), "osmdroid/tiles"));
    }

    private void updateMapMarkers(List<RegionLeaderboardEntry> entries) {
        mapView.getOverlays().clear();

        for (RegionLeaderboardEntry entry : entries) {
            addRegionPolygon(entry);
            for (GeoPoint mapPoint : entry.getMapPoints()) {
                Marker marker = new Marker(mapView);
                marker.setPosition(new org.osmdroid.util.GeoPoint(
                        mapPoint.getLatitude(),
                        mapPoint.getLongitude()
                ));
                marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
                marker.setTitle(entry.getRegion());
                marker.setSnippet(getString(
                        R.string.region_map_marker_snippet,
                        entry.getMonthlyStars(),
                        entry.getActivePlayers()
                ));
                marker.setOnMarkerClickListener((clickedMarker, clickedMapView) -> {
                    showRegionStats(entry);
                    return true;
                });
                mapView.getOverlays().add(marker);
            }
        }

        mapView.invalidate();
    }

    private void addRegionPolygon(RegionLeaderboardEntry entry) {
        List<org.osmdroid.util.GeoPoint> points = regionPolygons.get(normalizeRegionKey(entry.getRegionId()));
        if (points == null || points.isEmpty()) {
            return;
        }

        Polygon polygon = new Polygon(mapView);
        polygon.setPoints(points);
        polygon.setTitle(entry.getRegion());
        polygon.setSnippet(getString(
                R.string.region_map_marker_snippet,
                entry.getMonthlyStars(),
                entry.getActivePlayers()
        ));
        polygon.getFillPaint().setColor(regionFillColor(entry));
        polygon.getOutlinePaint().setColor(regionOutlineColor(entry));
        polygon.getOutlinePaint().setStrokeWidth(entry.isCurrentUserRegion() ? 5f : 3f);
        polygon.setOnClickListener((clickedPolygon, clickedMapView, eventPos) -> {
            showRegionStats(entry);
            return true;
        });
        mapView.getOverlays().add(polygon);
    }

    private int regionFillColor(RegionLeaderboardEntry entry) {
        if (entry.isCurrentUserRegion()) {
            return Color.argb(90, 255, 193, 7);
        }
        if (entry.getRank() == 1) {
            return Color.argb(70, 255, 215, 0);
        }
        if (entry.getRank() == 2) {
            return Color.argb(65, 189, 189, 189);
        }
        if (entry.getRank() == 3) {
            return Color.argb(65, 205, 127, 50);
        }
        return Color.argb(45, 76, 175, 80);
    }

    private int regionOutlineColor(RegionLeaderboardEntry entry) {
        if (entry.isCurrentUserRegion()) {
            return Color.rgb(255, 152, 0);
        }
        if (entry.getRank() == 1) {
            return Color.rgb(255, 193, 7);
        }
        if (entry.getRank() == 2) {
            return Color.rgb(117, 117, 117);
        }
        if (entry.getRank() == 3) {
            return Color.rgb(141, 84, 35);
        }
        return Color.rgb(46, 125, 50);
    }

    private Map<String, List<org.osmdroid.util.GeoPoint>> createRegionPolygons() {
        Map<String, List<org.osmdroid.util.GeoPoint>> polygons = new HashMap<>();
        polygons.put("vojvodina", points(
                point(46.25, 18.85),
                point(46.25, 21.55),
                point(45.55, 21.45),
                point(44.95, 20.95),
                point(44.75, 19.10),
                point(45.45, 18.75)
        ));
        polygons.put("beograd", points(
                point(45.05, 19.85),
                point(45.05, 20.85),
                point(44.45, 20.85),
                point(44.45, 19.85)
        ));
        List<org.osmdroid.util.GeoPoint> sumadijaZapadnaSrbija = points(
                point(44.80, 18.85),
                point(44.80, 20.45),
                point(43.85, 21.00),
                point(42.95, 20.55),
                point(42.85, 19.15),
                point(43.75, 18.85)
        );
        polygons.put("sumadija_zapadna_srbija", sumadijaZapadnaSrbija);
        polygons.put("sumadija_i_zapadna_srbija", sumadijaZapadnaSrbija);

        List<org.osmdroid.util.GeoPoint> juznaIstocnaSrbija = points(
                point(44.35, 20.75),
                point(44.55, 22.95),
                point(43.20, 23.05),
                point(42.20, 22.45),
                point(42.35, 20.75),
                point(43.35, 20.55)
        );
        polygons.put("juzna_istocna_srbija", juznaIstocnaSrbija);
        polygons.put("juzna_i_istocna_srbija", juznaIstocnaSrbija);
        polygons.put("kosovo_metohija", points(
                point(43.30, 20.05),
                point(43.15, 21.35),
                point(42.05, 21.65),
                point(41.85, 20.10),
                point(42.55, 19.75)
        ));
        return polygons;
    }

    private List<org.osmdroid.util.GeoPoint> points(org.osmdroid.util.GeoPoint... points) {
        List<org.osmdroid.util.GeoPoint> result = new ArrayList<>();
        for (org.osmdroid.util.GeoPoint point : points) {
            result.add(point);
        }
        return result;
    }

    private org.osmdroid.util.GeoPoint point(double latitude, double longitude) {
        return new org.osmdroid.util.GeoPoint(latitude, longitude);
    }

    private String normalizeRegionKey(String regionId) {
        return regionId == null ? "" : regionId.trim().toLowerCase(Locale.ROOT);
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
