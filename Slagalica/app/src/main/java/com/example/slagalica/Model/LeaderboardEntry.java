package com.example.slagalica.Model;

public class LeaderboardEntry {
    private final int rank;
    private final String playerId;
    private final String username;
    private final String leagueName;
    private final String region;
    private final int stars;
    private final boolean currentUser;

    public LeaderboardEntry(int rank,
                            String playerId,
                            String username,
                            String leagueName,
                            String region,
                            int stars,
                            boolean currentUser) {
        this.rank = rank;
        this.playerId = playerId;
        this.username = username;
        this.leagueName = leagueName;
        this.region = region;
        this.stars = stars;
        this.currentUser = currentUser;
    }

    public int getRank() {
        return rank;
    }

    public String getPlayerId() {
        return playerId;
    }

    public String getUsername() {
        return username;
    }

    public String getLeagueName() {
        return leagueName;
    }

    public String getRegion() {
        return region;
    }

    public int getStars() {
        return stars;
    }

    public boolean isCurrentUser() {
        return currentUser;
    }
}
