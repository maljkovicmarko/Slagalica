package com.example.slagalica.wsserver;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;

public class MatchmakingService {
    private final Object rankedQueueLock = new Object();
    private final Deque<String> rankedQueue = new ArrayDeque<>();
    private final Set<String> rankedQueuedUsers = new HashSet<>();
    private final SessionService sessionService;
    private final String rankedSessionType;

    public MatchmakingService(SessionService sessionService, String rankedSessionType) {
        this.sessionService = sessionService;
        this.rankedSessionType = rankedSessionType;
    }

    public JoinRankedQueueResult joinRankedQueue(String uid) {
        SessionState matchedSession = null;
        int queueSize;

        RankedPlayerProgressService.TokenCheckResult tokenCheckResult = sessionService.canJoinRankedQueue(uid);
        if (!tokenCheckResult.isAccepted()) {
            synchronized (rankedQueueLock) {
                rankedQueuedUsers.remove(uid);
                rankedQueue.remove(uid);
                queueSize = rankedQueue.size();
            }
            return JoinRankedQueueResult.rejected(queueSize, tokenCheckResult.getErrorMessage());
        }

        synchronized (rankedQueueLock) {
            if (!rankedQueuedUsers.contains(uid)) {
                rankedQueue.addLast(uid);
                rankedQueuedUsers.add(uid);
            }

            if (rankedQueue.size() >= 2) {
                String player1Uid = rankedQueue.removeFirst();
                String player2Uid = rankedQueue.removeFirst();
                rankedQueuedUsers.remove(player1Uid);
                rankedQueuedUsers.remove(player2Uid);
                RankedPlayerProgressService.TokenSpendResult spendResult = sessionService.spendRankedTokens(player1Uid, player2Uid);
                if (!spendResult.isAccepted()) {
                    queueSize = rankedQueue.size();
                    return JoinRankedQueueResult.rejected(queueSize, spendResult.getErrorMessage());
                }
                matchedSession = sessionService.createSession(player1Uid, player2Uid, rankedSessionType);
            }

            queueSize = rankedQueue.size();
        }

        return new JoinRankedQueueResult(matchedSession, queueSize, true, null);
    }

    public LeaveRankedQueueResult leaveRankedQueue(String uid) {
        int queueSize;
        boolean removed;

        synchronized (rankedQueueLock) {
            removed = rankedQueuedUsers.remove(uid);
            if (removed) {
                rankedQueue.remove(uid);
            }
            queueSize = rankedQueue.size();
        }

        return new LeaveRankedQueueResult(removed, queueSize);
    }

    public void removeUser(String uid) {
        synchronized (rankedQueueLock) {
            if (rankedQueuedUsers.remove(uid)) {
                rankedQueue.remove(uid);
            }
        }
    }

    public static final class JoinRankedQueueResult {
        private final SessionState matchedSession;
        private final int queueSize;
        private final boolean accepted;
        private final String errorMessage;

        private JoinRankedQueueResult(SessionState matchedSession, int queueSize, boolean accepted, String errorMessage) {
            this.matchedSession = matchedSession;
            this.queueSize = queueSize;
            this.accepted = accepted;
            this.errorMessage = errorMessage;
        }

        public static JoinRankedQueueResult rejected(int queueSize, String errorMessage) {
            return new JoinRankedQueueResult(null, queueSize, false, errorMessage);
        }

        public SessionState getMatchedSession() {
            return matchedSession;
        }

        public int getQueueSize() {
            return queueSize;
        }

        public boolean isAccepted() {
            return accepted;
        }

        public String getErrorMessage() {
            return errorMessage;
        }

        public boolean isMatched() {
            return accepted && matchedSession != null;
        }
    }

    public static final class LeaveRankedQueueResult {
        private final boolean removed;
        private final int queueSize;

        private LeaveRankedQueueResult(boolean removed, int queueSize) {
            this.removed = removed;
            this.queueSize = queueSize;
        }

        public boolean wasRemoved() {
            return removed;
        }

        public int getQueueSize() {
            return queueSize;
        }
    }
}
