package com.example.slagalica.Model;

import com.google.firebase.firestore.GeoPoint;

public class Player {
    private String id;
    private String email;
    private String username;
    private String usernameNormalized;
    private String regionId;
    private String region;
    private String regionIconKey;
    private GeoPoint mapPoint;
    private String avatarBase64;

    private int tokens;
    private int totalStars;
    private int weeklyStars;
    private int weeklyGames;
    private String weeklyCycleId;
    private int monthlyStars;
    private int monthlyGames;
    private String monthlyCycleId;
    private int monthlyRank;
    private String leagueName;
    private String regionalAvatarFrame;
    private String regionalAvatarFrameCycleId;
    private PlayerStatistics statistics;

    public Player() {
    }

    public int getTokens() {
        return tokens;
    }

    public void setTokens(int tokens) {
        this.tokens = tokens;
    }

    public int getTotalStars() {
        return totalStars;
    }

    public void setTotalStars(int totalStars) {
        this.totalStars = totalStars;
    }

    public int getMonthlyRank() {
        return monthlyRank;
    }

    public void setMonthlyRank(int monthlyRank) {
        this.monthlyRank = monthlyRank;
    }

    public int getWeeklyStars() {
        return weeklyStars;
    }

    public void setWeeklyStars(int weeklyStars) {
        this.weeklyStars = weeklyStars;
    }

    public int getWeeklyGames() {
        return weeklyGames;
    }

    public void setWeeklyGames(int weeklyGames) {
        this.weeklyGames = weeklyGames;
    }

    public String getWeeklyCycleId() {
        return weeklyCycleId;
    }

    public void setWeeklyCycleId(String weeklyCycleId) {
        this.weeklyCycleId = weeklyCycleId;
    }

    public int getMonthlyStars() {
        return monthlyStars;
    }

    public void setMonthlyStars(int monthlyStars) {
        this.monthlyStars = monthlyStars;
    }

    public int getMonthlyGames() {
        return monthlyGames;
    }

    public void setMonthlyGames(int monthlyGames) {
        this.monthlyGames = monthlyGames;
    }

    public String getMonthlyCycleId() {
        return monthlyCycleId;
    }

    public void setMonthlyCycleId(String monthlyCycleId) {
        this.monthlyCycleId = monthlyCycleId;
    }

    public String getLeagueName() {
        return leagueName;
    }

    public void setLeagueName(String leagueName) {
        this.leagueName = leagueName;
    }

    public String getRegionalAvatarFrame() {
        return regionalAvatarFrame;
    }

    public void setRegionalAvatarFrame(String regionalAvatarFrame) {
        this.regionalAvatarFrame = regionalAvatarFrame;
    }

    public String getRegionalAvatarFrameCycleId() {
        return regionalAvatarFrameCycleId;
    }

    public void setRegionalAvatarFrameCycleId(String regionalAvatarFrameCycleId) {
        this.regionalAvatarFrameCycleId = regionalAvatarFrameCycleId;
    }

    public PlayerStatistics getStatistics() {
        return statistics;
    }

    public void setStatistics(PlayerStatistics statistics) {
        this.statistics = statistics;
    }

    public Player(String id, String email, String username, String region) {
        this.id = id;
        this.email = email;
        this.username = username;
        this.region = region;
        this.avatarBase64 = "";
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getUsernameNormalized() {
        return usernameNormalized;
    }

    public void setUsernameNormalized(String usernameNormalized) {
        this.usernameNormalized = usernameNormalized;
    }

    public String getRegion() {
        return region;
    }

    public void setRegion(String region) {
        this.region = region;
    }

    public String getRegionId() {
        return regionId;
    }

    public void setRegionId(String regionId) {
        this.regionId = regionId;
    }

    public String getRegionIconKey() {
        return regionIconKey;
    }

    public void setRegionIconKey(String regionIconKey) {
        this.regionIconKey = regionIconKey;
    }

    public GeoPoint getMapPoint() {
        return mapPoint;
    }

    public void setMapPoint(GeoPoint mapPoint) {
        this.mapPoint = mapPoint;
    }

    public String getAvatarBase64() {
        return avatarBase64;
    }

    public void setAvatarBase64(String avatarBase64) {
        this.avatarBase64 = avatarBase64;
    }
}
