package com.example.slagalica.Fragments;

import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.preference.PreferenceManager;
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
import com.google.firebase.firestore.GeoPoint;

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
    private final Map<String, List<org.osmdroid.util.GeoPoint>> regionPolygons = createRegionPolygons();

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
        configureOsmDroid(requireContext());
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_regions, container, false);
        ImageButton menuButton = view.findViewById(R.id.menuButton);
        cycleRangeText = view.findViewById(R.id.regionCycleRangeText);
        stateText = view.findViewById(R.id.regionsStateText);
        rowsContainer = view.findViewById(R.id.regionRows);
        FrameLayout mapContainer = view.findViewById(R.id.regionMapContainer);
        mapView = new MapView(requireContext());
        configureMapView();
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
        if (mapView != null) {
            mapView.onResume();
        }
        refreshHandler.removeCallbacks(refreshRunnable);
        refreshHandler.postDelayed(refreshRunnable, REFRESH_INTERVAL_MS);
    }

    @Override
    public void onPause() {
        refreshHandler.removeCallbacks(refreshRunnable);
        if (mapView != null) {
            mapView.onPause();
        }
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
