package io.actifit.fitnesstracker.actifitfitnesstracker;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Log;
import android.view.View;
import android.view.ViewParent;
import android.widget.Toast;

import androidx.activity.ComponentActivity;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.health.connect.client.HealthConnectClient;
import androidx.health.connect.client.PermissionController;
import androidx.lifecycle.LifecycleCoroutineScope;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.LifecycleOwnerKt;

import java.time.ZonedDateTime;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import kotlin.Unit;
import kotlin.coroutines.Continuation;
import kotlin.jvm.functions.Function2;
import kotlinx.coroutines.BuildersKt;
import kotlinx.coroutines.CoroutineStart;
import kotlinx.coroutines.Dispatchers;

/**
 * Manages all activity tracking methods: device sensors, Fitbit, and Health Connect.
 * Handles tracking mode switching, Health Connect permissions, and fallback logic.
 * Extracted from MainActivity to reduce class size.
 */
public class TrackingManager {

    private static final String TAG = MainActivity.TAG;

    private final Context context;
    private final Activity activity;
    private final SharedPreferences sharedPreferences;

    private HealthConnectManager healthConnectManager;
    private LifecycleCoroutineScope lifecycleCoroutineScope;

    private final AtomicBoolean healthConnectCheckRunning = new AtomicBoolean(false);

    private ActivityResultLauncher<Intent> hcExternalActivityLauncher;
    private ActivityResultLauncher<Set<String>> hcRequestPermissionsLauncher;

    private View healthConnectStatusView;
    private ChartManager chartManager;
    private StepsDBHelper mStepsDBHelper;

    private Intent mServiceIntent;
    private ActivityMonitorService mSensorService;

    public TrackingManager(Context context, Activity activity) {
        this.context = context;
        this.activity = activity;
        this.sharedPreferences = context.getSharedPreferences("actifitSets", Context.MODE_PRIVATE);
    }

    public void initialize(ChartManager chartManager, StepsDBHelper stepsDBHelper, View healthConnectStatusView) {
        this.chartManager = chartManager;
        this.mStepsDBHelper = stepsDBHelper;
        this.healthConnectStatusView = healthConnectStatusView;

        healthConnectManager = new HealthConnectManager(context.getApplicationContext());
        lifecycleCoroutineScope = LifecycleOwnerKt.getLifecycleScope((LifecycleOwner) activity);

        hcExternalActivityLauncher = ((ComponentActivity) activity).registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(), result -> {
                    Log.d(TAG, "Returned from external HC activity. Result code: " + result.getResultCode());
                    BuildersKt.launch(lifecycleCoroutineScope, Dispatchers.getDefault(), CoroutineStart.DEFAULT,
                            (scope, continuation) -> {
                                checkHealthConnectStatusAndPermissions();
                                return Unit.INSTANCE;
                            });
                });

        hcRequestPermissionsLauncher = ((ComponentActivity) activity).registerForActivityResult(
                PermissionController.createRequestPermissionResultContract(), grantedPermissions -> {
                    Log.d(TAG, "Returned from Health Connect permissions UI.");
                    BuildersKt.launch(lifecycleCoroutineScope, Dispatchers.getDefault(), CoroutineStart.DEFAULT,
                            (scope, continuation) -> {
                                healthConnectManager.hasAllPermissions().whenComplete((hasPermissions, throwable) -> {
                                    if (hasPermissions) {
                                        Log.d(TAG, "HC permissions granted. Proceeding to read data.");
                                        ((Activity) context).runOnUiThread(() -> {
                                            SharedPreferences.Editor editor = sharedPreferences.edit();
                                            editor.putString("dataTrackingSystem", context.getString(R.string.health_connect_tracking_ntt));
                                            editor.apply();
                                            checkPermissionsAndReadData();
                                        });
                                    } else {
                                        Log.d(TAG, "HC permissions NOT granted. Falling back.");
                                        ((Activity) context).runOnUiThread(() -> Toast.makeText(context,
                                                "Health Connect permissions not granted. Using device sensors.",
                                                Toast.LENGTH_SHORT).show());
                                        useDefaultTrackingMethod();
                                    }
                                    healthConnectCheckRunning.set(false);
                                });
                                return Unit.INSTANCE;
                            });
                });

        healthConnectStatusView.setOnClickListener(v -> checkHealthConnectStatusAndPermissions());
    }

    public void startHealthConnectCheck() {
        healthConnectStatusView.setVisibility(View.GONE);
        try {
            BuildersKt.launch(lifecycleCoroutineScope, Dispatchers.getDefault(), CoroutineStart.DEFAULT,
                    (scope, continuation) -> {
                        try {
                            checkHealthConnectStatusAndPermissions();
                            return Unit.INSTANCE;
                        } catch (Exception innerEx) {
                            Log.e(TAG, "Exception in HC check coroutine: " + innerEx.getMessage(), innerEx);
                            useDefaultTrackingMethod();
                            return Unit.INSTANCE;
                        }
                    });
        } catch (Exception ex) {
            Log.e(TAG, "CRITICAL: Exception launching HC check coroutine: " + ex.getMessage(), ex);
            useDefaultTrackingMethod();
        }
    }

    public void checkHealthConnectStatusAndPermissions() {
        if (healthConnectCheckRunning.getAndSet(true)) {
            Log.d(TAG, "Health Connect check is already running.");
            return;
        }
        int sdkStatus = HealthConnectClient.getSdkStatus(context);
        Log.d(TAG, "HC SDK Status: " + sdkStatus);
        healthConnectStatusView.setVisibility(View.VISIBLE);

        if (sdkStatus == HealthConnectClient.SDK_AVAILABLE) {
            healthConnectManager.hasAllPermissions().whenComplete((hasPermissions, throwable) -> {
                if (throwable != null) {
                    Log.e(TAG, "Error checking HC permissions: " + throwable.getMessage(), throwable);
                    ((Activity) context).runOnUiThread(() -> Toast.makeText(context,
                            "Error accessing Health Connect. Falling back.", Toast.LENGTH_LONG).show());
                    useDefaultTrackingMethod();
                    healthConnectCheckRunning.set(false);
                    return;
                }
                if (!hasPermissions) {
                    Log.d(TAG, "HC permissions not granted. Showing rationale.");
                    showPermissionsRationaleDialog();
                } else {
                    Log.d(TAG, "HC permissions granted. Proceeding.");
                    healthConnectStatusView.setVisibility(View.GONE);
                    checkPermissionsAndReadData();
                    healthConnectCheckRunning.set(false);
                }
            });
        } else if (sdkStatus == HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED) {
            Log.d(TAG, "HC needs update.");
            showInstallOrUpdateHealthConnectRationale(true);
            healthConnectCheckRunning.set(false);
        } else {
            Log.d(TAG, "HC SDK unavailable.");
            showInstallOrUpdateHealthConnectRationale(false);
            healthConnectCheckRunning.set(false);
        }
    }

    private void showPermissionsRationaleDialog() {
        ((Activity) context).runOnUiThread(() -> {
            new AlertDialog.Builder(activity)
                    .setTitle("Health Connect Permission")
                    .setMessage("To track your activity, Actifit needs permission to access your step data via Health Connect.")
                    .setPositiveButton("Request Permission", (dialog, which) -> requestHealthConnectPermissionsUI())
                    .setNeutralButton("Open Settings", (dialog, which) -> {
                        try {
                            Intent intent = new Intent("androidx.health.connect.client.ACTION_MANAGE_HEALTH_PERMISSIONS");
                            intent.putExtra(Intent.EXTRA_PACKAGE_NAME, activity.getPackageName());
                            hcExternalActivityLauncher.launch(intent);
                        } catch (Exception e) {
                            Toast.makeText(context, "Could not open Health Connect settings.", Toast.LENGTH_LONG).show();
                        }
                    })
                    .setNegativeButton("Not Now", (dialog, which) -> {
                        Toast.makeText(context, "Health Connect features will be limited.", Toast.LENGTH_SHORT).show();
                        useDefaultTrackingMethod();
                        healthConnectCheckRunning.set(false);
                    })
                    .setOnDismissListener(dialog -> healthConnectCheckRunning.set(false))
                    .show();
        });
    }

    public void requestHealthConnectPermissionsUI() {
        try {
            int sdkStatus = HealthConnectClient.getSdkStatus(context);
            if (sdkStatus == HealthConnectClient.SDK_AVAILABLE) {
                hcRequestPermissionsLauncher.launch(healthConnectManager.permissions);
            } else {
                showInstallOrUpdateHealthConnectRationale(sdkStatus == HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED);
            }
        } catch (Exception e) {
            showInstallOrUpdateHealthConnectRationale(false);
        }
    }

    public void showInstallOrUpdateHealthConnectRationale(boolean needsUpdate) {
        ((Activity) context).runOnUiThread(() -> {
            new AlertDialog.Builder(activity)
                    .setTitle(needsUpdate ? "Health Connect Update Needed" : "Health Connect App Required")
                    .setMessage(needsUpdate
                            ? "The Health Connect app requires an update. Please update it from the Play Store."
                            : "The Health Connect app is necessary. Please install it from the Play Store.")
                    .setPositiveButton("Go to Play Store", (dialog, which) -> healthConnectManager.installHealthConnect())
                    .setNegativeButton("Not Now", (dialog, which) -> {
                        Toast.makeText(context, "Health Connect features will be limited.", Toast.LENGTH_SHORT).show();
                        useDefaultTrackingMethod();
                    })
                    .show();
        });
    }

    public void checkPermissionsAndReadData() {
        if (!isHealthConnectEnabledInSettings()) {
            Log.d(TAG, "Health Connect disabled in settings. Falling back.");
            useDefaultTrackingMethod();
            return;
        }

        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString("dataTrackingSystem", context.getString(R.string.health_connect_tracking_ntt));
        editor.apply();

        chartManager.hideCharts();
        View hcTracking = ((android.app.Activity) context).findViewById(R.id.health_connect_active);
        hcTracking.setVisibility(View.VISIBLE);

        ZonedDateTime today = ZonedDateTime.now();
        healthConnectManager.readAndPersistStepsData(today, mStepsDBHelper).whenComplete((steps, readThrowable) -> {
            ((Activity) context).runOnUiThread(() -> {
                if (readThrowable != null) {
                    Log.e(TAG, "Error reading steps from HC: " + readThrowable.getMessage(), readThrowable);
                    Toast.makeText(context, "Failed to read data from Health Connect. Falling back.", Toast.LENGTH_LONG).show();
                    useDefaultTrackingMethod();
                    return;
                }
                Log.d(TAG, "Steps from Health Connect: " + steps);
                Calendar mCalendar = Calendar.getInstance();
                editor.putString("healthConnectLastSyncDate", new SimpleDateFormat("yyyyMMdd").format(mCalendar.getTime()));
                editor.putLong("healthConnectLastSyncTime", System.currentTimeMillis());
                if (steps != null && steps > 0) {
                    editor.putInt("healthConnectSyncCount", steps.intValue());
                } else {
                    editor.putInt("healthConnectSyncCount", 0);
                }
                editor.apply();
                chartManager.displayActivityChartHealthConnect(steps != null ? steps.intValue() : 0, true);

                View barChartContainer = ((android.app.Activity) context).findViewById(R.id.bar_chart_container);
                if (barChartContainer != null) barChartContainer.setVisibility(View.VISIBLE);
                View chartSwitcherView = ((android.app.Activity) context).findViewById(R.id.chart_switcher);
                if (chartSwitcherView != null) chartSwitcherView.setVisibility(View.VISIBLE);
                chartManager.displayChartDataHC(true);
                chartManager.displayDayChartDataHC(true);
                if (context instanceof MainActivity) ((MainActivity) context).refreshSecondaryCards();

                if (mStepsDBHelper != null) {
                    Runnable heatmapRefresh = () -> ((Activity) context).runOnUiThread(() -> {
                        chartManager.displayChartDataHC(false);
                        if (context instanceof MainActivity) ((MainActivity) context).buildMonthHeatmap();
                    });
                    int hcEntryCount = mStepsDBHelper.readHCStepsEntries().size();
                    Log.d(TAG, "HC entry count: " + hcEntryCount);
                    if (hcEntryCount < 30) {
                        Log.d(TAG, "HC table has fewer than 30 days — triggering backfill for " + (30 - hcEntryCount) + " missing days");
                        healthConnectManager.backfillHCHistory(mStepsDBHelper, 30, heatmapRefresh);
                    } else {
                        try {
                            String lastHCDate = mStepsDBHelper.getLastHCDate();
                            java.util.Date last = new SimpleDateFormat("yyyyMMdd").parse(lastHCDate);
                            long diffMs = new java.util.Date().getTime() - last.getTime();
                            int diffDays = (int) (diffMs / (1000 * 60 * 60 * 24));
                            Log.d(TAG, "HC gap days: " + diffDays);
                            if (diffDays > 1) {
                                healthConnectManager.backfillHCHistory(mStepsDBHelper, diffDays, heatmapRefresh);
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "Error calculating HC gap: " + e.getMessage());
                        }
                    }
                }
            });
        });
    }

    public boolean isHealthConnectPermActivated() {
        int sdkStatus = HealthConnectClient.getSdkStatus(context);
        if (sdkStatus == HealthConnectClient.SDK_AVAILABLE) {
            try {
                return healthConnectManager.hasAllPermissions().get();
            } catch (Exception e) {
                Log.e(TAG, "Failed to get HC permissions: " + e.getMessage(), e);
                return false;
            }
        }
        return false;
    }

    public boolean isHealthConnectEnabledInSettings() {
        String dataTrackingSystem = sharedPreferences.getString("dataTrackingSystem", context.getString(R.string.health_connect_tracking_ntt));
        return dataTrackingSystem.equals(context.getString(R.string.health_connect_tracking_ntt));
    }

    public void useDefaultTrackingMethod() {
        ((Activity) context).runOnUiThread(() -> {
            Log.d(TAG, "Falling back to default tracking method.");
            try {
                String dataTrackingSystem = sharedPreferences.getString("dataTrackingSystem", context.getString(R.string.device_tracking_ntt));
                chartManager.hideCharts();

                if (dataTrackingSystem.equals(context.getString(R.string.fitbit_tracking_ntt))) {
                    View thirdPartyTracking = ((android.app.Activity) context).findViewById(R.id.third_party_active);
                    thirdPartyTracking.setVisibility(View.VISIBLE);
                    int fitbitStepCount = sharedPreferences.getInt("fitbitSyncCount", 0);
                    chartManager.displayActivityChartFitbit(fitbitStepCount, true);
                    View barChartContainer = ((android.app.Activity) context).findViewById(R.id.bar_chart_container);
                    barChartContainer.setVisibility(View.VISIBLE);
                    chartManager.displayChartDataFitbit(true);
                } else if (dataTrackingSystem.equals(context.getString(R.string.health_connect_tracking_ntt))) {
                    View barChartContainer = ((android.app.Activity) context).findViewById(R.id.bar_chart_container);
                    barChartContainer.setVisibility(View.VISIBLE);
                    View chartSwitcher = ((android.app.Activity) context).findViewById(R.id.chart_switcher);
                    chartSwitcher.setVisibility(View.VISIBLE);
                    chartManager.displayChartDataHC(true);
                    chartManager.displayDayChartDataHC(true);
                    if (context instanceof MainActivity) ((MainActivity) context).buildMonthHeatmap();
                    if (mStepsDBHelper != null) {
                        Runnable heatmapRefresh = () -> ((Activity) context).runOnUiThread(() -> {
                            chartManager.displayChartDataHC(false);
                            if (context instanceof MainActivity) ((MainActivity) context).buildMonthHeatmap();
                        });
                        int hcEntryCount = mStepsDBHelper.readHCStepsEntries().size();
                        Log.d(TAG, "HC entry count (useDefaultTrackingMethod): " + hcEntryCount);
                        if (hcEntryCount < 30) {
                            Log.d(TAG, "HC table has fewer than 30 days — triggering backfill for " + (30 - hcEntryCount) + " missing days");
                            healthConnectManager.backfillHCHistory(mStepsDBHelper, 30, heatmapRefresh);
                        } else {
                            try {
                                String lastHCDate = mStepsDBHelper.getLastHCDate();
                                java.util.Date last = new SimpleDateFormat("yyyyMMdd").parse(lastHCDate);
                                long diffMs = new java.util.Date().getTime() - last.getTime();
                                int diffDays = (int) (diffMs / (1000 * 60 * 60 * 24));
                                Log.d(TAG, "HC gap days (useDefaultTrackingMethod): " + diffDays);
                                if (diffDays > 1) {
                                    healthConnectManager.backfillHCHistory(mStepsDBHelper, diffDays, heatmapRefresh);
                                }
                            } catch (Exception e) {
                                Log.e(TAG, "Error calculating HC gap: " + e.getMessage());
                            }
                        }
                    }
                } else {
                    View pieChartView = ((android.app.Activity) context).findViewById(R.id.step_pie_chart);
                    if (pieChartView != null && pieChartView.getParent() instanceof View) {
                        ((View) pieChartView.getParent()).setVisibility(View.VISIBLE);
                    }
                    View chartSwitcher = ((android.app.Activity) context).findViewById(R.id.chart_switcher);
                    chartSwitcher.setVisibility(View.VISIBLE);
                    View barChartContainer = ((android.app.Activity) context).findViewById(R.id.bar_chart_container);
                    barChartContainer.setVisibility(View.VISIBLE);

                    if (mServiceIntent == null) {
                        chartManager.displayActivityChart(0, false);
                        return;
                    }
                    if (!isServiceRunning(mSensorService.getClass())) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            context.startForegroundService(mServiceIntent);
                        } else {
                            context.startService(mServiceIntent);
                        }
                    }
                    if (mStepsDBHelper != null) {
                        chartManager.displayActivityChart((int) mStepsDBHelper.fetchTodayStepCount(), true);
                    } else {
                        chartManager.displayActivityChart(0, false);
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Error in useDefaultTrackingMethod: " + e.getMessage());
                try {
                    chartManager.hideCharts();
                    View pieChartView = ((android.app.Activity) context).findViewById(R.id.step_pie_chart);
                    if (pieChartView != null && pieChartView.getParent() instanceof View) {
                        ((View) pieChartView.getParent()).setVisibility(View.VISIBLE);
                    }
                    chartManager.displayActivityChart(0, false);
                } catch (Exception ex) {
                    Log.e(TAG, "Error in fallback UI reset: " + ex.getMessage());
                }
            }
        });
    }

    private boolean isServiceRunning(Class<?> serviceClass) {
        android.app.ActivityManager manager = (android.app.ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        for (android.app.ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }

    public void setServiceIntent(Intent intent) {
        this.mServiceIntent = intent;
    }

    public void setSensorService(ActivityMonitorService service) {
        this.mSensorService = service;
    }

    public Intent getServiceIntent() {
        return mServiceIntent;
    }

    public ActivityMonitorService getSensorService() {
        return mSensorService;
    }

    public HealthConnectManager getHealthConnectManager() {
        return healthConnectManager;
    }
}
