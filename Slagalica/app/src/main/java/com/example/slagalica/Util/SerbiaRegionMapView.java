package com.example.slagalica.Util;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;

import com.example.slagalica.Model.RegionLeaderboardEntry;
import com.google.firebase.firestore.GeoPoint;

import java.util.ArrayList;
import java.util.List;

public class SerbiaRegionMapView extends View {
    private static final double MIN_LAT = 42.0;
    private static final double MAX_LAT = 46.3;
    private static final double MIN_LNG = 18.8;
    private static final double MAX_LNG = 23.1;

    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pointPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private List<RegionLeaderboardEntry> regions = new ArrayList<>();

    public SerbiaRegionMapView(Context context) {
        super(context);
        fillPaint.setColor(Color.rgb(232, 245, 233));
        fillPaint.setStyle(Paint.Style.FILL);
        strokePaint.setColor(Color.rgb(76, 175, 80));
        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setStrokeWidth(4f);
        pointPaint.setStyle(Paint.Style.FILL);
        textPaint.setColor(Color.rgb(38, 50, 56));
        textPaint.setTextSize(26f);
        textPaint.setFakeBoldText(true);
    }

    public void setRegions(List<RegionLeaderboardEntry> regions) {
        this.regions = regions == null ? new ArrayList<>() : new ArrayList<>(regions);
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float width = getWidth();
        float height = getHeight();

        Path serbia = new Path();
        serbia.moveTo(width * 0.50f, height * 0.05f);
        serbia.lineTo(width * 0.72f, height * 0.18f);
        serbia.lineTo(width * 0.66f, height * 0.40f);
        serbia.lineTo(width * 0.80f, height * 0.60f);
        serbia.lineTo(width * 0.58f, height * 0.92f);
        serbia.lineTo(width * 0.36f, height * 0.82f);
        serbia.lineTo(width * 0.22f, height * 0.56f);
        serbia.lineTo(width * 0.34f, height * 0.32f);
        serbia.close();

        canvas.drawPath(serbia, fillPaint);
        canvas.drawPath(serbia, strokePaint);

        for (RegionLeaderboardEntry entry : regions) {
            pointPaint.setColor(entry.isCurrentUserRegion() ? Color.rgb(255, 193, 7) : Color.rgb(46, 125, 50));
            for (GeoPoint mapPoint : entry.getMapPoints()) {
                float[] point = project(mapPoint, width, height);
                canvas.drawCircle(point[0], point[1], entry.isCurrentUserRegion() ? 8f : 5f, pointPaint);
            }
        }
    }

    private float[] project(GeoPoint point, float width, float height) {
        double clampedLat = Math.max(MIN_LAT, Math.min(MAX_LAT, point.getLatitude()));
        double clampedLng = Math.max(MIN_LNG, Math.min(MAX_LNG, point.getLongitude()));
        float x = (float) (((clampedLng - MIN_LNG) / (MAX_LNG - MIN_LNG)) * width);
        float y = (float) (((MAX_LAT - clampedLat) / (MAX_LAT - MIN_LAT)) * height);
        return new float[]{x, y};
    }
}
