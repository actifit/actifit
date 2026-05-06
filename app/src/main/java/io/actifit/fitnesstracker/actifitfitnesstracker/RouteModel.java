package io.actifit.fitnesstracker.actifitfitnesstracker;

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

    public String getFormattedDistance() {
        if (distanceMeters < 1000) {
            return String.format(Locale.getDefault(), "%.0f m", distanceMeters);
        }
        return String.format(Locale.getDefault(), "%.2f km", distanceMeters / 1000.0);
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

    /** Returns avg pace as min/km string, e.g. "7:07/km". Returns "" if no movement. */
    public String getFormattedPace() {
        long durationSec = TimeUnit.MILLISECONDS.toSeconds(getDurationMs());
        double distKm = distanceMeters / 1000.0;
        if (distKm < 0.01 || durationSec == 0) return "--";
        double secPerKm = durationSec / distKm;
        long paceMin = (long) secPerKm / 60;
        long paceSec = (long) secPerKm % 60;
        return String.format(Locale.getDefault(), "%d:%02d/km", paceMin, paceSec);
    }
}
