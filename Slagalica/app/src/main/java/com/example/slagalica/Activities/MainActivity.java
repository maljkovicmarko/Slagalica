package com.example.slagalica.Activities;

import android.app.AlertDialog;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.graphics.Insets;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.slagalica.Fragments.GeneralKnowledgeFragment;
import com.example.slagalica.Fragments.FriendsFragment;
import com.example.slagalica.Fragments.LoginFragment;
import com.example.slagalica.Model.LeaderboardRewardNotification;
import com.example.slagalica.R;
import com.example.slagalica.Services.ActiveSessionTracker;
import com.example.slagalica.Services.FcmTokenService;
import com.example.slagalica.Services.FriendService;
import com.example.slagalica.Services.LeaderboardRewardService;
import com.example.slagalica.Services.SessionSnapshot;
import com.example.slagalica.Services.SlagalicaFirebaseMessagingService;
import com.example.slagalica.Services.WebSocketConfig;
import com.example.slagalica.Services.WebSocketGameClient;
import com.example.slagalica.Util.FriendQrCode;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import org.json.JSONObject;

import java.util.ArrayDeque;
import java.util.Deque;

public class MainActivity extends AppCompatActivity {
    private WebSocketGameClient webSocketGameClient;
    private FriendService friendService;
    private WebSocketGameClient.ListenerHandle friendInviteListenerHandle;
    private AlertDialog friendInviteDialog;
    private String visibleInviteId;
    private LeaderboardRewardService leaderboardRewardService;
    private FcmTokenService fcmTokenService;
    private final Deque<LeaderboardRewardNotification> pendingRewards = new ArrayDeque<>();
    private AlertDialog rewardDialog;
    private boolean rewardCheckInProgress;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        webSocketGameClient = WebSocketGameClient.getInstance();
        friendService = new FriendService();
        leaderboardRewardService = new LeaderboardRewardService();
        fcmTokenService = new FcmTokenService(this);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        if (savedInstanceState == null) {
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.fragmentContainer, new LoginFragment())
                    .commit();
        }

        View overlay = findViewById(R.id.navbarOverlay);
        overlay.setOnClickListener(v -> {
            System.out.println("Overlay clicked");
            toggleNavbar();
        });

        registerFriendInviteListener();
        requestNotificationPermissionIfNeeded();
        handleFriendQrIntent(getIntent());
        handleLeaderboardRewardIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleFriendQrIntent(intent);
        handleLeaderboardRewardIntent(intent);
    }

    @Override
    protected void onStart() {
        super.onStart();
        connectGameSocketForCurrentUser();
        syncFcmTokenForCurrentUser();
        checkForLeaderboardRewards();
    }

    @Override
    protected void onDestroy() {
        clearFriendInviteListener();
        dismissFriendInviteDialog();
        dismissRewardDialog();
        super.onDestroy();
    }

    public void connectGameSocketForCurrentUser() {
        FirebaseUser firebaseUser = FirebaseAuth.getInstance().getCurrentUser();
        if (firebaseUser == null) {
            return;
        }

        webSocketGameClient.setServerUrl(WebSocketConfig.getServerUrl(this));
        webSocketGameClient.connect(firebaseUser.getUid(), new WebSocketGameClient.OnConnected() {
            @Override
            public void onConnected() {
            }

            @Override
            public void onFailure(String errorMessage) {
            }
        });
    }

    public void syncFcmTokenForCurrentUser() {
        fcmTokenService.syncTokenForCurrentUser();
    }

    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return;
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED) {
            return;
        }

        ActivityCompat.requestPermissions(
                this,
                new String[]{Manifest.permission.POST_NOTIFICATIONS},
                2001
        );
    }

    private void handleLeaderboardRewardIntent(Intent intent) {
        if (intent == null) {
            return;
        }
        if (SlagalicaFirebaseMessagingService.ACTION_LEADERBOARD_REWARD.equals(intent.getAction())) {
            checkForLeaderboardRewards();
        }
    }

    public void checkForLeaderboardRewards() {
        if (rewardCheckInProgress || rewardDialog != null || !pendingRewards.isEmpty()) {
            return;
        }
        rewardCheckInProgress = true;
        leaderboardRewardService.loadUnseenRewards(
                rewards -> {
                    rewardCheckInProgress = false;
                    if (isFinishing() || isDestroyed()) {
                        return;
                    }
                    pendingRewards.addAll(rewards);
                    showNextReward();
                },
                errorMessage -> {
                    rewardCheckInProgress = false;
                    if (!isFinishing()) {
                        Toast.makeText(this, errorMessage, Toast.LENGTH_LONG).show();
                    }
                }
        );
    }

    private void showNextReward() {
        if (rewardDialog != null || pendingRewards.isEmpty() || isFinishing() || isDestroyed()) {
            return;
        }

        LeaderboardRewardNotification reward = pendingRewards.removeFirst();
        View content = LayoutInflater.from(this).inflate(R.layout.dialog_leaderboard_reward, null, false);
        ImageView rewardIcon = content.findViewById(R.id.rewardIcon);
        TextView cycleText = content.findViewById(R.id.rewardCycleText);
        TextView rankText = content.findViewById(R.id.rewardRankText);
        TextView tokensText = content.findViewById(R.id.rewardTokensText);

        cycleText.setText("monthly".equalsIgnoreCase(reward.getCycleType())
                ? R.string.leaderboard_reward_monthly
                : R.string.leaderboard_reward_weekly);
        rankText.setText(getString(R.string.leaderboard_reward_rank, reward.getRank()));
        tokensText.setText(getString(R.string.leaderboard_reward_tokens, reward.getTokens()));

        rewardDialog = new AlertDialog.Builder(this)
                .setView(content)
                .setCancelable(false)
                .setPositiveButton(R.string.leaderboard_reward_accept, (dialog, which) ->
                        leaderboardRewardService.markSeen(
                                reward.getId(),
                                () -> { },
                                errorMessage -> Toast.makeText(this, errorMessage, Toast.LENGTH_LONG).show()
                        ))
                .create();
        rewardDialog.setOnShowListener(dialog -> {
            playRewardAnimation(rewardIcon);
            playRewardSound();
        });
        rewardDialog.setOnDismissListener(dialog -> {
            rewardDialog = null;
            showNextReward();
        });
        rewardDialog.show();
    }

    private void playRewardAnimation(ImageView rewardIcon) {
        rewardIcon.setScaleX(0.25f);
        rewardIcon.setScaleY(0.25f);
        ObjectAnimator scaleX = ObjectAnimator.ofFloat(rewardIcon, View.SCALE_X, 0.25f, 1.2f, 1f);
        ObjectAnimator scaleY = ObjectAnimator.ofFloat(rewardIcon, View.SCALE_Y, 0.25f, 1.2f, 1f);
        ObjectAnimator rotation = ObjectAnimator.ofFloat(rewardIcon, View.ROTATION, 0f, 360f);
        AnimatorSet animation = new AnimatorSet();
        animation.setDuration(850L);
        animation.playTogether(scaleX, scaleY, rotation);
        animation.start();
    }

    private void playRewardSound() {
        try {
            ToneGenerator toneGenerator = new ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80);
            toneGenerator.startTone(ToneGenerator.TONE_PROP_ACK, 450);
            new Handler(Looper.getMainLooper()).postDelayed(toneGenerator::release, 600L);
        } catch (RuntimeException exception) {
            android.util.Log.w("MainActivity", "Reward sound is unavailable", exception);
        }
    }

    private void dismissRewardDialog() {
        if (rewardDialog != null) {
            rewardDialog.setOnDismissListener(null);
            rewardDialog.dismiss();
            rewardDialog = null;
        }
        pendingRewards.clear();
    }

    public void toggleNavbar() {
        View navbar = findViewById(R.id.navbarFragment);
        View overlay = findViewById(R.id.navbarOverlay);

        System.out.println("Navbar visibility: " + navbar.getVisibility());
        System.out.println("Overlay visibility: " + overlay.getVisibility());

        if (navbar.getVisibility() == View.VISIBLE) {
            navbar.setVisibility(View.GONE);
            overlay.setVisibility(View.GONE);
        } else {
            overlay.setVisibility(View.VISIBLE);
            navbar.setVisibility(View.VISIBLE);

            overlay.bringToFront();
            navbar.bringToFront();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();

        if (isChangingConfigurations()) {
            return;
        }

        String activeSessionId = ActiveSessionTracker.getActiveSessionId();
        if (activeSessionId == null) {
            return;
        }

        ActiveSessionTracker.clearActiveSession(activeSessionId);
        WebSocketGameClient.getInstance().abandonSession(activeSessionId, new WebSocketGameClient.OnRequestResult() {
            @Override
            public void onSuccess(org.json.JSONObject data) {
            }

            @Override
            public void onFailure(String errorMessage) {
            }
        });
    }

    private void registerFriendInviteListener() {
        clearFriendInviteListener();
        friendInviteListenerHandle = webSocketGameClient.addFriendInviteListener(new WebSocketGameClient.OnFriendInviteListener() {
            @Override
            public void onInviteReceived(WebSocketGameClient.FriendInvite invite) {
                showFriendInviteDialog(invite);
            }

            @Override
            public void onInviteAccepted(WebSocketGameClient.FriendInvite invite, SessionSnapshot session) {
                dismissFriendInviteDialog(invite.getInviteId());
                openFriendlySession(session);
            }

            @Override
            public void onInviteRejected(WebSocketGameClient.FriendInvite invite) {
                Toast.makeText(MainActivity.this, R.string.friend_invite_rejected, Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onInviteCancelled(WebSocketGameClient.FriendInvite invite) {
                dismissFriendInviteDialog(invite.getInviteId());
                Toast.makeText(MainActivity.this, R.string.friend_invite_cancelled, Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onInviteExpired(WebSocketGameClient.FriendInvite invite) {
                dismissFriendInviteDialog(invite.getInviteId());
                Toast.makeText(MainActivity.this, R.string.friend_invite_expired, Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onFailure(String errorMessage) {
            }
        });
    }

    private void clearFriendInviteListener() {
        if (friendInviteListenerHandle != null) {
            friendInviteListenerHandle.remove();
            friendInviteListenerHandle = null;
        }
    }

    private void showFriendInviteDialog(WebSocketGameClient.FriendInvite invite) {
        if (invite == null || invite.getInviteId() == null || isFinishing()) {
            return;
        }

        dismissFriendInviteDialog();
        visibleInviteId = invite.getInviteId();
        friendInviteDialog = new AlertDialog.Builder(this)
                .setTitle(R.string.friend_invite_title)
                .setMessage(R.string.friend_invite_message)
                .setPositiveButton(R.string.accept_friend_invite, (dialog, which) -> acceptFriendInvite(invite.getInviteId()))
                .setNegativeButton(R.string.reject_friend_invite, (dialog, which) -> rejectFriendInvite(invite.getInviteId()))
                .create();
        friendInviteDialog.setOnDismissListener(dialog -> {
            if (invite.getInviteId().equals(visibleInviteId)) {
                visibleInviteId = null;
                friendInviteDialog = null;
            }
        });
        friendInviteDialog.show();
    }

    private void acceptFriendInvite(String inviteId) {
        webSocketGameClient.acceptFriendInvite(inviteId, new WebSocketGameClient.OnRequestResult() {
            @Override
            public void onSuccess(JSONObject data) {
            }

            @Override
            public void onFailure(String errorMessage) {
                Toast.makeText(MainActivity.this, errorMessage, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void rejectFriendInvite(String inviteId) {
        webSocketGameClient.rejectFriendInvite(inviteId, new WebSocketGameClient.OnRequestResult() {
            @Override
            public void onSuccess(JSONObject data) {
            }

            @Override
            public void onFailure(String errorMessage) {
                Toast.makeText(MainActivity.this, errorMessage, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void openFriendlySession(SessionSnapshot session) {
        if (session == null || isFinishing()) {
            return;
        }

        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragmentContainer, GeneralKnowledgeFragment.newInstance(session.toJson().toString()))
                .commit();
    }

    private void handleFriendQrIntent(Intent intent) {
        if (intent == null || intent.getData() == null) {
            return;
        }

        String friendUid = FriendQrCode.extractPlayerUid(intent.getData().toString());
        if (friendUid == null) {
            return;
        }

        friendService.addFriend(
                friendUid,
                () -> {
                    Toast.makeText(this, R.string.friend_added, Toast.LENGTH_SHORT).show();
                    getSupportFragmentManager()
                            .beginTransaction()
                            .replace(R.id.fragmentContainer, new FriendsFragment())
                            .addToBackStack(null)
                            .commit();
                },
                errorMessage -> Toast.makeText(this, errorMessage, Toast.LENGTH_LONG).show()
        );
    }

    private void dismissFriendInviteDialog(String inviteId) {
        if (inviteId == null || inviteId.equals(visibleInviteId)) {
            dismissFriendInviteDialog();
        }
    }

    private void dismissFriendInviteDialog() {
        if (friendInviteDialog != null) {
            friendInviteDialog.dismiss();
            friendInviteDialog = null;
        }
        visibleInviteId = null;
    }
}
