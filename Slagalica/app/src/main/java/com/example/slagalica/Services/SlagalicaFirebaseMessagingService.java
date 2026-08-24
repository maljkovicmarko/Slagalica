package com.example.slagalica.Services;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.core.app.NotificationCompat;

import com.example.slagalica.Activities.MainActivity;
import com.example.slagalica.R;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import java.util.Map;

public final class SlagalicaFirebaseMessagingService extends FirebaseMessagingService {
    public static final String ACTION_LEADERBOARD_REWARD =
            "com.example.slagalica.LEADERBOARD_REWARD";
    public static final String ACTION_REGION_CHAT =
            "com.example.slagalica.REGION_CHAT";
    public static final String REWARD_CHANNEL_ID = "leaderboard_rewards";
    public static final String CHAT_CHANNEL_ID = "region_chat_messages";

    @Override
    public void onNewToken(String token) {
        new FcmTokenService(this).storeTokenForCurrentUser(token);
    }

    @Override
    public void onMessageReceived(RemoteMessage message) {
        Map<String, String> data = message.getData();
        if ("leaderboard_reward".equals(data.get("type"))) {
            String title = firstNonEmpty(data.get("title"), getString(R.string.leaderboard_push_title));
            String body = firstNonEmpty(data.get("body"), getString(R.string.leaderboard_push_body));
            showRewardNotification(title, body);
        } else if ("region_chat_message".equals(data.get("type"))) {
            String title = firstNonEmpty(data.get("title"), getString(R.string.chat_push_title));
            String body = firstNonEmpty(data.get("body"), getString(R.string.chat_push_body));
            showChatNotification(title, body, data.get("messageId"));
        }
    }

    private void showRewardNotification(String title, String body) {
        ensureNotificationChannels();

        Intent intent = new Intent(this, MainActivity.class);
        intent.setAction(ACTION_LEADERBOARD_REWARD);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);

        PendingIntent pendingIntent = PendingIntent.getActivity(
                this,
                1001,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, REWARD_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent);

        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.notify(1001, builder.build());
        }
    }

    private void showChatNotification(String title, String body, String messageId) {
        ensureNotificationChannels();

        Intent intent = new Intent(this, MainActivity.class);
        intent.setAction(ACTION_REGION_CHAT);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);

        int notificationId = messageId == null ? (int) System.currentTimeMillis() : messageId.hashCode();
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this,
                notificationId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHAT_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent);

        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.notify(notificationId, builder.build());
        }
    }

    private void ensureNotificationChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }

        NotificationChannel channel = new NotificationChannel(
                REWARD_CHANNEL_ID,
                getString(R.string.leaderboard_push_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT
        );
        channel.setDescription(getString(R.string.leaderboard_push_channel_description));

        NotificationChannel chatChannel = new NotificationChannel(
                CHAT_CHANNEL_ID,
                getString(R.string.chat_push_channel_name),
                NotificationManager.IMPORTANCE_HIGH
        );
        chatChannel.setDescription(getString(R.string.chat_push_channel_description));

        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.createNotificationChannel(channel);
            manager.createNotificationChannel(chatChannel);
        }
    }

    private String firstNonEmpty(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value;
    }
}
