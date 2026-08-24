package com.example.slagalica.Model;

import org.json.JSONObject;

public final class ChatMessage {
    private final String messageId;
    private final String regionId;
    private final String senderUid;
    private final String senderName;
    private final String text;
    private final long sentAtMs;

    private ChatMessage(JSONObject json) {
        messageId = json.optString("messageId", null);
        regionId = json.optString("regionId", null);
        senderUid = json.optString("senderUid", null);
        senderName = json.optString("senderName", "Igrač");
        text = json.optString("text", "");
        sentAtMs = json.optLong("sentAtMs", 0L);
    }

    public static ChatMessage fromJson(JSONObject json) {
        if (json == null) {
            return null;
        }
        ChatMessage message = new ChatMessage(json);
        if (isBlank(message.messageId) || isBlank(message.senderUid) || isBlank(message.text)) {
            return null;
        }
        return message;
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    public String getMessageId() {
        return messageId;
    }

    public String getRegionId() {
        return regionId;
    }

    public String getSenderUid() {
        return senderUid;
    }

    public String getSenderName() {
        return senderName;
    }

    public String getText() {
        return text;
    }

    public long getSentAtMs() {
        return sentAtMs;
    }
}
