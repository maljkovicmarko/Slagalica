package com.example.slagalica.Services;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.messaging.FirebaseMessaging;

import java.util.HashMap;
import java.util.Map;

public final class FcmTokenService {
    private static final String TAG = "FcmTokenService";
    private static final String PREFS_NAME = "slagalica_fcm";
    private static final String PENDING_TOKEN_KEY = "pending_fcm_token";

    private final Context appContext;
    private final FirebaseAuth auth;
    private final FirebaseFirestore db;

    public FcmTokenService(Context context) {
        appContext = context.getApplicationContext();
        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
    }

    public void syncTokenForCurrentUser() {
        String pendingToken = getPrefs().getString(PENDING_TOKEN_KEY, null);
        if (pendingToken != null && !pendingToken.trim().isEmpty()) {
            storeTokenForCurrentUser(pendingToken);
            return;
        }

        FirebaseMessaging.getInstance()
                .getToken()
                .addOnSuccessListener(this::storeTokenForCurrentUser)
                .addOnFailureListener(exception ->
                        Log.w(TAG, "Unable to load FCM token", exception));
    }

    public void storeTokenForCurrentUser(String token) {
        if (token == null || token.trim().isEmpty()) {
            return;
        }

        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            savePendingToken(token);
            return;
        }

        Map<String, Object> fields = new HashMap<>();
        fields.put("fcmToken", token);
        fields.put("fcmTokens", FieldValue.arrayUnion(token));
        fields.put("fcmTokenUpdatedAtMs", System.currentTimeMillis());

        db.collection("players")
                .document(user.getUid())
                .set(fields, SetOptions.merge())
                .addOnSuccessListener(unused -> clearPendingToken(token))
                .addOnFailureListener(exception -> {
                    savePendingToken(token);
                    Log.w(TAG, "Unable to save FCM token", exception);
                });
    }

    private void savePendingToken(String token) {
        getPrefs()
                .edit()
                .putString(PENDING_TOKEN_KEY, token)
                .apply();
    }

    private void clearPendingToken(String token) {
        String pendingToken = getPrefs().getString(PENDING_TOKEN_KEY, null);
        if (token.equals(pendingToken)) {
            getPrefs()
                    .edit()
                    .remove(PENDING_TOKEN_KEY)
                    .apply();
        }
    }

    private SharedPreferences getPrefs() {
        return appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }
}
