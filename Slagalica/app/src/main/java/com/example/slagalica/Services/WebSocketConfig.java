package com.example.slagalica.Services;

import android.content.Context;
import android.content.SharedPreferences;

public final class WebSocketConfig {

    private static final String PREFERENCES_NAME = "websocket_config";
    private static final String KEY_SERVER_URL = "server_url";
    private static final String DEFAULT_SERVER_URL = "ws://10.0.2.2:8080";

    private WebSocketConfig() {
    }

    public static String getServerUrl(Context context) {
        SharedPreferences sharedPreferences = context.getSharedPreferences(
                PREFERENCES_NAME,
                Context.MODE_PRIVATE
        );

        String savedServerUrl = sharedPreferences.getString(KEY_SERVER_URL, DEFAULT_SERVER_URL);
        return normalizeServerUrl(savedServerUrl);
    }

    public static void saveServerUrl(Context context, String serverUrl) {
        SharedPreferences sharedPreferences = context.getSharedPreferences(
                PREFERENCES_NAME,
                Context.MODE_PRIVATE
        );

        sharedPreferences.edit()
                .putString(KEY_SERVER_URL, normalizeServerUrl(serverUrl))
                .apply();
    }

    public static void resetServerUrl(Context context) {
        SharedPreferences sharedPreferences = context.getSharedPreferences(
                PREFERENCES_NAME,
                Context.MODE_PRIVATE
        );

        sharedPreferences.edit()
                .remove(KEY_SERVER_URL)
                .apply();
    }

    public static String getDefaultServerUrl() {
        return DEFAULT_SERVER_URL;
    }

    public static String normalizeServerUrl(String serverUrl) {
        if (serverUrl == null) {
            return DEFAULT_SERVER_URL;
        }

        String normalizedServerUrl = serverUrl.trim();
        if (normalizedServerUrl.isEmpty()) {
            return DEFAULT_SERVER_URL;
        }

        if (!normalizedServerUrl.startsWith("ws://") && !normalizedServerUrl.startsWith("wss://")) {
            normalizedServerUrl = "ws://" + normalizedServerUrl;
        }

        return normalizedServerUrl;
    }
}
