package com.example.slagalica.Util;

import androidx.annotation.DrawableRes;

import com.example.slagalica.R;

import java.util.Locale;

public final class LeagueIconResolver {
    private LeagueIconResolver() {
    }

    @DrawableRes
    public static int iconFor(String leagueName) {
        String normalized = leagueName == null
                ? ""
                : leagueName.trim().toLowerCase(Locale.ROOT);

        if (normalized.contains("gold") || normalized.contains("zlat")) {
            return R.drawable.gold_icon;
        }
        if (normalized.contains("silver") || normalized.contains("srebr")) {
            return R.drawable.silver_icon;
        }
        return R.drawable.bronze_icon;
    }
}
