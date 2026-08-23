package com.example.slagalica.wsserver;

import com.example.slagalica.Model.LeaderboardCycle;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.Message;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class RewardPushNotificationSender {
    private static final String TYPE = "leaderboard_reward";

    public void sendRewardNotification(DocumentSnapshot player,
                                       LeaderboardCycle cycle,
                                       int rank,
                                       int tokens) {
        List<String> tokensToNotify = readFcmTokens(player);
        if (tokensToNotify.isEmpty()) {
            System.out.println("No FCM token for reward notification: player=" + player.getId());
            return;
        }

        String title = "Osvojena nagrada";
        String body = "Osvojili ste " + tokens + " tokena za " + rank + ". mesto na "
                + cycleLabel(cycle) + " rang listi.";

        for (String fcmToken : tokensToNotify) {
            sendToToken(fcmToken, title, body, cycle, rank, tokens, player.getId());
        }
    }

    private void sendToToken(String fcmToken,
                             String title,
                             String body,
                             LeaderboardCycle cycle,
                             int rank,
                             int tokens,
                             String playerId) {
        Message message = Message.builder()
                .setToken(fcmToken)
                .putData("type", TYPE)
                .putData("title", title)
                .putData("body", body)
                .putData("cycleId", cycle.getCycleId())
                .putData("cycleType", cycle.getType().getFirestoreValue())
                .putData("rank", Integer.toString(rank))
                .putData("tokens", Integer.toString(tokens))
                .build();

        try {
            FirebaseMessaging.getInstance().send(message);
        } catch (Exception exception) {
            System.out.println("Failed to send reward FCM notification to player "
                    + playerId + ": " + exception.getMessage());
        }
    }

    private List<String> readFcmTokens(DocumentSnapshot player) {
        Set<String> result = new LinkedHashSet<>();

        String singleToken = player.getString("fcmToken");
        addToken(result, singleToken);

        Object tokensValue = player.get("fcmTokens");
        if (tokensValue instanceof List<?>) {
            for (Object value : (List<?>) tokensValue) {
                if (value instanceof String) {
                    addToken(result, (String) value);
                }
            }
        }

        Object legacyTokensValue = player.get("fcmTokensByDevice");
        if (legacyTokensValue instanceof Map<?, ?>) {
            for (Object value : ((Map<?, ?>) legacyTokensValue).values()) {
                if (value instanceof String) {
                    addToken(result, (String) value);
                }
            }
        }

        return new ArrayList<>(result);
    }

    private void addToken(Set<String> tokens, String token) {
        if (token != null && !token.trim().isEmpty()) {
            tokens.add(token.trim());
        }
    }

    private String cycleLabel(LeaderboardCycle cycle) {
        return cycle.getType() == LeaderboardCycle.Type.MONTHLY ? "mesečnoj" : "nedeljnoj";
    }
}
