package com.example.slagalica.wsserver;

import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.SetOptions;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;

public class RankedPlayerProgressService {
    private static final String PLAYERS_COLLECTION = "players";
    private static final String TOKENS_FIELD = "tokens";
    private static final String TOTAL_STARS_FIELD = "totalStars";
    private static final String LAST_DAILY_TOKEN_GRANT_AT_MS_FIELD = "lastDailyTokenGrantAtMs";
    private static final int DAILY_TOKEN_GRANT = 5;
    private static final int TOKEN_COST_PER_RANKED_MATCH = 1;
    private static final int STARS_PER_TOKEN = 50;

    public TokenCheckResult canJoinRankedQueue(String uid) {
        try {
            Firestore firestore = FirebaseAdmin.getFirestore();
            DocumentReference playerRef = firestore.collection(PLAYERS_COLLECTION).document(uid);

            return firestore.runTransaction(transaction -> {
                DocumentSnapshot player = transaction.get(playerRef).get();
                if (!player.exists()) {
                    return TokenCheckResult.rejected("Igrač ne postoji u players kolekciji.");
                }

                PlayerProgress progress = readProgress(player);
                GrantResult grantResult = grantDailyTokensIfNeeded(progress);

                Map<String, Object> updates = new HashMap<>();
                if (grantResult.changed) {
                    updates.put(TOKENS_FIELD, grantResult.tokens);
                    updates.put(LAST_DAILY_TOKEN_GRANT_AT_MS_FIELD, grantResult.lastDailyTokenGrantAtMs);
                    transaction.set(playerRef, updates, SetOptions.merge());
                }

                if (grantResult.tokens < TOKEN_COST_PER_RANKED_MATCH) {
                    return TokenCheckResult.rejected("Nemaš dovoljno tokena za ranked partiju.");
                }
                return TokenCheckResult.accepted(grantResult.tokens);
            }).get();
        } catch (Exception exception) {
            System.err.println("Failed to check ranked tokens for player " + uid + ": " + exception.getMessage());
            return TokenCheckResult.rejected("Nije moguće proveriti tokene.");
        }
    }

    public TokenSpendResult spendRankedTokens(String player1Uid, String player2Uid) {
        try {
            Firestore firestore = FirebaseAdmin.getFirestore();
            DocumentReference player1Ref = firestore.collection(PLAYERS_COLLECTION).document(player1Uid);
            DocumentReference player2Ref = firestore.collection(PLAYERS_COLLECTION).document(player2Uid);

            return firestore.runTransaction(transaction -> {
                DocumentSnapshot player1 = transaction.get(player1Ref).get();
                DocumentSnapshot player2 = transaction.get(player2Ref).get();
                if (!player1.exists()) {
                    return TokenSpendResult.rejected(player1Uid, "Prvi igrač ne postoji u players kolekciji.");
                }
                if (!player2.exists()) {
                    return TokenSpendResult.rejected(player2Uid, "Drugi igrač ne postoji u players kolekciji.");
                }

                PlayerProgress player1Progress = readProgress(player1);
                PlayerProgress player2Progress = readProgress(player2);
                GrantResult player1Grant = grantDailyTokensIfNeeded(player1Progress);
                GrantResult player2Grant = grantDailyTokensIfNeeded(player2Progress);

                if (player1Grant.tokens < TOKEN_COST_PER_RANKED_MATCH) {
                    writeDailyGrantIfNeeded(transaction, player1Ref, player1Grant);
                    return TokenSpendResult.rejected(player1Uid, "Prvi igrač nema dovoljno tokena.");
                }
                if (player2Grant.tokens < TOKEN_COST_PER_RANKED_MATCH) {
                    writeDailyGrantIfNeeded(transaction, player2Ref, player2Grant);
                    return TokenSpendResult.rejected(player2Uid, "Drugi igrač nema dovoljno tokena.");
                }

                Map<String, Object> player1Updates = buildTokenUpdate(player1Grant.tokens - TOKEN_COST_PER_RANKED_MATCH, player1Grant);
                Map<String, Object> player2Updates = buildTokenUpdate(player2Grant.tokens - TOKEN_COST_PER_RANKED_MATCH, player2Grant);
                transaction.set(player1Ref, player1Updates, SetOptions.merge());
                transaction.set(player2Ref, player2Updates, SetOptions.merge());
                return TokenSpendResult.accepted();
            }).get();
        } catch (Exception exception) {
            System.err.println("Failed to spend ranked tokens: " + exception.getMessage());
            return TokenSpendResult.rejected(null, "Nije moguće potrošiti tokene.");
        }
    }

    public RewardResult applyRankedResult(SessionState session) {
        if (session == null || !"ranked".equals(session.getSessionType()) || session.isRankedRewardsApplied()) {
            return RewardResult.notApplied();
        }

        try {
            Firestore firestore = FirebaseAdmin.getFirestore();
            DocumentReference player1Ref = firestore.collection(PLAYERS_COLLECTION).document(session.getPlayer1Uid());
            DocumentReference player2Ref = firestore.collection(PLAYERS_COLLECTION).document(session.getPlayer2Uid());

            RewardResult rewardResult = firestore.runTransaction(transaction -> {
                DocumentSnapshot player1 = transaction.get(player1Ref).get();
                DocumentSnapshot player2 = transaction.get(player2Ref).get();
                if (!player1.exists() || !player2.exists()) {
                    return RewardResult.failed("Jedan od igrača ne postoji u players kolekciji.");
                }

                int player1Stars = readInt(player1, TOTAL_STARS_FIELD, 0);
                int player2Stars = readInt(player2, TOTAL_STARS_FIELD, 0);
                int player1Tokens = readInt(player1, TOKENS_FIELD, 0);
                int player2Tokens = readInt(player2, TOKENS_FIELD, 0);

                ScoreReward player1Reward = calculateScoreReward(
                        session.getPlayer1Uid(),
                        session.getPlayer1Score(),
                        session.getPlayer2Score(),
                        session.getWinnerUid(),
                        session.getAbandonedByUid()
                );
                ScoreReward player2Reward = calculateScoreReward(
                        session.getPlayer2Uid(),
                        session.getPlayer2Score(),
                        session.getPlayer1Score(),
                        session.getWinnerUid(),
                        session.getAbandonedByUid()
                );

                ConvertedProgress player1Converted = applyStarsAndConvertTokens(
                        player1Stars,
                        player1Tokens,
                        player1Reward.starDelta
                );
                ConvertedProgress player2Converted = applyStarsAndConvertTokens(
                        player2Stars,
                        player2Tokens,
                        player2Reward.starDelta
                );

                transaction.set(player1Ref, buildRewardUpdate(player1Converted), SetOptions.merge());
                transaction.set(player2Ref, buildRewardUpdate(player2Converted), SetOptions.merge());

                return RewardResult.applied(player1Reward.starDelta, player2Reward.starDelta);
            }).get();

            session.setRankedRewardsApplied(true);
            return rewardResult;
        } catch (Exception exception) {
            System.err.println("Failed to apply ranked rewards for session " + session.getSessionId() + ": " + exception.getMessage());
            return RewardResult.failed("Nije moguće upisati rezultate ranked partije.");
        }
    }

    private void writeDailyGrantIfNeeded(com.google.cloud.firestore.Transaction transaction,
                                         DocumentReference playerRef,
                                         GrantResult grantResult) {
        if (!grantResult.changed) {
            return;
        }
        Map<String, Object> updates = new HashMap<>();
        updates.put(TOKENS_FIELD, grantResult.tokens);
        updates.put(LAST_DAILY_TOKEN_GRANT_AT_MS_FIELD, grantResult.lastDailyTokenGrantAtMs);
        transaction.set(playerRef, updates, SetOptions.merge());
    }

    private Map<String, Object> buildTokenUpdate(int tokens, GrantResult grantResult) {
        Map<String, Object> updates = new HashMap<>();
        updates.put(TOKENS_FIELD, tokens);
        if (grantResult.changed) {
            updates.put(LAST_DAILY_TOKEN_GRANT_AT_MS_FIELD, grantResult.lastDailyTokenGrantAtMs);
        }
        return updates;
    }

    private Map<String, Object> buildRewardUpdate(ConvertedProgress progress) {
        Map<String, Object> updates = new HashMap<>();
        updates.put(TOTAL_STARS_FIELD, progress.stars);
        updates.put(TOKENS_FIELD, progress.tokens);
        return updates;
    }

    private PlayerProgress readProgress(DocumentSnapshot snapshot) {
        return new PlayerProgress(
                readInt(snapshot, TOKENS_FIELD, 0),
                readLong(snapshot, LAST_DAILY_TOKEN_GRANT_AT_MS_FIELD, 0L)
        );
    }

    private GrantResult grantDailyTokensIfNeeded(PlayerProgress progress) {
        long nowMs = System.currentTimeMillis();
        if (progress.lastDailyTokenGrantAtMs <= 0L) {
            return new GrantResult(progress.tokens, nowMs, true);
        }
        if (!shouldGrantDailyTokens(progress.lastDailyTokenGrantAtMs, nowMs)) {
            return new GrantResult(progress.tokens, progress.lastDailyTokenGrantAtMs, false);
        }
        return new GrantResult(progress.tokens + DAILY_TOKEN_GRANT, nowMs, true);
    }

    private boolean shouldGrantDailyTokens(long lastGrantAtMs, long nowMs) {
        if (lastGrantAtMs <= 0L) {
            return true;
        }

        ZoneId zoneId = ZoneId.systemDefault();
        LocalDate lastGrantDate = Instant.ofEpochMilli(lastGrantAtMs).atZone(zoneId).toLocalDate();
        LocalDate today = Instant.ofEpochMilli(nowMs).atZone(zoneId).toLocalDate();
        return lastGrantDate.isBefore(today);
    }

    private ScoreReward calculateScoreReward(String playerUid,
                                             int playerScore,
                                             int opponentScore,
                                             String winnerUid,
                                             String abandonedByUid) {
        if (playerUid != null && playerUid.equals(abandonedByUid)) {
            return new ScoreReward(0);
        }

        int scoreBonus = Math.max(0, playerScore) / 40;
        if (winnerUid != null) {
            return new ScoreReward(playerUid.equals(winnerUid) ? 10 + scoreBonus : scoreBonus - 10);
        }

        if (playerScore > opponentScore) {
            return new ScoreReward(10 + scoreBonus);
        }
        if (playerScore < opponentScore) {
            return new ScoreReward(scoreBonus - 10);
        }
        return new ScoreReward(scoreBonus);
    }

    private ConvertedProgress applyStarsAndConvertTokens(int currentStars, int currentTokens, int starDelta) {
        int updatedStars = Math.max(0, currentStars + starDelta);
        int earnedTokens = updatedStars / STARS_PER_TOKEN;
        int remainingStars = updatedStars % STARS_PER_TOKEN;
        return new ConvertedProgress(remainingStars, currentTokens + earnedTokens);
    }

    private int readInt(DocumentSnapshot snapshot, String field, int defaultValue) {
        if (snapshot == null || !snapshot.exists()) {
            return defaultValue;
        }
        Object value = snapshot.get(field);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return defaultValue;
    }

    private long readLong(DocumentSnapshot snapshot, String field, long defaultValue) {
        if (snapshot == null || !snapshot.exists()) {
            return defaultValue;
        }
        Object value = snapshot.get(field);
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        return defaultValue;
    }

    public static final class TokenCheckResult {
        private final boolean accepted;
        private final int tokens;
        private final String errorMessage;

        private TokenCheckResult(boolean accepted, int tokens, String errorMessage) {
            this.accepted = accepted;
            this.tokens = tokens;
            this.errorMessage = errorMessage;
        }

        public static TokenCheckResult accepted(int tokens) {
            return new TokenCheckResult(true, tokens, null);
        }

        public static TokenCheckResult rejected(String errorMessage) {
            return new TokenCheckResult(false, 0, errorMessage);
        }

        public boolean isAccepted() {
            return accepted;
        }

        public int getTokens() {
            return tokens;
        }

        public String getErrorMessage() {
            return errorMessage;
        }
    }

    public static final class TokenSpendResult {
        private final boolean accepted;
        private final String rejectedPlayerUid;
        private final String errorMessage;

        private TokenSpendResult(boolean accepted, String rejectedPlayerUid, String errorMessage) {
            this.accepted = accepted;
            this.rejectedPlayerUid = rejectedPlayerUid;
            this.errorMessage = errorMessage;
        }

        public static TokenSpendResult accepted() {
            return new TokenSpendResult(true, null, null);
        }

        public static TokenSpendResult rejected(String rejectedPlayerUid, String errorMessage) {
            return new TokenSpendResult(false, rejectedPlayerUid, errorMessage);
        }

        public boolean isAccepted() {
            return accepted;
        }

        public String getRejectedPlayerUid() {
            return rejectedPlayerUid;
        }

        public String getErrorMessage() {
            return errorMessage;
        }
    }

    public static final class RewardResult {
        private final boolean applied;
        private final boolean failed;
        private final int player1StarDelta;
        private final int player2StarDelta;
        private final String errorMessage;

        private RewardResult(boolean applied,
                             boolean failed,
                             int player1StarDelta,
                             int player2StarDelta,
                             String errorMessage) {
            this.applied = applied;
            this.failed = failed;
            this.player1StarDelta = player1StarDelta;
            this.player2StarDelta = player2StarDelta;
            this.errorMessage = errorMessage;
        }

        public static RewardResult applied(int player1StarDelta, int player2StarDelta) {
            return new RewardResult(true, false, player1StarDelta, player2StarDelta, null);
        }

        public static RewardResult notApplied() {
            return new RewardResult(false, false, 0, 0, null);
        }

        public static RewardResult failed(String errorMessage) {
            return new RewardResult(false, true, 0, 0, errorMessage);
        }

        public boolean isApplied() {
            return applied;
        }

        public boolean isFailed() {
            return failed;
        }

        public int getPlayer1StarDelta() {
            return player1StarDelta;
        }

        public int getPlayer2StarDelta() {
            return player2StarDelta;
        }

        public String getErrorMessage() {
            return errorMessage;
        }
    }

    private static final class PlayerProgress {
        private final int tokens;
        private final long lastDailyTokenGrantAtMs;

        private PlayerProgress(int tokens, long lastDailyTokenGrantAtMs) {
            this.tokens = tokens;
            this.lastDailyTokenGrantAtMs = lastDailyTokenGrantAtMs;
        }
    }

    private static final class GrantResult {
        private final int tokens;
        private final long lastDailyTokenGrantAtMs;
        private final boolean changed;

        private GrantResult(int tokens, long lastDailyTokenGrantAtMs, boolean changed) {
            this.tokens = tokens;
            this.lastDailyTokenGrantAtMs = lastDailyTokenGrantAtMs;
            this.changed = changed;
        }
    }

    private static final class ScoreReward {
        private final int starDelta;

        private ScoreReward(int starDelta) {
            this.starDelta = starDelta;
        }
    }

    private static final class ConvertedProgress {
        private final int stars;
        private final int tokens;

        private ConvertedProgress(int stars, int tokens) {
            this.stars = stars;
            this.tokens = tokens;
        }
    }
}
