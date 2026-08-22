package com.example.slagalica.Model;

import com.google.firebase.firestore.GeoPoint;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class RegionLeaderboardEntry {
    private final int rank;
    private final String region;
    private final int iconResId;
    private final int monthlyStars;
    private final int firstPlaceCount;
    private final int secondPlaceCount;
    private final int thirdPlaceCount;
    private final int activePlayers;
    private final int registeredPlayers;
    private final List<GeoPoint> mapPoints;
    private final boolean currentUserRegion;

    public RegionLeaderboardEntry(int rank,
                                  String region,
                                  int iconResId,
                                  int monthlyStars,
                                  int firstPlaceCount,
                                  int secondPlaceCount,
                                  int thirdPlaceCount,
                                  int activePlayers,
                                  int registeredPlayers,
                                  List<GeoPoint> mapPoints,
                                  boolean currentUserRegion) {
        this.rank = rank;
        this.region = region;
        this.iconResId = iconResId;
        this.monthlyStars = monthlyStars;
        this.firstPlaceCount = firstPlaceCount;
        this.secondPlaceCount = secondPlaceCount;
        this.thirdPlaceCount = thirdPlaceCount;
        this.activePlayers = activePlayers;
        this.registeredPlayers = registeredPlayers;
        this.mapPoints = mapPoints == null ? new ArrayList<>() : new ArrayList<>(mapPoints);
        this.currentUserRegion = currentUserRegion;
    }

    public int getRank() {
        return rank;
    }

    public String getRegion() {
        return region;
    }

    public int getIconResId() {
        return iconResId;
    }

    public int getMonthlyStars() {
        return monthlyStars;
    }

    public int getFirstPlaceCount() {
        return firstPlaceCount;
    }

    public int getSecondPlaceCount() {
        return secondPlaceCount;
    }

    public int getThirdPlaceCount() {
        return thirdPlaceCount;
    }

    public int getActivePlayers() {
        return activePlayers;
    }

    public int getRegisteredPlayers() {
        return registeredPlayers;
    }

    public List<GeoPoint> getMapPoints() {
        return Collections.unmodifiableList(mapPoints);
    }

    public boolean isCurrentUserRegion() {
        return currentUserRegion;
    }
}
