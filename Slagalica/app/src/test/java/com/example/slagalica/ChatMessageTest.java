package com.example.slagalica;

import com.example.slagalica.Model.ChatMessage;

import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class ChatMessageTest {
    @Test
    public void parsesCompleteServerMessage() throws Exception {
        JSONObject json = new JSONObject()
                .put("messageId", "message-1")
                .put("regionId", "beograd")
                .put("senderUid", "player-1")
                .put("senderName", "Marko")
                .put("text", "Pozdrav!")
                .put("sentAtMs", 123456789L);

        ChatMessage message = ChatMessage.fromJson(json);

        assertEquals("message-1", message.getMessageId());
        assertEquals("beograd", message.getRegionId());
        assertEquals("player-1", message.getSenderUid());
        assertEquals("Marko", message.getSenderName());
        assertEquals("Pozdrav!", message.getText());
        assertEquals(123456789L, message.getSentAtMs());
    }

    @Test
    public void rejectsMessagesWithoutRequiredFields() throws Exception {
        assertNull(ChatMessage.fromJson(new JSONObject().put("messageId", "message-1")));
    }
}
