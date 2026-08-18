package com.example.slagalica.wsserver;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.firestore.Firestore;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.cloud.FirestoreClient;

import java.io.IOException;

public final class FirebaseAdmin {
    private static boolean initialized;

    private FirebaseAdmin() {
    }

    public static synchronized Firestore getFirestore() throws IOException {
        if (!initialized) {
            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.getApplicationDefault())
                    .build();

            FirebaseApp.initializeApp(options);
            initialized = true;
        }

        return FirestoreClient.getFirestore();
    }
}
