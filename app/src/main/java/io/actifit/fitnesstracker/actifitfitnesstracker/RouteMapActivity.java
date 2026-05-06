package io.actifit.fitnesstracker.actifitfitnesstracker;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.BoundingBox;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.Polyline;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

public class RouteMapActivity extends BaseActivity {

    public static final String MODE_LIVE = "LIVE";
    public static final String MODE_VIEW = "VIEW";
    public static final String EXTRA_MODE = "mode";
    public static final String EXTRA_DATE = "date";
    public static final String EXTRA_ACTIVITY_TYPE = "activityType";
    public static final String EXTRA_ROUTE_ID = "routeId";

    private MapView mapView;
    private Polyline routePolyline;
    private Marker currentPositionMarker;
    private final List<GeoPoint> routePoints = new ArrayList<>();

    private TextView tvDistance, tvDuration, tvPace, tvSteps, tvTimer, tvActivityLabel;
    private TextView btnZoomIn, btnZoomOut, btnLocate;
    private LinearLayout liveControls, viewControls;

    private String mode = MODE_VIEW;
    private long recordingStartMs;
    private double currentDistanceMeters;
    private Handler timerHandler;
    private Runnable timerRunnable;

    private BroadcastReceiver waypointReceiver;
    private BroadcastReceiver stoppedReceiver;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Configuration.getInstance().setUserAgentValue(getPackageName());

        setContentView(R.layout.activity_route_map);

        mode = getIntent().getStringExtra(EXTRA_MODE);
        if (mode == null) mode = MODE_VIEW;

        mapView = findViewById(R.id.route_map_view);
        mapView.setTileSource(TileSourceFactory.MAPNIK);
        mapView.setMultiTouchControls(true);
        mapView.setMaxZoomLevel(19.0);
        mapView.getController().setZoom(16.0);

        tvDistance = findViewById(R.id.tv_route_distance);
        tvDuration = findViewById(R.id.tv_route_duration);
        tvPace = findViewById(R.id.tv_route_pace);
        tvSteps = findViewById(R.id.tv_route_steps);
        tvTimer = findViewById(R.id.tv_route_timer);
        tvActivityLabel = findViewById(R.id.tv_route_activity_label);
        liveControls = findViewById(R.id.live_controls);
        viewControls = findViewById(R.id.view_controls);

        btnZoomIn = findViewById(R.id.btn_zoom_in);
        btnZoomOut = findViewById(R.id.btn_zoom_out);
        btnLocate = findViewById(R.id.btn_locate);
        btnZoomIn.setOnClickListener(v -> mapView.getController().zoomIn());
        btnZoomOut.setOnClickListener(v -> mapView.getController().zoomOut());
        btnLocate.setOnClickListener(v -> onLocateTapped());

        routePolyline = new Polyline();
        routePolyline.getOutlinePaint().setColor(ContextCompat.getColor(this, R.color.actifitRed));
        routePolyline.getOutlinePaint().setStrokeWidth(10f);
        mapView.getOverlays().add(routePolyline);

        currentPositionMarker = new Marker(mapView);
        currentPositionMarker.setIcon(ContextCompat.getDrawable(this, R.drawable.actifit_logo));
        mapView.getOverlays().add(currentPositionMarker);

        if (MODE_LIVE.equals(mode)) {
            setupLiveMode();
        } else {
            setupViewMode();
        }

        findViewById(R.id.btn_route_back).setOnClickListener(v -> onBackPressed());
    }

    private void setupLiveMode() {
        liveControls.setVisibility(View.VISIBLE);
        viewControls.setVisibility(View.GONE);
        currentPositionMarker.setVisible(true);
        tvDuration.setVisibility(View.GONE); // top timer handles elapsed time in live mode

        String actType = getIntent().getStringExtra(EXTRA_ACTIVITY_TYPE);
        tvActivityLabel.setText(actType != null ? actType : "Recording");

        recordingStartMs = System.currentTimeMillis();
        startTimer();

        // Register for live waypoint broadcasts
        waypointReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                double lat = intent.getDoubleExtra(RouteRecordingService.EXTRA_LAT, 0);
                double lng = intent.getDoubleExtra(RouteRecordingService.EXTRA_LNG, 0);
                currentDistanceMeters = intent.getDoubleExtra(RouteRecordingService.EXTRA_DISTANCE, 0);
                addPoint(lat, lng);
                updateLiveStats(currentDistanceMeters,
                        intent.getLongExtra(RouteRecordingService.EXTRA_DURATION_MS, 0));
            }
        };
        stoppedReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                long routeId = intent.getLongExtra(RouteRecordingService.EXTRA_ROUTE_ID, -1);
                double dist = intent.getDoubleExtra(RouteRecordingService.EXTRA_DISTANCE, 0);
                long durationMs = intent.getLongExtra(RouteRecordingService.EXTRA_DURATION_MS, 0);
                openSummary(routeId, dist, durationMs);
            }
        };
        LocalBroadcastManager.getInstance(this).registerReceiver(
                waypointReceiver, new IntentFilter(RouteRecordingService.BROADCAST_WAYPOINT_UPDATE));
        LocalBroadcastManager.getInstance(this).registerReceiver(
                stoppedReceiver, new IntentFilter(RouteRecordingService.BROADCAST_RECORDING_STOPPED));

        findViewById(R.id.btn_stop_recording).setOnClickListener(v ->
                new AlertDialog.Builder(this)
                        .setTitle("Stop Recording?")
                        .setMessage("Save and finish this route?")
                        .setPositiveButton("Stop & Save", (d, w) -> {
                            Intent stopIntent = new Intent(this, RouteRecordingService.class);
                            stopIntent.setAction(RouteRecordingService.ACTION_STOP_RECORDING);
                            startService(stopIntent);
                        })
                        .setNegativeButton("Keep Going", null)
                        .show());
    }

    private void setupViewMode() {
        liveControls.setVisibility(View.GONE);
        viewControls.setVisibility(View.VISIBLE);
        currentPositionMarker.setVisible(false);
        tvTimer.setVisibility(View.GONE);
        tvDuration.setVisibility(View.VISIBLE);

        int dateInt = getIntent().getIntExtra(EXTRA_DATE, 0);
        long routeId = getIntent().getLongExtra(EXTRA_ROUTE_ID, -1);

        StepsDBHelper db = new StepsDBHelper(this);
        RouteModel route = null;
        if (dateInt != 0) {
            route = db.getRouteForDate(dateInt);
        }

        if (route == null) {
            Toast.makeText(this, "Route not found", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        tvActivityLabel.setText(route.activityType != null ? route.activityType : "Activity");
        tvDistance.setText(route.getFormattedDistance());
        tvDuration.setText(route.getFormattedDuration());
        tvPace.setText(route.getFormattedPace());

        tvSteps.setText("--"); // day total ≠ route steps; no per-route step data available

        // Draw route from stored waypoints
        if (route.waypointsJson != null) {
            List<WaypointModel> waypoints = new Gson().fromJson(route.waypointsJson,
                    new TypeToken<List<WaypointModel>>() {}.getType());
            if (waypoints != null) {
                for (WaypointModel w : waypoints) {
                    routePoints.add(new GeoPoint(w.lat, w.lng));
                }
                routePolyline.setPoints(new ArrayList<>(routePoints));
                if (!routePoints.isEmpty()) {
                    fitMapToRoute();
                }
            }
        }

        String sourceLabel = RouteModel.SOURCE_GPS.equals(route.sourceType) ? "GPS"
                : route.sourceType != null ? route.sourceType : "";
        if (!sourceLabel.isEmpty()) {
            tvActivityLabel.setText(tvActivityLabel.getText() + "  •  " + sourceLabel);
        }
    }

    private void addPoint(double lat, double lng) {
        GeoPoint point = new GeoPoint(lat, lng);
        routePoints.add(point);
        routePolyline.setPoints(new ArrayList<>(routePoints));
        currentPositionMarker.setPosition(point);
        mapView.getController().animateTo(point);
        mapView.invalidate();
    }

    private void updateLiveStats(double distanceMeters, long durationMs) {
        tvDistance.setText(String.format(Locale.getDefault(), "%.2f km", distanceMeters / 1000.0));
        long min = TimeUnit.MILLISECONDS.toMinutes(durationMs);
        long sec = TimeUnit.MILLISECONDS.toSeconds(durationMs) % 60;
        tvDuration.setText(String.format(Locale.getDefault(), "%d:%02d", min, sec));
        if (distanceMeters > 50) {
            double secPerKm = (durationMs / 1000.0) / (distanceMeters / 1000.0);
            long paceMin = (long) secPerKm / 60;
            long paceSec = (long) secPerKm % 60;
            tvPace.setText(String.format(Locale.getDefault(), "%d:%02d/km", paceMin, paceSec));
        }
    }

    private void onLocateTapped() {
        if (MODE_LIVE.equals(mode)) {
            if (!routePoints.isEmpty()) {
                mapView.getController().animateTo(routePoints.get(routePoints.size() - 1));
            }
        } else {
            if (!routePoints.isEmpty()) {
                fitMapToRoute();
            }
        }
    }

    private void fitMapToRoute() {
        if (routePoints.size() < 2) {
            mapView.getController().setCenter(routePoints.get(0));
            mapView.getController().setZoom(17.0);
            return;
        }
        BoundingBox box = BoundingBox.fromGeoPointsSafe(routePoints);
        mapView.post(() -> {
            mapView.zoomToBoundingBox(box, true, 80);
            if (mapView.getZoomLevelDouble() > 19.0) {
                mapView.getController().setZoom(19.0);
            }
        });
    }

    private void startTimer() {
        timerHandler = new Handler(Looper.getMainLooper());
        timerRunnable = new Runnable() {
            @Override
            public void run() {
                long elapsed = System.currentTimeMillis() - recordingStartMs;
                long h = TimeUnit.MILLISECONDS.toHours(elapsed);
                long m = TimeUnit.MILLISECONDS.toMinutes(elapsed) % 60;
                long s = TimeUnit.MILLISECONDS.toSeconds(elapsed) % 60;
                if (h > 0) {
                    tvTimer.setText(String.format(Locale.getDefault(), "%d:%02d:%02d", h, m, s));
                } else {
                    tvTimer.setText(String.format(Locale.getDefault(), "%02d:%02d", m, s));
                }
                timerHandler.postDelayed(this, 1000);
            }
        };
        timerHandler.post(timerRunnable);
    }

    private void openSummary(long routeId, double distMeters, long durationMs) {
        if (timerHandler != null) timerHandler.removeCallbacks(timerRunnable);
        Intent intent = new Intent(this, RouteMapActivity.class);
        intent.putExtra(EXTRA_MODE, MODE_VIEW);
        intent.putExtra(EXTRA_ROUTE_ID, routeId);
        SimpleDateFormat fmt = new SimpleDateFormat("yyyyMMdd", Locale.ENGLISH);
        intent.putExtra(EXTRA_DATE, Integer.parseInt(fmt.format(new Date())));
        startActivity(intent);
        finish();
    }

    @Override
    protected void onResume() {
        super.onResume();
        mapView.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        mapView.onPause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (timerHandler != null) timerHandler.removeCallbacks(timerRunnable);
        if (waypointReceiver != null)
            LocalBroadcastManager.getInstance(this).unregisterReceiver(waypointReceiver);
        if (stoppedReceiver != null)
            LocalBroadcastManager.getInstance(this).unregisterReceiver(stoppedReceiver);
        mapView.onDetach();
    }

}
