package com.example.slagalica.Fragments;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.util.Base64;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import com.example.slagalica.Activities.MainActivity;
import com.example.slagalica.Model.Player;
import com.example.slagalica.Model.PlayerStatistics;
import com.example.slagalica.R;
import com.example.slagalica.Services.PlayerService;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import android.graphics.Color;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.common.BitMatrix;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.Locale;

public class ProfileFragment extends Fragment {

    private TextView usernameText;
    private TextView emailText;
    private TextView regionText;
    private TextView tokensText;
    private TextView starsText;
    private TextView leagueText;

    private TextView gameSuccessText;
    private TextView koZnaZnaText;
    private TextView mojBrojText;
    private TextView korakPoKorakText;
    private TextView asocijacijeText;
    private TextView skockoText;
    private TextView spojniceText;
    private TextView playedGamesText;
    private TextView winsLossesText;

    private ImageView avatarImage;
    private Button changeAvatarButton;
    private Button logoutButton;
    private ImageButton menuButton;

    private ImageView qrCodeImage;

    private FirebaseAuth auth;
    private FirebaseFirestore db;
    private PlayerService playerService;

    private ActivityResultLauncher<String> imagePickerLauncher;

    public ProfileFragment() {
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        playerService = new PlayerService();

        imagePickerLauncher = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                this::handleSelectedImage
        );
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.fragment_profile, container, false);

        usernameText = view.findViewById(R.id.usernameText);
        emailText = view.findViewById(R.id.emailText);
        regionText = view.findViewById(R.id.regionText);
        tokensText = view.findViewById(R.id.tokensText);
        starsText = view.findViewById(R.id.starsText);
        leagueText = view.findViewById(R.id.leagueText);

        gameSuccessText = view.findViewById(R.id.gameSuccessText);
        koZnaZnaText = view.findViewById(R.id.koZnaZnaText);
        mojBrojText = view.findViewById(R.id.mojBrojText);
        korakPoKorakText = view.findViewById(R.id.korakPoKorakText);
        asocijacijeText = view.findViewById(R.id.asocijacijeText);
        skockoText = view.findViewById(R.id.skockoText);
        spojniceText = view.findViewById(R.id.spojniceText);
        playedGamesText = view.findViewById(R.id.playedGamesText);
        winsLossesText = view.findViewById(R.id.winsLossesText);

        avatarImage = view.findViewById(R.id.avatarImage);
        changeAvatarButton = view.findViewById(R.id.changeAvatarButton);

        logoutButton = view.findViewById(R.id.logoutButton);
        menuButton = view.findViewById(R.id.menuButton);

        menuButton.setVisibility(View.VISIBLE);

        qrCodeImage = view.findViewById(R.id.qrCodeImage);
        generatePlaceholderQrCode();

        loadCurrentPlayer();

        changeAvatarButton.setOnClickListener(v -> {
            imagePickerLauncher.launch("image/*");
        });

        logoutButton.setOnClickListener(v -> logout());

        menuButton.setOnClickListener(v -> {
            ((MainActivity) requireActivity()).toggleNavbar();
        });

        return view;
    }

    private void loadCurrentPlayer() {
        FirebaseUser firebaseUser = auth.getCurrentUser();

        if (firebaseUser == null) {
            navigateToLogin();
            return;
        }

        db.collection("players")
                .document(firebaseUser.getUid())
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (!documentSnapshot.exists()) {
                        Toast.makeText(requireContext(), "Player profile not found", Toast.LENGTH_LONG).show();
                        return;
                    }

                    Player player = documentSnapshot.toObject(Player.class);

                    if (player == null) {
                        Toast.makeText(requireContext(), "Failed to load player profile", Toast.LENGTH_LONG).show();
                        return;
                    }

                    displayPlayer(player);
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(requireContext(), e.getMessage(), Toast.LENGTH_LONG).show();
                });
    }

    private void displayPlayer(Player player) {
        usernameText.setText(player.getUsername());
        emailText.setText(player.getEmail());
        regionText.setText(player.getRegion());

        tokensText.setText(String.valueOf(player.getTokens()));
        starsText.setText(String.valueOf(player.getTotalStars()));

        if (player.getLeagueName() != null && !player.getLeagueName().isEmpty()) {
            leagueText.setText(player.getLeagueName());
        } else {
            leagueText.setText("No league");
        }

        displayAvatar(player.getAvatarBase64());
        displayStatistics(player.getStatistics());
    }

    private void displayStatistics(PlayerStatistics stats) {
        if (stats == null) {
            gameSuccessText.setText("Uspešnost po igrama: nema podataka");
            koZnaZnaText.setText("Ko zna zna: nema podataka");
            mojBrojText.setText("Moj broj: nema podataka");
            korakPoKorakText.setText("Korak po korak: nema podataka");
            asocijacijeText.setText("Asocijacije: nema podataka");
            skockoText.setText("Skočko: nema podataka");
            spojniceText.setText("Spojnice: nema podataka");
            playedGamesText.setText("Ukupan broj odigranih partija: 0");
            winsLossesText.setText("Pobede / Porazi: 0 / 0");
            return;
        }

        gameSuccessText.setText(String.format(
                Locale.getDefault(),
                "Uspešnost po igrama: prosečno %.1f poena",
                stats.getKoZnaZnaAverageScore()
        ));

        koZnaZnaText.setText(String.format(
                Locale.getDefault(),
                "Ko zna zna: odnos pogođenih i promašenih %.2f",
                stats.getKoZnaZnaHitMissRatio()
        ));

        mojBrojText.setText(String.format(
                Locale.getDefault(),
                "Moj broj: %.1f%% pronađen tačan broj",
                stats.getMojBrojSuccessPercentage()
        ));

        korakPoKorakText.setText(String.format(
                Locale.getDefault(),
                "Korak po korak: 1: %.1f%%, 2: %.1f%%, 3: %.1f%%, final: %.1f%%",
                stats.getKorakPoKorakStep1Percentage(),
                stats.getKorakPoKorakStep2Percentage(),
                stats.getKorakPoKorakStep3Percentage(),
                stats.getKorakPoKorakFinalPercentage()
        ));

        asocijacijeText.setText(String.format(
                Locale.getDefault(),
                "Asocijacije: odnos rešenih %.2f",
                stats.getAsocijacijeSolvedRatio()
        ));

        skockoText.setText(String.format(
                Locale.getDefault(),
                "Skočko: pokušaji 1: %.1f%%, 2: %.1f%%, 3: %.1f%%, 6: %.1f%%",
                stats.getSkockoAttempt1Percentage(),
                stats.getSkockoAttempt2Percentage(),
                stats.getSkockoAttempt3Percentage(),
                stats.getSkockoAttempt6Percentage()
        ));

        spojniceText.setText(String.format(
                Locale.getDefault(),
                "Spojnice: %.1f%% uspešno povezanih pojmova",
                stats.getSpojniceSuccessPercentage()
        ));

        playedGamesText.setText(String.format(
                Locale.getDefault(),
                "Ukupan broj odigranih partija: %d",
                stats.getTotalGamesPlayed()
        ));

        winsLossesText.setText(String.format(
                Locale.getDefault(),
                "Pobede / Porazi: %d / %d, uspešnost: %.1f%%",
                stats.getTotalGamesWon(),
                stats.getTotalGamesLost(),
                stats.getWinPercentage()
        ));
    }

    private void handleSelectedImage(Uri imageUri) {
        if (imageUri == null) {
            return;
        }

        String avatarBase64 = imageToBase64(imageUri);

        if (avatarBase64 == null) {
            Toast.makeText(requireContext(), "Failed to read image", Toast.LENGTH_LONG).show();
            return;
        }

        changeAvatarButton.setEnabled(false);

        playerService.updateAvatarBase64(
                avatarBase64,
                () -> {
                    changeAvatarButton.setEnabled(true);
                    Toast.makeText(requireContext(), "Avatar updated", Toast.LENGTH_SHORT).show();
                    loadCurrentPlayer();
                },
                errorMessage -> {
                    changeAvatarButton.setEnabled(true);
                    Toast.makeText(requireContext(), errorMessage, Toast.LENGTH_LONG).show();
                }
        );
    }

    private String imageToBase64(Uri imageUri) {
        try {
            InputStream inputStream = requireContext()
                    .getContentResolver()
                    .openInputStream(imageUri);

            Bitmap originalBitmap = BitmapFactory.decodeStream(inputStream);

            if (originalBitmap == null) {
                return null;
            }

            Bitmap resizedBitmap = Bitmap.createScaledBitmap(
                    originalBitmap,
                    256,
                    256,
                    true
            );

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

            resizedBitmap.compress(
                    Bitmap.CompressFormat.JPEG,
                    50,
                    outputStream
            );

            byte[] bytes = outputStream.toByteArray();

            return Base64.encodeToString(bytes, Base64.DEFAULT);

        } catch (Exception e) {
            return null;
        }
    }

    private void displayAvatar(String avatarBase64) {
        if (avatarBase64 == null || avatarBase64.isEmpty()) {
            avatarImage.setImageResource(R.mipmap.ic_launcher);
            return;
        }

        try {
            byte[] imageBytes = Base64.decode(avatarBase64, Base64.DEFAULT);

            Bitmap bitmap = BitmapFactory.decodeByteArray(
                    imageBytes,
                    0,
                    imageBytes.length
            );

            avatarImage.setImageBitmap(bitmap);

        } catch (Exception e) {
            avatarImage.setImageResource(R.mipmap.ic_launcher);
        }
    }

    private void logout() {
        auth.signOut();
        navigateToLogin();
    }

    private void navigateToLogin() {
        requireActivity()
                .getSupportFragmentManager()
                .popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE);

        requireActivity()
                .getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragmentContainer, new LoginFragment())
                .commit();
    }

    private void generatePlaceholderQrCode() {
        try {
            String dummyText = "SLAGALICA_PLAYER_PLACEHOLDER";

            BitMatrix bitMatrix = new MultiFormatWriter().encode(
                    dummyText,
                    BarcodeFormat.QR_CODE,
                    300,
                    300
            );

            Bitmap bitmap = Bitmap.createBitmap(300, 300, Bitmap.Config.RGB_565);

            for (int x = 0; x < 300; x++) {
                for (int y = 0; y < 300; y++) {
                    bitmap.setPixel(x, y, bitMatrix.get(x, y) ? Color.BLACK : Color.WHITE);
                }
            }

            qrCodeImage.setImageBitmap(bitmap);

        } catch (Exception e) {
            qrCodeImage.setImageResource(R.mipmap.ic_launcher);
        }
    }
}