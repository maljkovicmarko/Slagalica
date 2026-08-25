package com.example.slagalica.wsserver;

import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.QueryDocumentSnapshot;
import com.google.cloud.firestore.QuerySnapshot;
import com.google.cloud.firestore.SetOptions;
import org.json.JSONArray;
import org.json.JSONObject;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Server-authoritative regional challenge lifecycle and escrow economy.
 *
 * Stakes are removed when a player joins.  The displayed winner is provisional
 * until the challenge is closed and every accepted run is complete.  Settlement
 * is persisted on the challenge document so retries cannot pay twice.
 */
public class RegionalChallengeService {
    public static final String STATUS_OPEN = "open";
    public static final String STATUS_CLOSED = "closed";
    public static final String STATUS_FINISHED = "finished";
    public static final String STATUS_CANCELLED = "cancelled";

    private static final String CHALLENGES_COLLECTION = "regional_challenges";
    private static final String PLAYERS_COLLECTION = "players";
    private static final String TOKENS_FIELD = "tokens";
    private static final String STARS_FIELD = "totalStars";
    private static final int MAX_STARS = 10;
    private static final int MAX_TOKENS = 2;
    private static final int MAX_PLAYERS = 4;
    private static final long ACCEPTANCE_WINDOW_MS = 5 * 60_000L;
    private static final long RUN_WINDOW_MS = 20 * 60_000L;
    private static final long RECENT_FINISHED_WINDOW_MS = 24 * 60 * 60_000L;

    private final SecureRandom secureRandom = new SecureRandom();
    private final ChallengeChangeListener changeListener;

    public RegionalChallengeService(ChallengeChangeListener changeListener) {
        this.changeListener = changeListener;
    }

    public JSONObject listChallenges(String uid) throws Exception {
        sweepExpiredChallenges();
        Firestore firestore = FirebaseAdmin.getFirestore();
        QuerySnapshot snapshot = firestore.collection(CHALLENGES_COLLECTION).get().get();
        List<Map<String, Object>> challenges = new ArrayList<>();
        long visibleAfterMs = System.currentTimeMillis() - RECENT_FINISHED_WINDOW_MS;
        for (QueryDocumentSnapshot document : snapshot.getDocuments()) {
            Map<String, Object> data = copy(document.getData());
            data.put("challengeId", document.getId());
            String status = stringValue(data.get("status"));
            boolean participant = participant(data, uid) != null;
            long finishedAtMs = longValue(data.get("finishedAtMs"));
            if (STATUS_OPEN.equals(status)
                    || STATUS_CLOSED.equals(status)
                    || participant
                    || finishedAtMs >= visibleAfterMs) {
                challenges.add(data);
            }
        }
        challenges.sort(Comparator.comparingLong((Map<String, Object> item) -> longValue(item.get("createdAtMs"))).reversed());

        JSONArray array = new JSONArray();
        for (Map<String, Object> challenge : challenges) {
            array.put(toPublicJson(challenge, uid));
        }
        JSONObject result = new JSONObject();
        result.put("challenges", array);
        result.put("serverNowMs", System.currentTimeMillis());
        return result;
    }

    public JSONObject createChallenge(String uid, int stakeStars, int stakeTokens) throws Exception {
        validateStake(stakeStars, stakeTokens);
        Firestore firestore = FirebaseAdmin.getFirestore();
        DocumentReference challengeRef = firestore.collection(CHALLENGES_COLLECTION).document();
        DocumentReference playerRef = firestore.collection(PLAYERS_COLLECTION).document(uid);
        long nowMs = System.currentTimeMillis();

        Map<String, Object> stored = firestore.runTransaction(transaction -> {
            DocumentSnapshot player = transaction.get(playerRef).get();
            requirePlayerAndBalance(player, stakeStars, stakeTokens);

            String username = player.getString("username");
            String regionId = player.getString("regionId");
            Map<String, Object> participant = newParticipant(uid, username, 0, nowMs);
            List<Map<String, Object>> participants = new ArrayList<>();
            participants.add(participant);

            Map<String, Object> challenge = new HashMap<>();
            challenge.put("challengeId", challengeRef.getId());
            challenge.put("creatorUid", uid);
            challenge.put("creatorUsername", safeName(username));
            challenge.put("creatorRegionId", regionId == null ? "" : regionId);
            challenge.put("status", STATUS_OPEN);
            challenge.put("stakeStars", stakeStars);
            challenge.put("stakeTokens", stakeTokens);
            challenge.put("maxPlayers", MAX_PLAYERS);
            challenge.put("participantCount", 1);
            challenge.put("participants", participants);
            challenge.put("contentSeed", secureRandom.nextLong());
            challenge.put("createdAtMs", nowMs);
            challenge.put("acceptanceDeadlineAtMs", nowMs + ACCEPTANCE_WINDOW_MS);
            challenge.put("runDeadlineAtMs", 0L);
            challenge.put("closedAtMs", 0L);
            challenge.put("finishedAtMs", 0L);
            challenge.put("settlementApplied", false);
            challenge.put("provisionalWinnerUid", "");
            challenge.put("provisionalRunnerUpUid", "");
            challenge.put("winnerUid", "");
            challenge.put("runnerUpUid", "");

            transaction.update(playerRef, STARS_FIELD, intValue(player.get(STARS_FIELD)) - stakeStars,
                    TOKENS_FIELD, intValue(player.get(TOKENS_FIELD)) - stakeTokens);
            transaction.set(challengeRef, challenge);
            return challenge;
        }).get();

        JSONObject json = toPublicJson(stored, uid);
        notifyChanged(json);
        return json;
    }

    public JSONObject acceptChallenge(String uid, String challengeId) throws Exception {
        Firestore firestore = FirebaseAdmin.getFirestore();
        DocumentReference challengeRef = challengeRef(firestore, challengeId);
        DocumentReference playerRef = firestore.collection(PLAYERS_COLLECTION).document(uid);
        long nowMs = System.currentTimeMillis();

        Map<String, Object> stored = firestore.runTransaction(transaction -> {
            DocumentSnapshot challengeSnapshot = transaction.get(challengeRef).get();
            if (!challengeSnapshot.exists()) {
                throw new IllegalStateException("Izazov ne postoji.");
            }
            Map<String, Object> challenge = copy(challengeSnapshot.getData());
            challenge.put("challengeId", challengeSnapshot.getId());
            if (!STATUS_OPEN.equals(stringValue(challenge.get("status")))) {
                throw new IllegalStateException("Prijave za ovaj izazov su završene.");
            }
            if (nowMs > longValue(challenge.get("acceptanceDeadlineAtMs"))) {
                throw new IllegalStateException("Vreme za prihvatanje izazova je isteklo.");
            }
            List<Map<String, Object>> participants = participants(challenge);
            if (findParticipant(participants, uid) != null) {
                throw new IllegalStateException("Već učestvuješ u ovom izazovu.");
            }
            if (participants.size() >= MAX_PLAYERS) {
                throw new IllegalStateException("Izazov već ima maksimalan broj igrača.");
            }

            int stakeStars = intValue(challenge.get("stakeStars"));
            int stakeTokens = intValue(challenge.get("stakeTokens"));
            DocumentSnapshot player = transaction.get(playerRef).get();
            requirePlayerAndBalance(player, stakeStars, stakeTokens);

            participants.add(newParticipant(uid, player.getString("username"), participants.size(), nowMs));
            challenge.put("participants", participants);
            challenge.put("participantCount", participants.size());
            if (participants.size() >= MAX_PLAYERS) {
                challenge.put("status", STATUS_CLOSED);
                challenge.put("closedAtMs", nowMs);
                challenge.put("runDeadlineAtMs", nowMs + RUN_WINDOW_MS);
            }
            updateProvisionalRanking(challenge);

            transaction.update(playerRef, STARS_FIELD, intValue(player.get(STARS_FIELD)) - stakeStars,
                    TOKENS_FIELD, intValue(player.get(TOKENS_FIELD)) - stakeTokens);
            transaction.set(challengeRef, challenge, SetOptions.merge());
            return challenge;
        }).get();

        JSONObject json = toPublicJson(stored, uid);
        notifyChanged(json);
        return json;
    }

    public JSONObject closeChallenge(String uid, String challengeId) throws Exception {
        Firestore firestore = FirebaseAdmin.getFirestore();
        DocumentReference ref = challengeRef(firestore, challengeId);
        long nowMs = System.currentTimeMillis();
        Map<String, Object> stored = firestore.runTransaction(transaction -> {
            DocumentSnapshot snapshot = transaction.get(ref).get();
            if (!snapshot.exists()) {
                throw new IllegalStateException("Izazov ne postoji.");
            }
            Map<String, Object> challenge = copy(snapshot.getData());
            challenge.put("challengeId", snapshot.getId());
            if (!uid.equals(stringValue(challenge.get("creatorUid")))) {
                throw new IllegalStateException("Samo autor može zatvoriti prijave.");
            }
            if (!STATUS_OPEN.equals(stringValue(challenge.get("status")))) {
                throw new IllegalStateException("Prijave su već zatvorene.");
            }
            if (participants(challenge).size() < 2) {
                throw new IllegalStateException("Potreban je najmanje još jedan igrač.");
            }
            challenge.put("status", STATUS_CLOSED);
            challenge.put("closedAtMs", nowMs);
            challenge.put("runDeadlineAtMs", nowMs + RUN_WINDOW_MS);
            transaction.set(ref, challenge, SetOptions.merge());
            return challenge;
        }).get();
        JSONObject json = toPublicJson(stored, uid);
        notifyChanged(json);
        settleIfReady(challengeId);
        return json;
    }

    public JSONObject cancelChallenge(String uid, String challengeId) throws Exception {
        Firestore firestore = FirebaseAdmin.getFirestore();
        DocumentReference ref = challengeRef(firestore, challengeId);
        long nowMs = System.currentTimeMillis();
        Map<String, Object> stored = firestore.runTransaction(transaction -> {
            DocumentSnapshot snapshot = transaction.get(ref).get();
            if (!snapshot.exists()) {
                throw new IllegalStateException("Izazov ne postoji.");
            }
            Map<String, Object> challenge = copy(snapshot.getData());
            challenge.put("challengeId", snapshot.getId());
            if (!uid.equals(stringValue(challenge.get("creatorUid")))) {
                throw new IllegalStateException("Samo autor može otkazati izazov.");
            }
            List<Map<String, Object>> participants = participants(challenge);
            if (!STATUS_OPEN.equals(stringValue(challenge.get("status"))) || participants.size() != 1) {
                throw new IllegalStateException("Izazov se može otkazati samo pre prvog prihvatanja.");
            }
            DocumentReference playerRef = firestore.collection(PLAYERS_COLLECTION).document(uid);
            DocumentSnapshot player = transaction.get(playerRef).get();
            if (!player.exists()) {
                throw new IllegalStateException("Igrač ne postoji.");
            }
            transaction.update(playerRef,
                    STARS_FIELD, intValue(player.get(STARS_FIELD)) + intValue(challenge.get("stakeStars")),
                    TOKENS_FIELD, intValue(player.get(TOKENS_FIELD)) + intValue(challenge.get("stakeTokens")));
            challenge.put("status", STATUS_CANCELLED);
            challenge.put("finishedAtMs", nowMs);
            transaction.set(ref, challenge, SetOptions.merge());
            return challenge;
        }).get();
        JSONObject json = toPublicJson(stored, uid);
        notifyChanged(json);
        return json;
    }

    public PreparedRun prepareRun(String uid, String challengeId) throws Exception {
        reconcileChallenge(challengeId);
        Firestore firestore = FirebaseAdmin.getFirestore();
        DocumentReference ref = challengeRef(firestore, challengeId);
        long nowMs = System.currentTimeMillis();
        Map<String, Object> stored = firestore.runTransaction(transaction -> {
            DocumentSnapshot snapshot = transaction.get(ref).get();
            if (!snapshot.exists()) {
                throw new IllegalStateException("Izazov ne postoji.");
            }
            Map<String, Object> challenge = copy(snapshot.getData());
            challenge.put("challengeId", snapshot.getId());
            String status = stringValue(challenge.get("status"));
            if (!STATUS_OPEN.equals(status) && !STATUS_CLOSED.equals(status)) {
                throw new IllegalStateException("Izazov više nije aktivan.");
            }
            List<Map<String, Object>> participants = participants(challenge);
            Map<String, Object> participant = findParticipant(participants, uid);
            if (participant == null) {
                throw new IllegalStateException("Nisi učesnik ovog izazova.");
            }
            String participantStatus = stringValue(participant.get("status"));
            if ("finished".equals(participantStatus) || "abandoned".equals(participantStatus)) {
                throw new IllegalStateException("Već si završio ovaj izazov.");
            }
            String sessionId = stringValue(participant.get("runSessionId"));
            if (sessionId.isBlank()) {
                sessionId = UUID.randomUUID().toString();
                participant.put("runSessionId", sessionId);
            }
            if (!"running".equals(participantStatus)) {
                participant.put("status", "running");
                participant.put("startedAtMs", nowMs);
            }
            challenge.put("participants", participants);
            transaction.set(ref, challenge, SetOptions.merge());
            return challenge;
        }).get();

        Map<String, Object> participant = participant(stored, uid);
        JSONObject json = toPublicJson(stored, uid);
        notifyChanged(json);
        return new PreparedRun(
                stringValue(participant.get("runSessionId")),
                longValue(stored.get("contentSeed")),
                json
        );
    }

    public void completeRun(String challengeId,
                            String uid,
                            int score,
                            long durationMs,
                            boolean abandoned) throws Exception {
        Firestore firestore = FirebaseAdmin.getFirestore();
        DocumentReference ref = challengeRef(firestore, challengeId);
        Map<String, Object> stored = firestore.runTransaction(transaction -> {
            DocumentSnapshot snapshot = transaction.get(ref).get();
            if (!snapshot.exists()) {
                throw new IllegalStateException("Izazov ne postoji.");
            }
            Map<String, Object> challenge = copy(snapshot.getData());
            challenge.put("challengeId", snapshot.getId());
            List<Map<String, Object>> participants = participants(challenge);
            Map<String, Object> participant = findParticipant(participants, uid);
            if (participant == null) {
                throw new IllegalStateException("Igrač nije učesnik izazova.");
            }
            String currentStatus = stringValue(participant.get("status"));
            if (!"finished".equals(currentStatus) && !"abandoned".equals(currentStatus)) {
                participant.put("status", abandoned ? "abandoned" : "finished");
                participant.put("score", abandoned ? 0 : score);
                participant.put("durationMs", Math.max(0L, durationMs));
                participant.put("finishedAtMs", System.currentTimeMillis());
                challenge.put("participants", participants);
                updateProvisionalRanking(challenge);
                transaction.set(ref, challenge, SetOptions.merge());
            }
            return challenge;
        }).get();
        notifyChanged(toPublicJson(stored, uid));
        settleIfReady(challengeId);
    }

    private void sweepExpiredChallenges() throws Exception {
        Firestore firestore = FirebaseAdmin.getFirestore();
        QuerySnapshot snapshot = firestore.collection(CHALLENGES_COLLECTION).get().get();
        for (QueryDocumentSnapshot document : snapshot.getDocuments()) {
            String status = document.getString("status");
            if (STATUS_OPEN.equals(status) || STATUS_CLOSED.equals(status)) {
                reconcileChallenge(document.getId());
            }
        }
    }

    private void reconcileChallenge(String challengeId) throws Exception {
        Firestore firestore = FirebaseAdmin.getFirestore();
        DocumentReference ref = challengeRef(firestore, challengeId);
        long nowMs = System.currentTimeMillis();
        Map<String, Object> changed = firestore.runTransaction(transaction -> {
            DocumentSnapshot snapshot = transaction.get(ref).get();
            if (!snapshot.exists()) {
                return null;
            }
            Map<String, Object> challenge = copy(snapshot.getData());
            challenge.put("challengeId", snapshot.getId());
            String status = stringValue(challenge.get("status"));
            List<Map<String, Object>> participants = participants(challenge);
            boolean updated = false;
            if (STATUS_OPEN.equals(status) && nowMs >= longValue(challenge.get("acceptanceDeadlineAtMs"))) {
                if (participants.size() >= 2) {
                    challenge.put("status", STATUS_CLOSED);
                    challenge.put("closedAtMs", nowMs);
                    challenge.put("runDeadlineAtMs", nowMs + RUN_WINDOW_MS);
                    updated = true;
                } else if (participants.size() == 1) {
                    String creatorUid = stringValue(challenge.get("creatorUid"));
                    DocumentReference creatorRef = firestore.collection(PLAYERS_COLLECTION).document(creatorUid);
                    DocumentSnapshot creator = transaction.get(creatorRef).get();
                    if (creator.exists()) {
                        transaction.update(creatorRef,
                                STARS_FIELD, intValue(creator.get(STARS_FIELD)) + intValue(challenge.get("stakeStars")),
                                TOKENS_FIELD, intValue(creator.get(TOKENS_FIELD)) + intValue(challenge.get("stakeTokens")));
                    }
                    challenge.put("status", STATUS_CANCELLED);
                    challenge.put("finishedAtMs", nowMs);
                    updated = true;
                }
            } else if (STATUS_CLOSED.equals(status)
                    && longValue(challenge.get("runDeadlineAtMs")) > 0L
                    && nowMs >= longValue(challenge.get("runDeadlineAtMs"))) {
                for (Map<String, Object> participant : participants) {
                    String participantStatus = stringValue(participant.get("status"));
                    if (!"finished".equals(participantStatus) && !"abandoned".equals(participantStatus)) {
                        participant.put("status", "abandoned");
                        participant.put("score", 0);
                        participant.put("durationMs", 0L);
                        participant.put("finishedAtMs", nowMs);
                        updated = true;
                    }
                }
                challenge.put("participants", participants);
                updateProvisionalRanking(challenge);
            }
            if (updated) {
                transaction.set(ref, challenge, SetOptions.merge());
                return challenge;
            }
            return null;
        }).get();
        if (changed != null) {
            notifyChanged(toPublicJson(changed, null));
        }
        settleIfReady(challengeId);
    }

    private void settleIfReady(String challengeId) throws Exception {
        Firestore firestore = FirebaseAdmin.getFirestore();
        DocumentReference ref = challengeRef(firestore, challengeId);
        Map<String, Object> settled = firestore.runTransaction(transaction -> {
            DocumentSnapshot snapshot = transaction.get(ref).get();
            if (!snapshot.exists()) {
                return null;
            }
            Map<String, Object> challenge = copy(snapshot.getData());
            challenge.put("challengeId", snapshot.getId());
            if (!STATUS_CLOSED.equals(stringValue(challenge.get("status")))
                    || booleanValue(challenge.get("settlementApplied"))) {
                return null;
            }
            List<Map<String, Object>> participants = participants(challenge);
            if (participants.size() < 2 || !allRunsComplete(participants)) {
                return null;
            }
            List<Map<String, Object>> ranked = rankedParticipants(participants);
            Map<String, Object> winner = ranked.get(0);
            Map<String, Object> runnerUp = ranked.get(1);
            String winnerUid = stringValue(winner.get("uid"));
            String runnerUpUid = stringValue(runnerUp.get("uid"));
            DocumentReference winnerRef = firestore.collection(PLAYERS_COLLECTION).document(winnerUid);
            DocumentReference runnerUpRef = firestore.collection(PLAYERS_COLLECTION).document(runnerUpUid);
            DocumentSnapshot winnerPlayer = transaction.get(winnerRef).get();
            DocumentSnapshot runnerUpPlayer = transaction.get(runnerUpRef).get();
            if (!winnerPlayer.exists() || !runnerUpPlayer.exists()) {
                throw new IllegalStateException("Nije moguće pronaći pobednike izazova.");
            }

            int stakeStars = intValue(challenge.get("stakeStars"));
            int stakeTokens = intValue(challenge.get("stakeTokens"));
            int winnerStars = RegionalChallengeRules.winnerShare(stakeStars, participants.size());
            int winnerTokens = RegionalChallengeRules.winnerShare(stakeTokens, participants.size());

            transaction.update(winnerRef,
                    STARS_FIELD, intValue(winnerPlayer.get(STARS_FIELD)) + winnerStars,
                    TOKENS_FIELD, intValue(winnerPlayer.get(TOKENS_FIELD)) + winnerTokens);
            transaction.update(runnerUpRef,
                    STARS_FIELD, intValue(runnerUpPlayer.get(STARS_FIELD)) + stakeStars,
                    TOKENS_FIELD, intValue(runnerUpPlayer.get(TOKENS_FIELD)) + stakeTokens);

            for (int index = 0; index < ranked.size(); index++) {
                Map<String, Object> participant = ranked.get(index);
                participant.put("rank", index + 1);
                participant.put("rewardStars", index == 0 ? winnerStars : index == 1 ? stakeStars : 0);
                participant.put("rewardTokens", index == 0 ? winnerTokens : index == 1 ? stakeTokens : 0);
            }
            challenge.put("participants", participants);
            challenge.put("winnerUid", winnerUid);
            challenge.put("runnerUpUid", runnerUpUid);
            challenge.put("provisionalWinnerUid", winnerUid);
            challenge.put("provisionalRunnerUpUid", runnerUpUid);
            challenge.put("status", STATUS_FINISHED);
            challenge.put("settlementApplied", true);
            challenge.put("finishedAtMs", System.currentTimeMillis());
            transaction.set(ref, challenge, SetOptions.merge());
            return challenge;
        }).get();
        if (settled != null) {
            notifyChanged(toPublicJson(settled, null));
        }
    }

    private JSONObject toPublicJson(Map<String, Object> challenge, String viewerUid) {
        JSONObject json = new JSONObject();
        json.put("challengeId", stringValue(challenge.get("challengeId")));
        json.put("creatorUid", stringValue(challenge.get("creatorUid")));
        json.put("creatorUsername", stringValue(challenge.get("creatorUsername")));
        json.put("creatorRegionId", stringValue(challenge.get("creatorRegionId")));
        json.put("status", stringValue(challenge.get("status")));
        json.put("stakeStars", intValue(challenge.get("stakeStars")));
        json.put("stakeTokens", intValue(challenge.get("stakeTokens")));
        json.put("participantCount", participants(challenge).size());
        json.put("maxPlayers", MAX_PLAYERS);
        json.put("createdAtMs", longValue(challenge.get("createdAtMs")));
        json.put("acceptanceDeadlineAtMs", longValue(challenge.get("acceptanceDeadlineAtMs")));
        json.put("runDeadlineAtMs", longValue(challenge.get("runDeadlineAtMs")));
        json.put("finishedAtMs", longValue(challenge.get("finishedAtMs")));
        json.put("provisionalWinnerUid", stringValue(challenge.get("provisionalWinnerUid")));
        json.put("provisionalRunnerUpUid", stringValue(challenge.get("provisionalRunnerUpUid")));
        json.put("winnerUid", stringValue(challenge.get("winnerUid")));
        json.put("runnerUpUid", stringValue(challenge.get("runnerUpUid")));
        int playerCount = participants(challenge).size();
        json.put("provisionalWinnerStars", RegionalChallengeRules.winnerShare(intValue(challenge.get("stakeStars")), playerCount));
        json.put("provisionalWinnerTokens", RegionalChallengeRules.winnerShare(intValue(challenge.get("stakeTokens")), playerCount));
        JSONArray participantArray = new JSONArray();
        for (Map<String, Object> participant : rankedParticipants(participants(challenge))) {
            JSONObject participantJson = new JSONObject();
            participantJson.put("uid", stringValue(participant.get("uid")));
            participantJson.put("username", stringValue(participant.get("username")));
            participantJson.put("status", stringValue(participant.get("status")));
            participantJson.put("score", intValue(participant.get("score")));
            participantJson.put("rank", intValue(participant.get("rank")));
            participantJson.put("rewardStars", intValue(participant.get("rewardStars")));
            participantJson.put("rewardTokens", intValue(participant.get("rewardTokens")));
            participantJson.put("isViewer", viewerUid != null && viewerUid.equals(stringValue(participant.get("uid"))));
            participantArray.put(participantJson);
        }
        json.put("participants", participantArray);
        Map<String, Object> viewer = participant(challenge, viewerUid);
        json.put("viewerParticipant", viewer != null);
        json.put("viewerRunStatus", viewer == null ? "" : stringValue(viewer.get("status")));
        json.put("serverNowMs", System.currentTimeMillis());
        return json;
    }

    private void updateProvisionalRanking(Map<String, Object> challenge) {
        List<Map<String, Object>> completed = completedParticipants(participants(challenge));
        challenge.put("provisionalWinnerUid", completed.isEmpty() ? "" : stringValue(completed.get(0).get("uid")));
        challenge.put("provisionalRunnerUpUid", completed.size() < 2 ? "" : stringValue(completed.get(1).get("uid")));
    }

    private List<Map<String, Object>> rankedParticipants(List<Map<String, Object>> participants) {
        List<Map<String, Object>> ranked = new ArrayList<>(participants);
        ranked.sort(participantComparator());
        return ranked;
    }

    private List<Map<String, Object>> completedParticipants(List<Map<String, Object>> participants) {
        List<Map<String, Object>> completed = new ArrayList<>();
        for (Map<String, Object> participant : participants) {
            String status = stringValue(participant.get("status"));
            if ("finished".equals(status) || "abandoned".equals(status)) {
                completed.add(participant);
            }
        }
        completed.sort(participantComparator());
        return completed;
    }

    private Comparator<Map<String, Object>> participantComparator() {
        return (left, right) -> RegionalChallengeRules.compareResults(
                intValue(left.get("score")),
                durationForRanking(left),
                intValue(left.get("joinOrder")),
                intValue(right.get("score")),
                durationForRanking(right),
                intValue(right.get("joinOrder"))
        );
    }

    private long durationForRanking(Map<String, Object> participant) {
        String status = stringValue(participant.get("status"));
        if (!"finished".equals(status) && !"abandoned".equals(status)) {
            return Long.MAX_VALUE;
        }
        return longValue(participant.get("durationMs"));
    }

    private boolean allRunsComplete(List<Map<String, Object>> participants) {
        for (Map<String, Object> participant : participants) {
            String status = stringValue(participant.get("status"));
            if (!"finished".equals(status) && !"abandoned".equals(status)) {
                return false;
            }
        }
        return true;
    }

    private Map<String, Object> newParticipant(String uid, String username, int order, long nowMs) {
        Map<String, Object> participant = new HashMap<>();
        participant.put("uid", uid);
        participant.put("username", safeName(username));
        participant.put("joinOrder", order);
        participant.put("acceptedAtMs", nowMs);
        participant.put("status", "ready");
        participant.put("runSessionId", "");
        participant.put("startedAtMs", 0L);
        participant.put("finishedAtMs", 0L);
        participant.put("durationMs", 0L);
        participant.put("score", 0);
        participant.put("rank", 0);
        participant.put("rewardStars", 0);
        participant.put("rewardTokens", 0);
        return participant;
    }

    private void requirePlayerAndBalance(DocumentSnapshot player, int stars, int tokens) {
        if (player == null || !player.exists()) {
            throw new IllegalStateException("Igrač ne postoji.");
        }
        if (intValue(player.get(STARS_FIELD)) < stars) {
            throw new IllegalStateException("Nemaš dovoljno zvezda.");
        }
        if (intValue(player.get(TOKENS_FIELD)) < tokens) {
            throw new IllegalStateException("Nemaš dovoljno tokena.");
        }
    }

    private void validateStake(int stars, int tokens) {
        if (stars < 0 || stars > MAX_STARS) {
            throw new IllegalArgumentException("Ulog zvezda mora biti između 0 i 10.");
        }
        if (tokens < 0 || tokens > MAX_TOKENS) {
            throw new IllegalArgumentException("Ulog tokena mora biti između 0 i 2.");
        }
        if (stars == 0 && tokens == 0) {
            throw new IllegalArgumentException("Izazov mora imati najmanje jedan ulog.");
        }
    }

    private DocumentReference challengeRef(Firestore firestore, String challengeId) {
        if (challengeId == null || challengeId.isBlank()) {
            throw new IllegalArgumentException("ID izazova je obavezan.");
        }
        return firestore.collection(CHALLENGES_COLLECTION).document(challengeId);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> participants(Map<String, Object> challenge) {
        Object value = challenge.get("participants");
        List<Map<String, Object>> result = new ArrayList<>();
        if (value instanceof List<?>) {
            for (Object item : (List<?>) value) {
                if (item instanceof Map<?, ?>) {
                    result.add(new HashMap<>((Map<String, Object>) item));
                }
            }
        }
        return result;
    }

    private Map<String, Object> participant(Map<String, Object> challenge, String uid) {
        return findParticipant(participants(challenge), uid);
    }

    private Map<String, Object> findParticipant(List<Map<String, Object>> participants, String uid) {
        if (uid == null) {
            return null;
        }
        for (Map<String, Object> participant : participants) {
            if (uid.equals(stringValue(participant.get("uid")))) {
                return participant;
            }
        }
        return null;
    }

    private Map<String, Object> copy(Map<String, Object> source) {
        return source == null ? new HashMap<>() : new HashMap<>(source);
    }

    private int intValue(Object value) {
        return value instanceof Number ? ((Number) value).intValue() : 0;
    }

    private long longValue(Object value) {
        return value instanceof Number ? ((Number) value).longValue() : 0L;
    }

    private boolean booleanValue(Object value) {
        return value instanceof Boolean && (Boolean) value;
    }

    private String stringValue(Object value) {
        return value instanceof String ? (String) value : "";
    }

    private String safeName(String username) {
        return username == null || username.isBlank() ? "Igrač" : username;
    }

    private void notifyChanged(JSONObject challenge) {
        if (changeListener != null && challenge != null) {
            changeListener.onChallengeChanged(challenge);
        }
    }

    public interface ChallengeChangeListener {
        void onChallengeChanged(JSONObject challenge);
    }

    public static final class PreparedRun {
        private final String sessionId;
        private final long contentSeed;
        private final JSONObject challenge;

        private PreparedRun(String sessionId, long contentSeed, JSONObject challenge) {
            this.sessionId = sessionId;
            this.contentSeed = contentSeed;
            this.challenge = challenge;
        }

        public String getSessionId() {
            return sessionId;
        }

        public long getContentSeed() {
            return contentSeed;
        }

        public JSONObject getChallenge() {
            return challenge;
        }
    }
}
