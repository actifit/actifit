package io.actifit.fitnesstracker.actifitfitnesstracker;

import android.content.Context;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

public class RouteModel {
    public long routeId;
    public int date; // yyyyMMdd
    public String activityType;
    public String sourceType; // "GPS", "HealthConnect", "Fitbit"
    public long startTimeMs;
    public long endTimeMs;
    public double distanceMeters;
    public double elevationGainMeters;
    public String waypointsJson;

    // Transient — populated after deserialization, not persisted
    public List<WaypointModel> waypoints;

    public static final String SOURCE_GPS = "GPS";
    public static final String SOURCE_HEALTH_CONNECT = "HealthConnect";
    public static final String SOURCE_FITBIT = "Fitbit";

    public RouteModel() {}

    public long getDurationMs() {
        if (endTimeMs <= startTimeMs) return 0;
        return endTimeMs - startTimeMs;
    }

    /** Formats distance honoring the user's active measurement system (metric km / US miles). */
    public String getFormattedDistance(Context context) {
        return Utils.formatDistance(context, distanceMeters);
    }

    public String getFormattedDuration() {
        long ms = getDurationMs();
        long hours = TimeUnit.MILLISECONDS.toHours(ms);
        long minutes = TimeUnit.MILLISECONDS.toMinutes(ms) % 60;
        long seconds = TimeUnit.MILLISECONDS.toSeconds(ms) % 60;
        if (hours > 0) {
            return String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, seconds);
        }
        return String.format(Locale.getDefault(), "%d:%02d", minutes, seconds);
    }

    /** Returns avg pace (min/km or min/mi per the user's setting), e.g. "7:07/km". "--" if no movement. */
    public String getFormattedPace(Context context) {
        return Utils.formatPace(context, distanceMeters, getDurationMs());
    }
}
