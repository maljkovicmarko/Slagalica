package com.example.slagalica.wsserver;

import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.SetOptions;

import org.json.JSONArray;
import org.json.JSONObject;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class DailyMissionService {
    public static final String MISSION_WIN_MATCH = "win_match";
    public static final String MISSION_SEND_CHAT_MESSAGE = "send_chat_message";
    public static final String MISSION_PLAY_FRIENDLY_MATCH = "play_friendly_match";

    private static final String PLAYERS_COLLECTION = "players";
    private static final String DAILY_MISSIONS_COLLECTION = "dailyMissions";
    private static final String TOTAL_STARS_FIELD = "totalStars";
    private static final String TOKENS_FIELD = "tokens";
    private static final ZoneId MISSION_ZONE = ZoneId.of("Europe/Belgrade");
    private static final int MISSION_REWARD_STARS = 3;
    private static final int BONUS_REWARD_STARS = 3;
    private static final int BONUS_REWARD_TOKENS = 2;

    private static final List<MissionDefinition> MISSIONS = List.of(
            new MissionDefinition(MISSION_WIN_MATCH, "Pobedi partiju", MISSION_REWARD_STARS),
            new MissionDefinition(MISSION_SEND_CHAT_MESSAGE, "Pošalji poruku u čet", MISSION_REWARD_STARS),
            new MissionDefinition(MISSION_PLAY_FRIENDLY_MATCH, "Odigraj prijateljsku partiju", MISSION_REWARD_STARS)
    );

    private final MissionChangeListener changeListener;

    public DailyMissionService() {
        this(null);
    }

    public DailyMissionService(MissionChangeListener changeListener) {
        this.changeListener = changeListener;
    }

    public JSONObject getToday(String uid) throws Exception {
        if (uid == null || uid.isBlank()) {
            throw new IllegalArgumentException("Korisnik nije prijavljen.");
        }
        Firestore firestore = FirebaseAdmin.getFirestore();
        String date = todayMissionDate();
        DocumentReference missionRef = missionRef(firestore, uid, date);
        DocumentSnapshot snapshot = missionRef.get().get();
        Map<String, Object> state = snapshot.exists()
                ? new HashMap<>(snapshot.getData())
                : initialMissionDocument(date, System.currentTimeMillis());
        return toPublicJson(uid, state);
    }

    public MissionUpdateResult completeMission(String uid, String missionId) throws Exception {
        validateMissionId(missionId);
        if (uid == null || uid.isBlank()) {
            throw new IllegalArgumentException("Korisnik nije prijavljen.");
        }

        Firestore firestore = FirebaseAdmin.getFirestore();
        String date = todayMissionDate();
        DocumentReference playerRef = firestore.collection(PLAYERS_COLLECTION).document(uid);
        DocumentReference missionRef = missionRef(firestore, uid, date);

        MissionUpdateResult result = firestore.runTransaction(transaction -> {
            DocumentSnapshot player = transaction.get(playerRef).get();
            if (!player.exists()) {
                throw new IllegalArgumentException("Profil igrača ne postoji.");
            }

            DocumentSnapshot missionSnapshot = transaction.get(missionRef).get();
            Map<String, Object> state = missionSnapshot.exists()
                    ? new HashMap<>(missionSnapshot.getData())
                    : initialMissionDocument(date, System.currentTimeMillis());
            normalizeMissionDocument(state, date);

            @SuppressWarnings("unchecked")
            Map<String, Object> missions = (Map<String, Object>) state.get("missions");
            @SuppressWarnings("unchecked")
            Map<String, Object> mission = new HashMap<>((Map<String, Object>) missions.get(missionId));

            boolean missionChanged = false;
            boolean bonusChanged = false;
            int starsToAward = 0;
            int tokensToAward = 0;
            long nowMs = System.currentTimeMillis();

            if (!booleanValue(mission.get("completed"))) {
                mission.put("completed", true);
                mission.put("completedAtMs", nowMs);
                mission.put("starsAwarded", MISSION_REWARD_STARS);
                missions.put(missionId, mission);
                starsToAward += MISSION_REWARD_STARS;
                missionChanged = true;
            }

            if (!booleanValue(state.get("bonusApplied")) && areAllMissionsCompleted(missions)) {
                state.put("bonusApplied", true);
                state.put("bonusAppliedAtMs", nowMs);
                state.put("bonusStarsAwarded", BONUS_REWARD_STARS);
                state.put("bonusTokensAwarded", BONUS_REWARD_TOKENS);
                starsToAward += BONUS_REWARD_STARS;
                tokensToAward += BONUS_REWARD_TOKENS;
                bonusChanged = true;
            }

            if (!missionChanged && !bonusChanged) {
                return new MissionUpdateResult(false, false, toPublicJson(uid, state));
            }

            state.put("totalStarsAwarded", intValue(state.get("totalStarsAwarded")) + starsToAward);
            state.put("totalTokensAwarded", intValue(state.get("totalTokensAwarded")) + tokensToAward);
            state.put("updatedAtMs", nowMs);

            int updatedStars = intValue(player.get(TOTAL_STARS_FIELD)) + starsToAward;
            int updatedTokens = intValue(player.get(TOKENS_FIELD)) + tokensToAward;

            Map<String, Object> playerUpdates = new HashMap<>();
            playerUpdates.put(TOTAL_STARS_FIELD, updatedStars);
            playerUpdates.put(TOKENS_FIELD, updatedTokens);
            transaction.set(playerRef, playerUpdates, SetOptions.merge());
            transaction.set(missionRef, state, SetOptions.merge());
            return new MissionUpdateResult(missionChanged, bonusChanged, toPublicJson(uid, state));
        }).get();

        if ((result.isMissionCompletedNow() || result.isBonusAppliedNow()) && changeListener != null) {
            changeListener.onDailyMissionsChanged(uid, result.getStateJson());
        }
        return result;
    }

    private String todayMissionDate() {
        return LocalDate.now(MISSION_ZONE).toString();
    }

    private DocumentReference missionRef(Firestore firestore, String uid, String date) {
        return firestore
                .collection(PLAYERS_COLLECTION)
                .document(uid)
                .collection(DAILY_MISSIONS_COLLECTION)
                .document(date);
    }

    private Map<String, Object> initialMissionDocument(String date, long nowMs) {
        Map<String, Object> state = new HashMap<>();
        state.put("date", date);
        state.put("timezone", MISSION_ZONE.getId());
        state.put("missions", initialMissionStates());
        state.put("bonusApplied", false);
        state.put("bonusStarsAwarded", 0);
        state.put("bonusTokensAwarded", 0);
        state.put("totalStarsAwarded", 0);
        state.put("totalTokensAwarded", 0);
        state.put("updatedAtMs", nowMs);
        return state;
    }

    @SuppressWarnings("unchecked")
    private void normalizeMissionDocument(Map<String, Object> state, String date) {
        state.putIfAbsent("date", date);
        state.putIfAbsent("timezone", MISSION_ZONE.getId());
        state.putIfAbsent("bonusApplied", false);
        state.putIfAbsent("bonusStarsAwarded", 0);
        state.putIfAbsent("bonusTokensAwarded", 0);
        state.putIfAbsent("totalStarsAwarded", 0);
        state.putIfAbsent("totalTokensAwarded", 0);

        Object rawMissions = state.get("missions");
        Map<String, Object> missions = rawMissions instanceof Map<?, ?>
                ? new HashMap<>((Map<String, Object>) rawMissions)
                : new HashMap<>();
        Map<String, Object> defaults = initialMissionStates();
        for (MissionDefinition definition : MISSIONS) {
            Object existing = missions.get(definition.id);
            if (existing instanceof Map<?, ?>) {
                missions.put(definition.id, new HashMap<>((Map<String, Object>) existing));
            } else {
                missions.put(definition.id, defaults.get(definition.id));
            }
        }
        state.put("missions", missions);
    }

    private Map<String, Object> initialMissionStates() {
        Map<String, Object> missions = new LinkedHashMap<>();
        for (MissionDefinition definition : MISSIONS) {
            Map<String, Object> mission = new HashMap<>();
            mission.put("completed", false);
            mission.put("starsAwarded", 0);
            missions.put(definition.id, mission);
        }
        return missions;
    }

    @SuppressWarnings("unchecked")
    private JSONObject toPublicJson(String uid, Map<String, Object> state) {
        JSONObject json = new JSONObject();
        json.put("uid", uid);
        json.put("date", stringValue(state.get("date")));
        json.put("timezone", stringValue(state.get("timezone")));
        json.put("serverNowMs", System.currentTimeMillis());
        json.put("bonusApplied", booleanValue(state.get("bonusApplied")));
        json.put("bonusStars", BONUS_REWARD_STARS);
        json.put("bonusTokens", BONUS_REWARD_TOKENS);
        json.put("bonusStarsAwarded", intValue(state.get("bonusStarsAwarded")));
        json.put("bonusTokensAwarded", intValue(state.get("bonusTokensAwarded")));
        json.put("totalStarsAwarded", intValue(state.get("totalStarsAwarded")));
        json.put("totalTokensAwarded", intValue(state.get("totalTokensAwarded")));

        Map<String, Object> missions = state.get("missions") instanceof Map<?, ?>
                ? (Map<String, Object>) state.get("missions")
                : initialMissionStates();
        JSONArray missionArray = new JSONArray();
        for (MissionDefinition definition : MISSIONS) {
            Object rawMission = missions.get(definition.id);
            Map<String, Object> mission = rawMission instanceof Map<?, ?>
                    ? (Map<String, Object>) rawMission
                    : new HashMap<>();
            JSONObject missionJson = new JSONObject();
            missionJson.put("id", definition.id);
            missionJson.put("title", definition.title);
            missionJson.put("rewardStars", definition.rewardStars);
            missionJson.put("completed", booleanValue(mission.get("completed")));
            missionJson.put("completedAtMs", longValue(mission.get("completedAtMs")));
            missionJson.put("starsAwarded", intValue(mission.get("starsAwarded")));
            missionArray.put(missionJson);
        }
        json.put("missions", missionArray);
        json.put("completedCount", completedCount(missions));
        json.put("missionCount", MISSIONS.size());
        return json;
    }

    private boolean areAllMissionsCompleted(Map<String, Object> missions) {
        return completedCount(missions) == MISSIONS.size();
    }

    @SuppressWarnings("unchecked")
    private int completedCount(Map<String, Object> missions) {
        int count = 0;
        for (MissionDefinition definition : MISSIONS) {
            Object rawMission = missions.get(definition.id);
            if (rawMission instanceof Map<?, ?>
                    && booleanValue(((Map<String, Object>) rawMission).get("completed"))) {
                count++;
            }
        }
        return count;
    }

    private void validateMissionId(String missionId) {
        for (MissionDefinition definition : MISSIONS) {
            if (definition.id.equals(missionId)) {
                return;
            }
        }
        throw new IllegalArgumentException("Nepoznata dnevna misija.");
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

    public interface MissionChangeListener {
        void onDailyMissionsChanged(String uid, JSONObject state);
    }

    public static final class MissionUpdateResult {
        private final boolean missionCompletedNow;
        private final boolean bonusAppliedNow;
        private final JSONObject stateJson;

        private MissionUpdateResult(boolean missionCompletedNow, boolean bonusAppliedNow, JSONObject stateJson) {
            this.missionCompletedNow = missionCompletedNow;
            this.bonusAppliedNow = bonusAppliedNow;
            this.stateJson = stateJson;
        }

        public boolean isMissionCompletedNow() {
            return missionCompletedNow;
        }

        public boolean isBonusAppliedNow() {
            return bonusAppliedNow;
        }

        public JSONObject getStateJson() {
            return stateJson;
        }
    }

    private static final class MissionDefinition {
        private final String id;
        private final String title;
        private final int rewardStars;

        private MissionDefinition(String id, String title, int rewardStars) {
            this.id = id;
            this.title = title;
            this.rewardStars = rewardStars;
        }
    }
}
