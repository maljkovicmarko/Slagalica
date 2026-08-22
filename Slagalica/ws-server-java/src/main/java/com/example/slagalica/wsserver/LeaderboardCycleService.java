package com.example.slagalica.wsserver;

import com.example.slagalica.Model.LeaderboardCycle;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.QueryDocumentSnapshot;
import com.google.cloud.firestore.SetOptions;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class LeaderboardCycleService {
    private static final String PLAYERS_COLLECTION = "players";
    private static final String REGIONS_COLLECTION = "regions";
    private static final String PLAYER_RESULTS_COLLECTION = "playerResults";
    private static final String REGION_RESULTS_COLLECTION = "regionResults";
    private static final String REWARD_NOTIFICATIONS_COLLECTION = "rewardNotifications";

    public synchronized ActiveCycles ensureCurrentCycles() throws Exception {
        Firestore firestore = FirebaseAdmin.getFirestore();
        LocalDate today = LocalDate.now(LeaderboardCycle.COMPETITION_ZONE);

        LeaderboardCycle weekly = ensureCurrentCycle(firestore, LeaderboardCycle.Type.WEEKLY, today);
        LeaderboardCycle monthly = ensureCurrentCycle(firestore, LeaderboardCycle.Type.MONTHLY, today);
        return new ActiveCycles(weekly, monthly);
    }

    public ActiveCycles getRequiredCurrentCycles() throws Exception {
        return ensureCurrentCycles();
    }

    private LeaderboardCycle ensureCurrentCycle(Firestore firestore,
                                                LeaderboardCycle.Type type,
                                                LocalDate today) throws Exception {
        LeaderboardCycle interrupted = loadSingleCycleByStatus(
                firestore,
                type,
                LeaderboardCycle.Status.FINALIZING
        );
        if (interrupted != null) {
            finalizeCycle(firestore, interrupted);
        }

        LeaderboardCycle activeCycle = loadSingleCycleByStatus(
                firestore,
                type,
                LeaderboardCycle.Status.ACTIVE
        );
        if (activeCycle == null) {
            return createCurrentCycle(firestore, type, today);
        }
        if (activeCycle.contains(today)) {
            return activeCycle;
        }
        if (activeCycle.getStartDate().isAfter(today)) {
            throw new IllegalStateException(
                    "Active " + type.getFirestoreValue() + " cycle " + activeCycle.getCycleId()
                            + " starts in the future on " + activeCycle.getStartDate() + "."
            );
        }

        LeaderboardCycle claimedCycle = claimForFinalization(firestore, activeCycle);
        if (claimedCycle != null) {
            finalizeCycle(firestore, claimedCycle);
        }
        return createCurrentCycle(firestore, type, today);
    }

    private LeaderboardCycle loadSingleCycleByStatus(Firestore firestore,
                                                     LeaderboardCycle.Type type,
                                                     LeaderboardCycle.Status status) throws Exception {
        List<QueryDocumentSnapshot> documents = firestore
                .collection(LeaderboardCycle.COLLECTION_NAME)
                .whereEqualTo("type", type.getFirestoreValue())
                .whereEqualTo("status", status.getFirestoreValue())
                .limit(2)
                .get()
                .get()
                .getDocuments();

        if (documents.size() > 1) {
            throw new IllegalStateException(
                    "More than one " + status.getFirestoreValue() + " " + type.getFirestoreValue()
                            + " leaderboard cycle exists."
            );
        }
        return documents.isEmpty() ? null : mapCycle(documents.get(0));
    }

    private LeaderboardCycle claimForFinalization(Firestore firestore,
                                                  LeaderboardCycle cycle) throws Exception {
        DocumentReference cycleReference = cycleReference(firestore, cycle);
        return firestore.runTransaction(transaction -> {
            DocumentSnapshot latest = transaction.get(cycleReference).get();
            if (!latest.exists()) {
                throw new IllegalStateException("Leaderboard cycle no longer exists: " + cycle.getCycleId());
            }

            String currentStatus = latest.getString("status");
            if (LeaderboardCycle.Status.FINALIZED.getFirestoreValue().equals(currentStatus)) {
                return null;
            }
            if (!LeaderboardCycle.Status.ACTIVE.getFirestoreValue().equals(currentStatus)
                    && !LeaderboardCycle.Status.FINALIZING.getFirestoreValue().equals(currentStatus)) {
                throw new IllegalStateException(
                        "Cannot finalize cycle " + cycle.getCycleId() + " from status " + currentStatus
                );
            }

            Map<String, Object> updates = new HashMap<>();
            updates.put("status", LeaderboardCycle.Status.FINALIZING.getFirestoreValue());
            updates.put("finalizingStartedAtMs", System.currentTimeMillis());
            transaction.set(cycleReference, updates, SetOptions.merge());
            return withStatus(mapCycle(latest), LeaderboardCycle.Status.FINALIZING);
        }).get();
    }

    private void finalizeCycle(Firestore firestore, LeaderboardCycle cycle) throws Exception {
        System.out.println("Finalizing " + cycle.getType().getFirestoreValue() + " cycle " + cycle.getCycleId());
        List<QueryDocumentSnapshot> players = firestore.collection(PLAYERS_COLLECTION).get().get().getDocuments();
        List<PlayerStanding> playerStandings = buildPlayerStandings(players, cycle);

        for (PlayerStanding standing : playerStandings) {
            writePlayerResultIfMissing(firestore, cycle, standing);
        }
        for (PlayerStanding standing : playerStandings) {
            applyPlayerReward(firestore, cycle, standing);
        }

        if (cycle.getType() == LeaderboardCycle.Type.MONTHLY) {
            finalizeRegionResults(firestore, cycle, players);
        }

        long completedAtMs = System.currentTimeMillis();
        Map<String, Object> updates = new HashMap<>();
        updates.put("status", LeaderboardCycle.Status.FINALIZED.getFirestoreValue());
        updates.put("finalizedAtMs", completedAtMs);
        updates.put("rewardsDistributedAtMs", completedAtMs);
        cycleReference(firestore, cycle).set(updates, SetOptions.merge()).get();
        System.out.println("Finalized cycle " + cycle.getCycleId() + " with " + playerStandings.size() + " players");
    }

    private List<PlayerStanding> buildPlayerStandings(List<QueryDocumentSnapshot> players,
                                                      LeaderboardCycle cycle) {
        String cycleIdField = cycle.getType() == LeaderboardCycle.Type.WEEKLY
                ? "weeklyCycleId"
                : "monthlyCycleId";
        String starsField = cycle.getType() == LeaderboardCycle.Type.WEEKLY
                ? "weeklyStars"
                : "monthlyStars";
        String gamesField = cycle.getType() == LeaderboardCycle.Type.WEEKLY
                ? "weeklyGames"
                : "monthlyGames";

        List<PlayerStanding> standings = new ArrayList<>();
        for (QueryDocumentSnapshot player : players) {
            if (!cycle.getCycleId().equals(player.getString(cycleIdField))) {
                continue;
            }
            int games = readInt(player, gamesField);
            if (games <= 0) {
                continue;
            }
            standings.add(new PlayerStanding(
                    player.getId(),
                    safeString(player.getString("username"), "Igrač"),
                    safeString(player.getString("leagueName"), "Bronze League"),
                    safeString(player.getString("regionId"), "unknown"),
                    safeString(player.getString("region"), "Nepoznat region"),
                    readInt(player, starsField),
                    games
            ));
        }

        standings.sort((first, second) -> {
            int byStars = Integer.compare(second.stars, first.stars);
            if (byStars != 0) {
                return byStars;
            }
            int byUsername = first.username.compareToIgnoreCase(second.username);
            return byUsername != 0 ? byUsername : first.playerId.compareTo(second.playerId);
        });
        for (int index = 0; index < standings.size(); index++) {
            standings.get(index).rank = index + 1;
            standings.get(index).rewardTokens = rewardTokens(cycle.getType(), index + 1);
        }
        return standings;
    }

    private void writePlayerResultIfMissing(Firestore firestore,
                                            LeaderboardCycle cycle,
                                            PlayerStanding standing) throws Exception {
        DocumentReference resultReference = playerResultReference(firestore, cycle, standing.playerId);
        if (resultReference.get().get().exists()) {
            return;
        }

        Map<String, Object> fields = new HashMap<>();
        fields.put("cycleId", cycle.getCycleId());
        fields.put("cycleType", cycle.getType().getFirestoreValue());
        fields.put("playerId", standing.playerId);
        fields.put("username", standing.username);
        fields.put("leagueName", standing.leagueName);
        fields.put("regionId", standing.regionId);
        fields.put("region", standing.region);
        fields.put("rank", standing.rank);
        fields.put("stars", standing.stars);
        fields.put("games", standing.games);
        fields.put("rewardTokens", standing.rewardTokens);
        fields.put("rewardApplied", false);
        fields.put("createdAtMs", System.currentTimeMillis());
        resultReference.set(fields).get();
    }

    private void applyPlayerReward(Firestore firestore,
                                   LeaderboardCycle cycle,
                                   PlayerStanding standing) throws Exception {
        DocumentReference resultReference = playerResultReference(firestore, cycle, standing.playerId);
        DocumentReference playerReference = firestore.collection(PLAYERS_COLLECTION).document(standing.playerId);
        DocumentReference notificationReference = playerReference
                .collection(REWARD_NOTIFICATIONS_COLLECTION)
                .document(safeDocumentId(cycle.getCycleId()));

        firestore.runTransaction(transaction -> {
            DocumentSnapshot result = transaction.get(resultReference).get();
            DocumentSnapshot player = transaction.get(playerReference).get();
            if (!result.exists()) {
                throw new IllegalStateException("Missing player result for " + standing.playerId);
            }
            if (Boolean.TRUE.equals(result.getBoolean("rewardApplied"))) {
                return null;
            }
            if (!player.exists()) {
                throw new IllegalStateException("Player no longer exists: " + standing.playerId);
            }

            long appliedAtMs = System.currentTimeMillis();
            if (standing.rewardTokens > 0) {
                Map<String, Object> playerUpdates = new HashMap<>();
                playerUpdates.put("tokens", readInt(player, "tokens") + standing.rewardTokens);
                transaction.set(playerReference, playerUpdates, SetOptions.merge());

                Map<String, Object> notification = new HashMap<>();
                notification.put("cycleDocumentId", cycle.getDocumentId());
                notification.put("cycleId", cycle.getCycleId());
                notification.put("cycleType", cycle.getType().getFirestoreValue());
                notification.put("rank", standing.rank);
                notification.put("tokens", standing.rewardTokens);
                notification.put("createdAtMs", appliedAtMs);
                notification.put("seen", false);
                transaction.set(notificationReference, notification);
            }

            Map<String, Object> resultUpdates = new HashMap<>();
            resultUpdates.put("rewardApplied", true);
            resultUpdates.put("rewardAppliedAtMs", appliedAtMs);
            transaction.set(resultReference, resultUpdates, SetOptions.merge());
            return null;
        }).get();
    }

    private void finalizeRegionResults(Firestore firestore,
                                       LeaderboardCycle cycle,
                                       List<QueryDocumentSnapshot> players) throws Exception {
        List<QueryDocumentSnapshot> regionDocuments = firestore
                .collection(REGIONS_COLLECTION)
                .whereEqualTo("active", true)
                .get()
                .get()
                .getDocuments();

        Map<String, RegionStanding> standingsByRegion = new LinkedHashMap<>();
        Map<String, DocumentReference> regionReferences = new HashMap<>();
        for (QueryDocumentSnapshot region : regionDocuments) {
            String regionId = safeString(region.getString("regionId"), region.getId());
            String regionKey = normalizeKey(regionId);
            standingsByRegion.put(regionKey, new RegionStanding(
                    regionId,
                    safeString(region.getString("displayName"), regionId)
            ));
            regionReferences.put(regionKey, region.getReference());
        }

        for (QueryDocumentSnapshot player : players) {
            String regionId = safeString(player.getString("regionId"), "unknown");
            String regionKey = normalizeKey(regionId);
            RegionStanding standing = standingsByRegion.computeIfAbsent(
                    regionKey,
                    ignored -> new RegionStanding(
                            regionId,
                            safeString(player.getString("region"), "Nepoznat region")
                    )
            );
            standing.registeredPlayers++;
            if (cycle.getCycleId().equals(player.getString("monthlyCycleId"))
                    && readInt(player, "monthlyGames") > 0) {
                standing.activePlayers++;
                standing.stars += readInt(player, "monthlyStars");
            }
        }

        List<RegionStanding> standings = new ArrayList<>(standingsByRegion.values());
        standings.sort((first, second) -> {
            int byStars = Integer.compare(second.stars, first.stars);
            if (byStars != 0) {
                return byStars;
            }
            int byName = first.displayName.compareToIgnoreCase(second.displayName);
            return byName != 0 ? byName : first.regionId.compareTo(second.regionId);
        });

        for (int index = 0; index < standings.size(); index++) {
            RegionStanding standing = standings.get(index);
            standing.rank = index + 1;
            writeRegionResultIfMissing(firestore, cycle, standing);
        }
        for (RegionStanding standing : standings) {
            applyRegionPlacement(
                    firestore,
                    cycle,
                    standing,
                    regionReferences.get(normalizeKey(standing.regionId))
            );
        }
    }

    private void writeRegionResultIfMissing(Firestore firestore,
                                            LeaderboardCycle cycle,
                                            RegionStanding standing) throws Exception {
        DocumentReference resultReference = regionResultReference(firestore, cycle, standing.regionId);
        if (resultReference.get().get().exists()) {
            return;
        }

        Map<String, Object> fields = new HashMap<>();
        fields.put("cycleId", cycle.getCycleId());
        fields.put("regionId", standing.regionId);
        fields.put("displayName", standing.displayName);
        fields.put("rank", standing.rank);
        fields.put("stars", standing.stars);
        fields.put("activePlayers", standing.activePlayers);
        fields.put("registeredPlayers", standing.registeredPlayers);
        fields.put("placementApplied", standing.rank > 3 || standing.activePlayers <= 0);
        fields.put("createdAtMs", System.currentTimeMillis());
        resultReference.set(fields).get();
    }

    private void applyRegionPlacement(Firestore firestore,
                                      LeaderboardCycle cycle,
                                      RegionStanding standing,
                                      DocumentReference regionReference) throws Exception {
        if (standing.rank > 3 || standing.activePlayers <= 0) {
            return;
        }

        DocumentReference resultReference = regionResultReference(firestore, cycle, standing.regionId);
        firestore.runTransaction(transaction -> {
            DocumentSnapshot result = transaction.get(resultReference).get();
            DocumentSnapshot region = regionReference == null ? null : transaction.get(regionReference).get();
            if (!result.exists() || Boolean.TRUE.equals(result.getBoolean("placementApplied"))) {
                return null;
            }

            if (region != null && region.exists()) {
                String placementField = placementField(standing.rank);
                Map<String, Object> regionUpdates = new HashMap<>();
                regionUpdates.put(placementField, readInt(region, placementField) + 1);
                transaction.set(regionReference, regionUpdates, SetOptions.merge());
            }

            Map<String, Object> resultUpdates = new HashMap<>();
            resultUpdates.put("placementApplied", true);
            resultUpdates.put("placementAppliedAtMs", System.currentTimeMillis());
            transaction.set(resultReference, resultUpdates, SetOptions.merge());
            return null;
        }).get();
    }

    private LeaderboardCycle createCurrentCycle(Firestore firestore,
                                                LeaderboardCycle.Type type,
                                                LocalDate today) throws Exception {
        LeaderboardCycle generated = LeaderboardCycle.forDate(type, today);
        List<QueryDocumentSnapshot> existingDocuments = firestore
                .collection(LeaderboardCycle.COLLECTION_NAME)
                .whereEqualTo("cycleId", generated.getCycleId())
                .limit(1)
                .get()
                .get()
                .getDocuments();

        if (!existingDocuments.isEmpty()) {
            throw new IllegalStateException(
                    "Cycle " + generated.getCycleId() + " already exists but is not the active "
                            + type.getFirestoreValue() + " cycle."
            );
        }

        DocumentReference reference = firestore.collection(LeaderboardCycle.COLLECTION_NAME).document();
        Map<String, Object> fields = new HashMap<>();
        fields.put("cycleId", generated.getCycleId());
        fields.put("type", generated.getType().getFirestoreValue());
        fields.put("status", generated.getStatus().getFirestoreValue());
        fields.put("startDate", generated.getStartDate().toString());
        fields.put("endDate", generated.getEndDate().toString());
        reference.set(fields).get();

        System.out.println("Created " + type.getFirestoreValue() + " leaderboard cycle " + generated.getCycleId());
        return new LeaderboardCycle(
                reference.getId(),
                generated.getCycleId(),
                generated.getType(),
                generated.getStatus(),
                generated.getStartDate(),
                generated.getEndDate()
        );
    }

    private DocumentReference cycleReference(Firestore firestore, LeaderboardCycle cycle) {
        if (cycle.getDocumentId() == null || cycle.getDocumentId().isBlank()) {
            throw new IllegalArgumentException("Cycle Firestore document id is required");
        }
        return firestore.collection(LeaderboardCycle.COLLECTION_NAME).document(cycle.getDocumentId());
    }

    private DocumentReference playerResultReference(Firestore firestore,
                                                    LeaderboardCycle cycle,
                                                    String playerId) {
        return cycleReference(firestore, cycle)
                .collection(PLAYER_RESULTS_COLLECTION)
                .document(playerId);
    }

    private DocumentReference regionResultReference(Firestore firestore,
                                                    LeaderboardCycle cycle,
                                                    String regionId) {
        return cycleReference(firestore, cycle)
                .collection(REGION_RESULTS_COLLECTION)
                .document(safeDocumentId(regionId));
    }

    private LeaderboardCycle mapCycle(DocumentSnapshot document) {
        return LeaderboardCycle.fromFirestore(
                document.getId(),
                document.getString("cycleId"),
                document.getString("type"),
                document.getString("status"),
                document.getString("startDate"),
                document.getString("endDate")
        );
    }

    private LeaderboardCycle withStatus(LeaderboardCycle cycle, LeaderboardCycle.Status status) {
        return new LeaderboardCycle(
                cycle.getDocumentId(),
                cycle.getCycleId(),
                cycle.getType(),
                status,
                cycle.getStartDate(),
                cycle.getEndDate()
        );
    }

    private int rewardTokens(LeaderboardCycle.Type type, int rank) {
        if (rank == 1) {
            return type == LeaderboardCycle.Type.WEEKLY ? 5 : 10;
        }
        if (rank == 2) {
            return type == LeaderboardCycle.Type.WEEKLY ? 3 : 6;
        }
        if (rank == 3) {
            return type == LeaderboardCycle.Type.WEEKLY ? 2 : 4;
        }
        if (rank <= 10) {
            return type == LeaderboardCycle.Type.WEEKLY ? 1 : 2;
        }
        return 0;
    }

    private String placementField(int rank) {
        if (rank == 1) {
            return "firstPlaceCount";
        }
        if (rank == 2) {
            return "secondPlaceCount";
        }
        return "thirdPlaceCount";
    }

    private int readInt(DocumentSnapshot document, String field) {
        Object value = document == null ? null : document.get(field);
        return value instanceof Number ? ((Number) value).intValue() : 0;
    }

    private String safeString(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value.trim();
    }

    private String normalizeKey(String value) {
        return safeString(value, "unknown").toLowerCase(Locale.ROOT);
    }

    private String safeDocumentId(String value) {
        return safeString(value, "unknown").replace('/', '_');
    }

    public static final class ActiveCycles {
        private final LeaderboardCycle weekly;
        private final LeaderboardCycle monthly;

        private ActiveCycles(LeaderboardCycle weekly, LeaderboardCycle monthly) {
            this.weekly = weekly;
            this.monthly = monthly;
        }

        public LeaderboardCycle getWeekly() {
            return weekly;
        }

        public LeaderboardCycle getMonthly() {
            return monthly;
        }
    }

    private static final class PlayerStanding {
        private final String playerId;
        private final String username;
        private final String leagueName;
        private final String regionId;
        private final String region;
        private final int stars;
        private final int games;
        private int rank;
        private int rewardTokens;

        private PlayerStanding(String playerId,
                               String username,
                               String leagueName,
                               String regionId,
                               String region,
                               int stars,
                               int games) {
            this.playerId = playerId;
            this.username = username;
            this.leagueName = leagueName;
            this.regionId = regionId;
            this.region = region;
            this.stars = stars;
            this.games = games;
        }
    }

    private static final class RegionStanding {
        private final String regionId;
        private final String displayName;
        private int stars;
        private int activePlayers;
        private int registeredPlayers;
        private int rank;

        private RegionStanding(String regionId, String displayName) {
            this.regionId = regionId;
            this.displayName = displayName;
        }
    }
}
