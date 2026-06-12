package com.example.slagalica.Services;

import android.util.Log;

import com.example.slagalica.Model.Player;
import com.example.slagalica.Model.PlayerStatistics;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

public class PlayerService {

    public interface OnFailureCallback {
        void onFailure(String errorMessage);
    }

    private final FirebaseAuth auth;
    private final FirebaseFirestore db;

    public PlayerService() {
        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
    }

    public void registerPlayer(String email,
                               String username,
                               String region,
                               String password,
                               Runnable onSuccess,
                               OnFailureCallback onFailure) {

        db.collection("players")
                .whereEqualTo("username", username)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    if (!querySnapshot.isEmpty()) {
                        onFailure.onFailure("Username is already taken");
                        return;
                    }

                    createPlayer(email, username, region, password, onSuccess, onFailure);
                })
                .addOnFailureListener(e -> onFailure.onFailure(e.getMessage()));
    }

    private void createPlayer(String email,
                              String username,
                              String region,
                              String password,
                              Runnable onSuccess,
                              OnFailureCallback onFailure) {

        auth.createUserWithEmailAndPassword(email, password)
                .addOnSuccessListener(authResult -> {
                    FirebaseUser firebaseUser = authResult.getUser();

                    if (firebaseUser == null) {
                        onFailure.onFailure("Registration failed");
                        return;
                    }

                    Player player = new Player(
                            firebaseUser.getUid(),
                            email,
                            username,
                            region
                    );

                    player.setTokens(120);
                    player.setTotalStars(340);
                    player.setLeagueName("Bronze League");
                    player.setStatistics(createDummyStatistics());

                    db.collection("players")
                            .document(firebaseUser.getUid())
                            .set(player)
                            .addOnSuccessListener(unused -> sendVerificationEmail(firebaseUser, onSuccess, onFailure))
                            .addOnFailureListener(e -> onFailure.onFailure(e.getMessage()));
                })
                .addOnFailureListener(e -> onFailure.onFailure(e.getMessage()));
    }

    private void sendVerificationEmail(FirebaseUser firebaseUser,
                                       Runnable onSuccess,
                                       OnFailureCallback onFailure) {

        firebaseUser.sendEmailVerification()
                .addOnSuccessListener(unused -> {
                    Log.d("PlayerService", "Verification email sent");
                    auth.signOut();
                    onSuccess.run();
                })
                .addOnFailureListener(e -> {
                    onFailure.onFailure(e.getMessage());
                    Log.d("PlayerServie", "Verifiaction email failed");
                });
    }

    public void loginPlayer(String emailOrUsername,
                            String password,
                            Runnable onSuccess,
                            OnFailureCallback onFailure) {

        if (emailOrUsername.contains("@")) {
            signInWithEmail(emailOrUsername, password, onSuccess, onFailure);
        } else {
            db.collection("players")
                    .whereEqualTo("username", emailOrUsername)
                    .get()
                    .addOnSuccessListener(querySnapshot -> {
                        if (querySnapshot.isEmpty()) {
                            onFailure.onFailure("Player not found");
                            return;
                        }

                        String email = querySnapshot.getDocuments()
                                .get(0)
                                .getString("email");

                        if (email == null || email.isEmpty()) {
                            onFailure.onFailure("Player email not found");
                            return;
                        }

                        signInWithEmail(email, password, onSuccess, onFailure);
                    })
                    .addOnFailureListener(e -> onFailure.onFailure(e.getMessage()));
        }
    }

    public void updateAvatarBase64(String avatarBase64,
                                   Runnable onSuccess,
                                   OnFailureCallback onFailure) {

        FirebaseUser firebaseUser = auth.getCurrentUser();

        if (firebaseUser == null) {
            onFailure.onFailure("User is not logged in");
            return;
        }

        db.collection("players")
                .document(firebaseUser.getUid())
                .update("avatarBase64", avatarBase64)
                .addOnSuccessListener(unused -> onSuccess.run())
                .addOnFailureListener(e -> onFailure.onFailure(e.getMessage()));
    }
    private void signInWithEmail(String email,
                                 String password,
                                 Runnable onSuccess,
                                 OnFailureCallback onFailure) {

        auth.signInWithEmailAndPassword(email, password)
                .addOnSuccessListener(authResult -> {
                    FirebaseUser firebaseUser = auth.getCurrentUser();

                    if (firebaseUser == null) {
                        onFailure.onFailure("Login failed");
                        return;
                    }

                    firebaseUser.reload()
                            .addOnSuccessListener(unused -> {
                                if (firebaseUser.isEmailVerified()) {
                                    onSuccess.run();
                                } else {
                                    auth.signOut();
                                    onFailure.onFailure("Please verify your email before logging in");
                                }
                            })
                            .addOnFailureListener(e -> onFailure.onFailure(e.getMessage()));
                })
                .addOnFailureListener(e -> onFailure.onFailure(e.getMessage()));
    }

    private PlayerStatistics createDummyStatistics() {
        PlayerStatistics statistics = new PlayerStatistics();

        statistics.setKoZnaZnaAverageScore(75.5);
        statistics.setKoZnaZnaHitMissRatio(0.78);

        statistics.setMojBrojSuccessPercentage(62.0);

        statistics.setKorakPoKorakStep1Percentage(90.0);
        statistics.setKorakPoKorakStep2Percentage(82.0);
        statistics.setKorakPoKorakStep3Percentage(74.0);
        statistics.setKorakPoKorakStep4Percentage(65.0);
        statistics.setKorakPoKorakStep5Percentage(53.0);
        statistics.setKorakPoKorakStep6Percentage(41.0);
        statistics.setKorakPoKorakFinalPercentage(35.0);

        statistics.setAsocijacijeSolvedRatio(0.57);

        statistics.setSkockoAttempt1Percentage(12.0);
        statistics.setSkockoAttempt2Percentage(24.0);
        statistics.setSkockoAttempt3Percentage(39.0);
        statistics.setSkockoAttempt4Percentage(48.0);
        statistics.setSkockoAttempt5Percentage(61.0);
        statistics.setSkockoAttempt6Percentage(70.0);

        statistics.setSpojniceSuccessPercentage(84.0);

        statistics.setTotalGamesPlayed(120);
        statistics.setTotalGamesWon(73);
        statistics.setTotalGamesLost(47);

        return statistics;
    }
}