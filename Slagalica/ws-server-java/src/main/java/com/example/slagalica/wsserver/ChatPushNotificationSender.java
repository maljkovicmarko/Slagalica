package com.example.slagalica.wsserver;

import com.google.cloud.firestore.DocumentSnapshot;
import com.google.firebase.messaging.AndroidConfig;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.Message;

import org.json.JSONObject;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ChatPushNotificationSender {
    private static final String TYPE = "region_chat_message";

    public void send(DocumentSnapshot recipient, JSONObject chatMessage) {
        Set<String> tokens = readFcmTokens(recipient);
        if (tokens.isEmpty()) {
            return;
        }

        String senderName = chatMessage.optString("senderName", "Nova poruka");
        String text = chatMessage.optString("text", "");
        for (String token : tokens) {
            Message message = Message.builder()
                    .setToken(token)
                    .setAndroidConfig(AndroidConfig.builder()
                            .setPriority(AndroidConfig.Priority.HIGH)
                            .build())
                    .putData("type", TYPE)
                    .putData("title", senderName)
                    .putData("body", text)
                    .putData("regionId", chatMessage.optString("regionId", ""))
                    .putData("messageId", chatMessage.optString("messageId", ""))
                    .build();
            try {
                FirebaseMessaging.getInstance().send(message);
            } catch (Exception exception) {
                System.out.println("Failed to send chat FCM notification to player "
                        + recipient.getId() + ": " + exception.getMessage());
            }
        }
    }

    private Set<String> readFcmTokens(DocumentSnapshot player) {
        Set<String> result = new LinkedHashSet<>();
        addToken(result, player.getString("fcmToken"));

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
        return result;
    }

    private void addToken(Set<String> tokens, String token) {
        if (token != null && !token.trim().isEmpty()) {
            tokens.add(token.trim());
        }
    }
}
