package io.actifit.fitnesstracker.actifitfitnesstracker;

public class WaypointModel {
    public double lat;
    public double lng;
    public double altitudeMeters;
    public long timestampMs;
    public float accuracy;
    public float speedMps;

    public WaypointModel() {}

    public WaypointModel(double lat, double lng, double altitudeMeters,
                         long timestampMs, float accuracy, float speedMps) {
        this.lat = lat;
        this.lng = lng;
        this.altitudeMeters = altitudeMeters;
        this.timestampMs = timestampMs;
        this.accuracy = accuracy;
        this.speedMps = speedMps;
    }
}
