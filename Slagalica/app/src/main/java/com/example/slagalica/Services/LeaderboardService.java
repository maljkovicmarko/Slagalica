package com.example.slagalica.Services;

import com.example.slagalica.Model.LeaderboardEntry;
import com.example.slagalica.Model.LeaderboardCycle;
import com.example.slagalica.Model.Region;
import com.example.slagalica.Model.RegionLeaderboardEntry;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.GeoPoint;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class LeaderboardService {
    public interface PlayerLeaderboardCallback {
        void onSuccess(LeaderboardCycle cycle, List<LeaderboardEntry> entries);
    }

    public interface RegionLeaderboardCallback {
        void onSuccess(LeaderboardCycle cycle, List<RegionLeaderboardEntry> entries);
    }

    public interface FailureCallback {
        void onFailure(String errorMessage);
    }

    private final FirebaseFirestore db;
    private final FirebaseAuth auth;

    public LeaderboardService() {
        db = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();
    }

    public void loadPlayerLeaderboard(LeaderboardCycle.Type cycleType,
                                      PlayerLeaderboardCallback onSuccess,
                                      FailureCallback onFailure) {
        loadActiveCycle(
                cycleType,
                cycleInfo -> loadPlayerLeaderboardForCycle(cycleType, cycleInfo, onSuccess, onFailure),
                onFailure
        );
    }

    private void loadPlayerLeaderboardForCycle(LeaderboardCycle.Type cycleType,
                                               LeaderboardCycle cycleInfo,
                                               PlayerLeaderboardCallback onSuccess,
                                               FailureCallback onFailure) {
        FirebaseUser currentUser = auth.getCurrentUser();
        String currentUid = currentUser == null ? null : currentUser.getUid();

        db.collection("players")
                .get()
                .addOnSuccessListener(snapshot -> {
                    List<LeaderboardEntry> candidates = new ArrayList<>();
                    for (DocumentSnapshot document : snapshot.getDocuments()) {
                        String storedCycleId = readString(document, cycleType == LeaderboardCycle.Type.WEEKLY ? "weeklyCycleId" : "monthlyCycleId");
                        int games = readInt(document, cycleType == LeaderboardCycle.Type.WEEKLY ? "weeklyGames" : "monthlyGames");
                        if (!cycleInfo.getCycleId().equals(storedCycleId) || games <= 0) {
                            continue;
                        }

                        int stars = readInt(document, cycleType == LeaderboardCycle.Type.WEEKLY ? "weeklyStars" : "monthlyStars");
                        candidates.add(new LeaderboardEntry(
                                0,
                                document.getId(),
                                safeDisplay(readString(document, "username"), "Igrač"),
                                safeDisplay(readString(document, "leagueName"), "Bronze League"),
                                safeDisplay(readString(document, "region"), "Nepoznat region"),
                                stars,
                                document.getId().equals(currentUid)
                        ));
                    }

                    candidates.sort((first, second) -> {
                        int byStars = Integer.compare(second.getStars(), first.getStars());
                        if (byStars != 0) {
                            return byStars;
                        }
                        return first.getUsername().compareToIgnoreCase(second.getUsername());
                    });

                    List<LeaderboardEntry> ranked = new ArrayList<>();
                    for (int index = 0; index < candidates.size(); index++) {
                        LeaderboardEntry entry = candidates.get(index);
                        ranked.add(new LeaderboardEntry(
                                index + 1,
                                entry.getPlayerId(),
                                entry.getUsername(),
                                entry.getLeagueName(),
                                entry.getRegion(),
                                entry.getStars(),
                                entry.isCurrentUser()
                        ));
                    }
                    onSuccess.onSuccess(cycleInfo, ranked);
                })
                .addOnFailureListener(e -> {
                    String errorClass = e.getClass().getName();
                    String errorMessage = e.getMessage();

                    android.util.Log.e("LeaderboardService", "loadPlayerLeaderboard failed", e);

                    onFailure.onFailure(errorClass + ": " + errorMessage);
                });
    }

    public void loadRegionLeaderboard(RegionLeaderboardCallback onSuccess,
                                      FailureCallback onFailure) {
        loadActiveCycle(
                LeaderboardCycle.Type.MONTHLY,
                cycleInfo -> loadRegionLeaderboardForCycle(cycleInfo, onSuccess, onFailure),
                onFailure
        );
    }

    private void loadRegionLeaderboardForCycle(LeaderboardCycle cycleInfo,
                                               RegionLeaderboardCallback onSuccess,
                                               FailureCallback onFailure) {
        FirebaseUser currentUser = auth.getCurrentUser();
        String currentUid = currentUser == null ? null : currentUser.getUid();

        db.collection("regions")
                .whereEqualTo("active", true)
                .get()
                .addOnSuccessListener(regionSnapshot -> loadRegionLeaderboardWithCatalog(
                        cycleInfo,
                        currentUid,
                        regionSnapshot.getDocuments(),
                        onSuccess,
                        onFailure
                ))
                .addOnFailureListener(e -> onFailure.onFailure(messageOrDefault(e, "Nije moguće učitati regione.")));
    }

    private void loadActiveCycle(LeaderboardCycle.Type cycleType,
                                 LeaderboardCycleCallback onSuccess,
                                 FailureCallback onFailure) {
        db.collection(LeaderboardCycle.COLLECTION_NAME)
                .whereEqualTo("type", cycleType.getFirestoreValue())
                .whereEqualTo("status", LeaderboardCycle.Status.ACTIVE.getFirestoreValue())
                .limit(2)
                .get()
                .addOnSuccessListener(snapshot -> {
                    if (snapshot.size() > 1) {
                        onFailure.onFailure(
                                "Postoji više aktivnih " + cycleType.getFirestoreValue() + " ciklusa."
                        );
                        return;
                    }
                    if (snapshot.isEmpty()) {
                        onSuccess.onSuccess(LeaderboardCycle.currentFallback(cycleType));
                        return;
                    }

                    DocumentSnapshot document = snapshot.getDocuments().get(0);
                    String cycleId = readString(document, "cycleId");
                    if (cycleId == null || cycleId.trim().isEmpty()) {
                        cycleId = document.getId();
                    }

                    try {
                        onSuccess.onSuccess(LeaderboardCycle.fromFirestore(
                                document.getId(),
                                cycleId,
                                readString(document, "type"),
                                readString(document, "status"),
                                readString(document, "startDate"),
                                readString(document, "endDate")
                        ));
                    } catch (IllegalArgumentException exception) {
                        onFailure.onFailure("Neispravan dokument aktivnog ciklusa: " + exception.getMessage());
                    }
                })
                .addOnFailureListener(e -> onFailure.onFailure(messageOrDefault(e, "Nije moguće učitati ciklus rang liste.")));
    }

    private void loadRegionLeaderboardWithCatalog(LeaderboardCycle cycleInfo,
                                                  String currentUid,
                                                  List<DocumentSnapshot> regionDocuments,
                                                  RegionLeaderboardCallback onSuccess,
                                                  FailureCallback onFailure) {
        Map<String, RegionStats> statsByRegion = new HashMap<>();
        for (DocumentSnapshot regionDocument : regionDocuments) {
            String regionId = readString(regionDocument, "regionId");
            if (regionId == null || regionId.trim().isEmpty()) {
                regionId = regionDocument.getId();
            }
            String regionKey = regionId.trim().toLowerCase(Locale.ROOT);
            String displayName = safeDisplay(readString(regionDocument, "displayName"), regionId);
            String iconKey = readString(regionDocument, "iconKey");
            RegionStats stats = statsByRegion.computeIfAbsent(regionKey, ignored -> new RegionStats(regionKey, displayName, iconKey));
            stats.displayName = displayName;
            stats.iconKey = iconKey;
            stats.firstPlaceCount = readInt(regionDocument, "firstPlaceCount");
            stats.secondPlaceCount = readInt(regionDocument, "secondPlaceCount");
            stats.thirdPlaceCount = readInt(regionDocument, "thirdPlaceCount");
        }

        db.collection("players")
                .get()
                .addOnSuccessListener(snapshot -> {
                    String currentUserRegionKey = null;

                    for (DocumentSnapshot document : snapshot.getDocuments()) {
                        String region = safeDisplay(readString(document, "region"), "Nepoznat region");
                        String regionKey = regionKeyForPlayer(document, region);
                        String iconKey = readString(document, "regionIconKey");
                        RegionStats stats = statsByRegion.computeIfAbsent(regionKey, ignored -> new RegionStats(regionKey, region, iconKey));
                        stats.registeredPlayers++;
                        if (stats.iconKey == null || stats.iconKey.trim().isEmpty()) {
                            stats.iconKey = iconKey;
                        }
                        GeoPoint mapPoint = document.getGeoPoint("mapPoint");
                        if (mapPoint != null) {
                            stats.mapPoints.add(mapPoint);
                        }

                        String monthlyCycleId = readString(document, "monthlyCycleId");
                        int monthlyGames = readInt(document, "monthlyGames");
                        if (cycleInfo.getCycleId().equals(monthlyCycleId) && monthlyGames > 0) {
                            stats.activePlayers++;
                            stats.monthlyStars += readInt(document, "monthlyStars");
                        }

                        if (document.getId().equals(currentUid)) {
                            currentUserRegionKey = regionKey;
                        }
                    }

                    List<RegionStats> statsList = new ArrayList<>(statsByRegion.values());
                    statsList.sort((first, second) -> {
                        int byStars = Integer.compare(second.monthlyStars, first.monthlyStars);
                        if (byStars != 0) {
                            return byStars;
                        }
                        return first.displayName.compareToIgnoreCase(second.displayName);
                    });

                    List<RegionLeaderboardEntry> entries = new ArrayList<>();
                    for (int index = 0; index < statsList.size(); index++) {
                        RegionStats stats = statsList.get(index);
                        entries.add(new RegionLeaderboardEntry(
                                index + 1,
                                stats.displayName,
                                Region.iconResIdForKey(stats.iconKey),
                                stats.monthlyStars,
                                stats.firstPlaceCount,
                                stats.secondPlaceCount,
                                stats.thirdPlaceCount,
                                stats.activePlayers,
                                stats.registeredPlayers,
                                stats.mapPoints,
                                stats.regionKey.equals(currentUserRegionKey)
                        ));
                    }

                    onSuccess.onSuccess(cycleInfo, entries);
                })
                .addOnFailureListener(e -> onFailure.onFailure(messageOrDefault(e, "Nije moguće učitati regione.")));
    }

    private int readInt(DocumentSnapshot document, String field) {
        Object value = document.get(field);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return 0;
    }

    private String readString(DocumentSnapshot document, String field) {
        String value = document.getString(field);
        return value == null ? "" : value;
    }

    private String safeDisplay(String value, String fallback) {
        if (value == null || value.trim().isEmpty()) {
            return fallback;
        }
        return value.trim();
    }

    private String normalizeRegion(String region) {
        return safeDisplay(region, "Nepoznat region").toLowerCase(Locale.ROOT);
    }

    private String regionKeyForPlayer(DocumentSnapshot document, String region) {
        String regionId = readString(document, "regionId");
        if (regionId != null && !regionId.trim().isEmpty()) {
            return regionId.trim().toLowerCase(Locale.ROOT);
        }
        return normalizeRegion(region);
    }

    private String messageOrDefault(Exception exception, String fallback) {
        return exception.getMessage() == null ? fallback : exception.getMessage();
    }

    private interface LeaderboardCycleCallback {
        void onSuccess(LeaderboardCycle cycle);
    }

    private static final class RegionStats {
        private final String regionKey;
        private String displayName;
        private String iconKey;
        private int monthlyStars;
        private int firstPlaceCount;
        private int secondPlaceCount;
        private int thirdPlaceCount;
        private int activePlayers;
        private int registeredPlayers;
        private final List<GeoPoint> mapPoints;

        private RegionStats(String regionKey, String displayName, String iconKey) {
            this.regionKey = regionKey;
            this.displayName = displayName;
            this.iconKey = iconKey;
            this.mapPoints = new ArrayList<>();
        }
    }

}
