package com.example.slagalica.Model;

import com.example.slagalica.R;
import com.google.firebase.firestore.GeoPoint;

public class Region {
    private String regionId;
    private String displayName;
    private String iconKey;
    private GeoPoint center;
    private double randomRadiusKm;
    private int sortOrder;
    private int firstPlaceCount;
    private int secondPlaceCount;
    private int thirdPlaceCount;
    private boolean active;

    public Region() {
    }

    public Region(String regionId,
                  String displayName,
                  String iconKey,
                  GeoPoint center,
                  double randomRadiusKm,
                  int sortOrder,
                  boolean active) {
        this.regionId = regionId;
        this.displayName = displayName;
        this.iconKey = iconKey;
        this.center = center;
        this.randomRadiusKm = randomRadiusKm;
        this.sortOrder = sortOrder;
        this.active = active;
    }

    public String getRegionId() {
        return regionId;
    }

    public void setRegionId(String regionId) {
        this.regionId = regionId;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getIconKey() {
        return iconKey;
    }

    public void setIconKey(String iconKey) {
        this.iconKey = iconKey;
    }

    public GeoPoint getCenter() {
        return center;
    }

    public void setCenter(GeoPoint center) {
        this.center = center;
    }

    public double getRandomRadiusKm() {
        return randomRadiusKm;
    }

    public void setRandomRadiusKm(double randomRadiusKm) {
        this.randomRadiusKm = randomRadiusKm;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(int sortOrder) {
        this.sortOrder = sortOrder;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public int getFirstPlaceCount() {
        return firstPlaceCount;
    }

    public void setFirstPlaceCount(int firstPlaceCount) {
        this.firstPlaceCount = firstPlaceCount;
    }

    public int getSecondPlaceCount() {
        return secondPlaceCount;
    }

    public void setSecondPlaceCount(int secondPlaceCount) {
        this.secondPlaceCount = secondPlaceCount;
    }

    public int getThirdPlaceCount() {
        return thirdPlaceCount;
    }

    public void setThirdPlaceCount(int thirdPlaceCount) {
        this.thirdPlaceCount = thirdPlaceCount;
    }

    public int getIconResId() {
        return iconResIdForKey(iconKey);
    }

    public static int iconResIdForKey(String iconKey) {
        if ("bridge".equals(iconKey)) {
            return R.drawable.ic_region_bridge;
        }
        if ("field".equals(iconKey)) {
            return R.drawable.ic_region_field;
        }
        if ("forest".equals(iconKey)) {
            return R.drawable.ic_region_forest;
        }
        if ("mountain".equals(iconKey)) {
            return R.drawable.ic_region_mountain;
        }
        if ("fortress".equals(iconKey)) {
            return R.drawable.ic_region_fortress;
        }
        return R.drawable.ic_region_star_badge;
    }

    @Override
    public String toString() {
        return displayName == null || displayName.trim().isEmpty() ? regionId : displayName;
    }
}
