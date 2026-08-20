package com.example.slagalica.wsserver;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class FriendInviteService {
    private static final long INVITE_TIMEOUT_MS = 10_000L;

    private final Object lock = new Object();
    private final Map<String, FriendInvite> invitesById = new HashMap<>();
    private final Map<String, String> pendingInviteIdByInviter = new HashMap<>();
    private final Map<String, String> pendingInviteIdByInvitee = new HashMap<>();
    private final ScheduledExecutorService inviteTimeoutExecutor;

    public FriendInviteService() {
        inviteTimeoutExecutor = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "slagalica-friend-invites");
            thread.setDaemon(true);
            return thread;
        });
    }

    public InviteResult createInvite(String inviterUid,
                                     String inviteeUid,
                                     AvailabilityChecker availabilityChecker,
                                     ExpiredInviteListener expiredInviteListener) {
        if (inviterUid == null || inviterUid.isBlank()) {
            return InviteResult.rejected("User id is required.");
        }
        if (inviteeUid == null || inviteeUid.isBlank()) {
            return InviteResult.rejected("Friend id is required.");
        }
        if (inviterUid.equals(inviteeUid)) {
            return InviteResult.rejected("Cannot invite yourself.");
        }
        if (!availabilityChecker.isAvailable(inviterUid)) {
            return InviteResult.rejected("You are not available for a friendly game.");
        }
        if (!availabilityChecker.isAvailable(inviteeUid)) {
            return InviteResult.rejected("Friend is not available for a friendly game.");
        }

        FriendInvite invite;
        synchronized (lock) {
            if (pendingInviteIdByInviter.containsKey(inviterUid) || pendingInviteIdByInvitee.containsKey(inviterUid)) {
                return InviteResult.rejected("You already have a pending friendly game invite.");
            }
            if (pendingInviteIdByInviter.containsKey(inviteeUid) || pendingInviteIdByInvitee.containsKey(inviteeUid)) {
                return InviteResult.rejected("Friend already has a pending friendly game invite.");
            }

            long nowMs = System.currentTimeMillis();
            invite = new FriendInvite(
                    UUID.randomUUID().toString(),
                    inviterUid,
                    inviteeUid,
                    nowMs,
                    nowMs + INVITE_TIMEOUT_MS
            );
            invitesById.put(invite.getInviteId(), invite);
            pendingInviteIdByInviter.put(inviterUid, invite.getInviteId());
            pendingInviteIdByInvitee.put(inviteeUid, invite.getInviteId());
        }

        inviteTimeoutExecutor.schedule(
                () -> expireInvite(invite.getInviteId(), expiredInviteListener),
                INVITE_TIMEOUT_MS,
                TimeUnit.MILLISECONDS
        );
        return InviteResult.accepted(invite);
    }

    public InviteResult acceptInvite(String inviteeUid, String inviteId, AvailabilityChecker availabilityChecker) {
        FriendInvite invite;
        synchronized (lock) {
            invite = invitesById.get(inviteId);
            if (invite == null) {
                return InviteResult.rejected("Friendly game invite is no longer active.");
            }
            if (!invite.getInviteeUid().equals(inviteeUid)) {
                return InviteResult.rejected("Only the invited player can accept this invite.");
            }
            if (invite.isExpired(System.currentTimeMillis())) {
                removeInvite(invite);
                return InviteResult.rejected("Friendly game invite expired.");
            }
            if (!availabilityChecker.isAvailable(invite.getInviterUid()) || !availabilityChecker.isAvailable(invite.getInviteeUid())) {
                removeInvite(invite);
                return InviteResult.rejected("One of the players is no longer available.");
            }
            removeInvite(invite);
        }
        return InviteResult.accepted(invite);
    }

    public InviteResult rejectInvite(String inviteeUid, String inviteId) {
        synchronized (lock) {
            FriendInvite invite = invitesById.get(inviteId);
            if (invite == null) {
                return InviteResult.rejected("Friendly game invite is no longer active.");
            }
            if (!invite.getInviteeUid().equals(inviteeUid)) {
                return InviteResult.rejected("Only the invited player can reject this invite.");
            }
            removeInvite(invite);
            return InviteResult.accepted(invite);
        }
    }

    public InviteResult cancelInvite(String inviterUid, String inviteId) {
        synchronized (lock) {
            FriendInvite invite = invitesById.get(inviteId);
            if (invite == null) {
                return InviteResult.rejected("Friendly game invite is no longer active.");
            }
            if (!invite.getInviterUid().equals(inviterUid)) {
                return InviteResult.rejected("Only the inviting player can cancel this invite.");
            }
            removeInvite(invite);
            return InviteResult.accepted(invite);
        }
    }

    public void removeUser(String uid, InviteClosedListener listener) {
        if (uid == null) {
            return;
        }

        FriendInvite invite = null;
        synchronized (lock) {
            String inviteId = pendingInviteIdByInviter.get(uid);
            if (inviteId == null) {
                inviteId = pendingInviteIdByInvitee.get(uid);
            }
            if (inviteId != null) {
                invite = invitesById.get(inviteId);
                if (invite != null) {
                    removeInvite(invite);
                }
            }
        }

        if (invite != null && listener != null) {
            listener.onInviteClosed(invite);
        }
    }

    private void expireInvite(String inviteId, ExpiredInviteListener listener) {
        FriendInvite invite = null;
        synchronized (lock) {
            FriendInvite pendingInvite = invitesById.get(inviteId);
            if (pendingInvite != null && pendingInvite.isExpired(System.currentTimeMillis())) {
                invite = pendingInvite;
                removeInvite(invite);
            }
        }

        if (invite != null && listener != null) {
            listener.onInviteExpired(invite);
        }
    }

    private void removeInvite(FriendInvite invite) {
        invitesById.remove(invite.getInviteId());
        pendingInviteIdByInviter.remove(invite.getInviterUid());
        pendingInviteIdByInvitee.remove(invite.getInviteeUid());
    }

    public interface AvailabilityChecker {
        boolean isAvailable(String uid);
    }

    public interface ExpiredInviteListener {
        void onInviteExpired(FriendInvite invite);
    }

    public interface InviteClosedListener {
        void onInviteClosed(FriendInvite invite);
    }

    public static final class FriendInvite {
        private final String inviteId;
        private final String inviterUid;
        private final String inviteeUid;
        private final long createdAtMs;
        private final long expiresAtMs;

        private FriendInvite(String inviteId, String inviterUid, String inviteeUid, long createdAtMs, long expiresAtMs) {
            this.inviteId = inviteId;
            this.inviterUid = inviterUid;
            this.inviteeUid = inviteeUid;
            this.createdAtMs = createdAtMs;
            this.expiresAtMs = expiresAtMs;
        }

        public String getInviteId() {
            return inviteId;
        }

        public String getInviterUid() {
            return inviterUid;
        }

        public String getInviteeUid() {
            return inviteeUid;
        }

        public long getCreatedAtMs() {
            return createdAtMs;
        }

        public long getExpiresAtMs() {
            return expiresAtMs;
        }

        public boolean isExpired(long nowMs) {
            return nowMs >= expiresAtMs;
        }
    }

    public static final class InviteResult {
        private final FriendInvite invite;
        private final boolean accepted;
        private final String errorMessage;

        private InviteResult(FriendInvite invite, boolean accepted, String errorMessage) {
            this.invite = invite;
            this.accepted = accepted;
            this.errorMessage = errorMessage;
        }

        public static InviteResult accepted(FriendInvite invite) {
            return new InviteResult(invite, true, null);
        }

        public static InviteResult rejected(String errorMessage) {
            return new InviteResult(null, false, errorMessage);
        }

        public FriendInvite getInvite() {
            return invite;
        }

        public boolean isAccepted() {
            return accepted;
        }

        public String getErrorMessage() {
            return errorMessage;
        }
    }
}
