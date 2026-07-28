package io.actifit.fitnesstracker.actifitfitnesstracker;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.location.Location;
import android.os.Build;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

public class RouteRecordingService extends Service {

    private static final String TAG = "RouteRecordingService";
    private static final String CHANNEL_ID = "route_recording";
    private static final int NOTIFICATION_ID = 2001;

    public static final String ACTION_STOP_RECORDING = "io.actifit.route.STOP";
    public static final String BROADCAST_WAYPOINT_UPDATE = "io.actifit.route.WAYPOINT_UPDATE";
    public static final String BROADCAST_RECORDING_STOPPED = "io.actifit.route.STOPPED";

    public static final String EXTRA_ACTIVITY_TYPE = "activityType";
    public static final String EXTRA_ROUTE_ID = "routeId";
    public static final String EXTRA_LAT = "lat";
    public static final String EXTRA_LNG = "lng";
    public static final String EXTRA_DISTANCE = "distance";
    public static final String EXTRA_DURATION_MS = "durationMs";

    private FusedLocationProviderClient fusedLocationClient;
    private LocationCallback locationCallback;
    private final List<WaypointModel> waypoints = new ArrayList<>();
    private String activityType = "Walking";
    private long startTimeMs;
    private double totalDistanceMeters = 0;
    private Location lastLocation;

    public static boolean isRunning = false;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(@NonNull LocationResult result) {
                for (Location location : result.getLocations()) {
                    onNewLocation(location);
                }
            }
        };
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            if (ACTION_STOP_RECORDING.equals(intent.getAction())) {
                stopRecording();
                return START_NOT_STICKY;
            }
            activityType = intent.getStringExtra(EXTRA_ACTIVITY_TYPE);
            if (activityType == null) activityType = "Walking";
        }
        startRecording();
        return START_STICKY;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        isRunning = false;
        fusedLocationClient.removeLocationUpdates(locationCallback);
    }

    private void startRecording() {
        isRunning = true;
        startTimeMs = System.currentTimeMillis();
        waypoints.clear();
        totalDistanceMeters = 0;
        lastLocation = null;

        startForeground(NOTIFICATION_ID, buildNotification(Utils.formatDistance(this, 0), "00:00"));

        LocationRequest locationRequest = new LocationRequest.Builder(
                Priority.PRIORITY_HIGH_ACCURACY, 3000)
                .setMinUpdateIntervalMillis(1000)
                .build();

        try {
            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback,
                    Looper.getMainLooper());
        } catch (SecurityException e) {
            Log.e(TAG, "Location permission missing", e);
            stopSelf();
        }
    }

    private void onNewLocation(Location location) {
        if (location == null) return;

        WaypointModel waypoint = new WaypointModel(
                location.getLatitude(),
                location.getLongitude(),
                location.hasAltitude() ? location.getAltitude() : 0,
                location.getTime(),
                location.getAccuracy(),
                location.hasSpeed() ? location.getSpeed() : 0
        );
        waypoints.add(waypoint);

        if (lastLocation != null) {
            totalDistanceMeters += lastLocation.distanceTo(location);
        }
        lastLocation = location;

        long durationMs = System.currentTimeMillis() - startTimeMs;
        updateNotification(totalDistanceMeters, durationMs);

        Intent broadcast = new Intent(BROADCAST_WAYPOINT_UPDATE);
        broadcast.putExtra(EXTRA_LAT, location.getLatitude());
        broadcast.putExtra(EXTRA_LNG, location.getLongitude());
        broadcast.putExtra(EXTRA_DISTANCE, totalDistanceMeters);
        broadcast.putExtra(EXTRA_DURATION_MS, durationMs);
        LocalBroadcastManager.getInstance(this).sendBroadcast(broadcast);
    }

    private void stopRecording() {
        fusedLocationClient.removeLocationUpdates(locationCallback);
        isRunning = false;

        long endTimeMs = System.currentTimeMillis();
        String waypointsJson = new Gson().toJson(waypoints,
                new TypeToken<List<WaypointModel>>() {}.getType());

        SimpleDateFormat fmt = new SimpleDateFormat("yyyyMMdd", Locale.ENGLISH);
        int dateInt = Integer.parseInt(fmt.format(new Date(startTimeMs)));

        RouteModel route = new RouteModel();
        route.date = dateInt;
        route.activityType = activityType;
        route.sourceType = RouteModel.SOURCE_GPS;
        route.startTimeMs = startTimeMs;
        route.endTimeMs = endTimeMs;
        route.distanceMeters = totalDistanceMeters;
        route.waypointsJson = waypointsJson;

        StepsDBHelper db = new StepsDBHelper(this);
        long routeId = db.insertRoute(route);

        Intent broadcast = new Intent(BROADCAST_RECORDING_STOPPED);
        broadcast.putExtra(EXTRA_ROUTE_ID, routeId);
        broadcast.putExtra(EXTRA_DISTANCE, totalDistanceMeters);
        broadcast.putExtra(EXTRA_DURATION_MS, endTimeMs - startTimeMs);
        LocalBroadcastManager.getInstance(this).sendBroadcast(broadcast);

        stopForeground(true);
        stopSelf();
    }

    private void updateNotification(double distanceMeters, long durationMs) {
        String distStr = Utils.formatDistance(this, distanceMeters);
        long min = TimeUnit.MILLISECONDS.toMinutes(durationMs);
        long sec = TimeUnit.MILLISECONDS.toSeconds(durationMs) % 60;
        String timeStr = String.format(Locale.getDefault(), "%02d:%02d", min, sec);
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm != null) nm.notify(NOTIFICATION_ID, buildNotification(distStr, timeStr));
    }

    private Notification buildNotification(String distStr, String timeStr) {
        Intent stopIntent = new Intent(this, RouteRecordingService.class);
        stopIntent.setAction(ACTION_STOP_RECORDING);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) flags |= PendingIntent.FLAG_IMMUTABLE;
        PendingIntent stopPending = PendingIntent.getService(this, 0, stopIntent, flags);

        Intent openIntent = new Intent(this, RouteMapActivity.class);
        openIntent.putExtra(RouteMapActivity.EXTRA_MODE, RouteMapActivity.MODE_LIVE);
        openIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent openPending = PendingIntent.getActivity(this, 0, openIntent, flags);

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.actifit_logo)
                .setContentTitle("Recording: " + activityType)
                .setContentText(distStr + "  •  " + timeStr)
                .setOngoing(true)
                .setContentIntent(openPending)
                .addAction(R.drawable.actifit_logo, "Stop", stopPending)
                .build();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "Route Recording", NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("Shows active route recording status");
            channel.setShowBadge(false);
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(channel);
        }
    }
}
