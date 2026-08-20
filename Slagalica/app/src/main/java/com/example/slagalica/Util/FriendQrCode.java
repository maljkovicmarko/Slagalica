package com.example.slagalica.Util;

import android.net.Uri;

public final class FriendQrCode {
    private static final String SCHEME = "slagalica";
    private static final String HOST = "friend";

    private FriendQrCode() {
    }

    public static String buildPayload(String playerUid) {
        return SCHEME + "://" + HOST + "/" + playerUid;
    }

    public static String extractPlayerUid(String payload) {
        if (payload == null || payload.trim().isEmpty()) {
            return null;
        }

        String trimmedPayload = payload.trim();
        if (trimmedPayload.startsWith(SCHEME + "://" + HOST + "/")) {
            Uri uri = Uri.parse(trimmedPayload);
            if (!SCHEME.equals(uri.getScheme()) || !HOST.equals(uri.getHost())) {
                return null;
            }
            return sanitizeUid(uri.getLastPathSegment());
        }

        if (!trimmedPayload.contains("://")) {
            return sanitizeUid(trimmedPayload);
        }

        return null;
    }

    private static String sanitizeUid(String uid) {
        if (uid == null) {
            return null;
        }

        String trimmedUid = uid.trim();
        if (trimmedUid.length() < 8 || trimmedUid.length() > 128) {
            return null;
        }
        if (!trimmedUid.matches("[A-Za-z0-9_-]+")) {
            return null;
        }
        return trimmedUid;
    }
}
