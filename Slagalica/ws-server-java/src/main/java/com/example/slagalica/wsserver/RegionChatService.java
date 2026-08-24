package com.example.slagalica.wsserver;

import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.FieldValue;
import com.google.cloud.firestore.Query;
import com.google.cloud.firestore.QueryDocumentSnapshot;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class RegionChatService {
    private static final String PLAYERS_COLLECTION = "players";
    private static final String REGION_CHATS_COLLECTION = "region_chats";
    private static final String MESSAGES_COLLECTION = "messages";
    private static final int HISTORY_LIMIT = 100;
    private static final int MAX_MESSAGE_LENGTH = 500;

    public ChatHistory loadHistory(String uid) throws Exception {
        PlayerIdentity player = loadPlayer(uid);
        List<QueryDocumentSnapshot> documents = FirebaseAdmin.getFirestore()
                .collection(REGION_CHATS_COLLECTION)
                .document(player.regionId)
                .collection(MESSAGES_COLLECTION)
                .orderBy("sentAtMs", Query.Direction.DESCENDING)
                .limit(HISTORY_LIMIT)
                .get()
                .get()
                .getDocuments();

        List<JSONObject> messages = new ArrayList<>();
        for (QueryDocumentSnapshot document : documents) {
            messages.add(toJson(document));
        }
        Collections.reverse(messages);
        return new ChatHistory(player.regionId, player.regionName, messages);
    }

    public SentMessage sendMessage(String uid, String rawText) throws Exception {
        String text = validateMessageText(rawText);
        Firestore firestore = FirebaseAdmin.getFirestore();
        PlayerIdentity sender = loadPlayer(firestore, uid);
        long sentAtMs = System.currentTimeMillis();
        DocumentReference messageReference = firestore
                .collection(REGION_CHATS_COLLECTION)
                .document(sender.regionId)
                .collection(MESSAGES_COLLECTION)
                .document();

        Map<String, Object> fields = new HashMap<>();
        fields.put("messageId", messageReference.getId());
        fields.put("regionId", sender.regionId);
        fields.put("senderUid", sender.uid);
        fields.put("senderName", sender.username);
        fields.put("text", text);
        fields.put("sentAtMs", sentAtMs);
        fields.put("sentAt", FieldValue.serverTimestamp());
        messageReference.set(fields).get();

        JSONObject message = new JSONObject();
        message.put("messageId", messageReference.getId());
        message.put("regionId", sender.regionId);
        message.put("senderUid", sender.uid);
        message.put("senderName", sender.username);
        message.put("text", text);
        message.put("sentAtMs", sentAtMs);
        List<DocumentSnapshot> regionPlayers = loadRegionPlayers(firestore, sender.regionId);
        return new SentMessage(message, regionPlayers);
    }

    static String validateMessageText(String rawText) {
        String text = rawText == null ? "" : rawText.trim();
        if (text.isEmpty()) {
            throw new IllegalArgumentException("Poruka ne može biti prazna.");
        }
        if (text.length() > MAX_MESSAGE_LENGTH) {
            throw new IllegalArgumentException("Poruka može imati najviše " + MAX_MESSAGE_LENGTH + " znakova.");
        }
        return text;
    }

    private PlayerIdentity loadPlayer(String uid) throws Exception {
        return loadPlayer(FirebaseAdmin.getFirestore(), uid);
    }

    private PlayerIdentity loadPlayer(Firestore firestore, String uid) throws Exception {
        if (uid == null || uid.isBlank()) {
            throw new IllegalArgumentException("Korisnik nije prijavljen.");
        }

        DocumentSnapshot player = firestore.collection(PLAYERS_COLLECTION).document(uid).get().get();
        if (!player.exists()) {
            throw new IllegalArgumentException("Profil igrača ne postoji.");
        }

        String regionName = firstNonBlank(player.getString("region"), player.getString("regionId"));
        String regionId = firstNonBlank(player.getString("regionId"), normalizeRegionId(regionName));
        String username = firstNonBlank(player.getString("username"), "Igrač");
        if (regionId == null) {
            throw new IllegalArgumentException("Igrač nema izabran region.");
        }
        return new PlayerIdentity(uid, username, regionId, firstNonBlank(regionName, regionId));
    }

    private List<DocumentSnapshot> loadRegionPlayers(Firestore firestore, String regionId) throws Exception {
        List<DocumentSnapshot> result = new ArrayList<>();
        for (QueryDocumentSnapshot player : firestore.collection(PLAYERS_COLLECTION).get().get().getDocuments()) {
            String playerRegionId = firstNonBlank(
                    player.getString("regionId"),
                    normalizeRegionId(player.getString("region"))
            );
            if (regionId.equals(playerRegionId)) {
                result.add(player);
            }
        }
        return result;
    }

    private JSONObject toJson(DocumentSnapshot document) {
        JSONObject json = new JSONObject();
        json.put("messageId", firstNonBlank(document.getString("messageId"), document.getId()));
        json.put("regionId", document.getString("regionId"));
        json.put("senderUid", document.getString("senderUid"));
        json.put("senderName", document.getString("senderName"));
        json.put("text", document.getString("text"));
        Long sentAtMs = document.getLong("sentAtMs");
        json.put("sentAtMs", sentAtMs == null ? 0L : sentAtMs);
        return json;
    }

    static String normalizeRegionId(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "_")
                .replaceAll("^_+|_+$", "");
        if ("sumadija_i_zapadna_srbija".equals(normalized)) {
            return "sumadija_zapadna_srbija";
        }
        if ("juzna_i_istocna_srbija".equals(normalized)) {
            return "juzna_istocna_srbija";
        }
        if ("kosovo_i_metohija".equals(normalized)) {
            return "kosovo_metohija";
        }
        return normalized.isBlank() ? null : normalized;
    }

    private String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first.trim();
        }
        if (second != null && !second.isBlank()) {
            return second.trim();
        }
        return null;
    }

    public static final class ChatHistory {
        private final String regionId;
        private final String regionName;
        private final List<JSONObject> messages;

        private ChatHistory(String regionId, String regionName, List<JSONObject> messages) {
            this.regionId = regionId;
            this.regionName = regionName;
            this.messages = messages;
        }

        public JSONObject toJson() {
            JSONObject json = new JSONObject();
            json.put("regionId", regionId);
            json.put("regionName", regionName);
            json.put("messages", new JSONArray(messages));
            return json;
        }
    }

    public static final class SentMessage {
        private final JSONObject message;
        private final List<DocumentSnapshot> regionPlayers;

        private SentMessage(JSONObject message, List<DocumentSnapshot> regionPlayers) {
            this.message = message;
            this.regionPlayers = regionPlayers;
        }

        public JSONObject getMessage() {
            return message;
        }

        public List<DocumentSnapshot> getRegionPlayers() {
            return regionPlayers;
        }
    }

    private static final class PlayerIdentity {
        private final String uid;
        private final String username;
        private final String regionId;
        private final String regionName;

        private PlayerIdentity(String uid, String username, String regionId, String regionName) {
            this.uid = uid;
            this.username = username;
            this.regionId = regionId;
            this.regionName = regionName;
        }
    }
}
