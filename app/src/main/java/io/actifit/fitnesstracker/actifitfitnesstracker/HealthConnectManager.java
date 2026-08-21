package io.actifit.fitnesstracker.actifitfitnesstracker;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;

import androidx.health.connect.client.HealthConnectClient;
import androidx.health.connect.client.permission.HealthPermission;
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord;
import androidx.health.connect.client.records.DistanceRecord;
//import androidx.health.connect.client.records.HeartRateRecord;
import androidx.health.connect.client.records.StepsRecord;
import androidx.health.connect.client.units.Energy;
import androidx.health.connect.client.units.Length;
import androidx.health.connect.client.aggregate.AggregateMetric;
import androidx.health.connect.client.aggregate.AggregationResult;
import androidx.health.connect.client.request.AggregateRequest;
import androidx.health.connect.client.request.ReadRecordsRequest;
import androidx.health.connect.client.response.ReadRecordsResponse;
import androidx.health.connect.client.time.TimeRangeFilter;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import kotlin.coroutines.Continuation;
import kotlin.coroutines.EmptyCoroutineContext;
import kotlin.jvm.JvmClassMappingKt;
import kotlin.jvm.functions.Function2;
import kotlinx.coroutines.CoroutineScope;
import kotlinx.coroutines.CoroutineScopeKt;
import kotlinx.coroutines.CoroutineStart;
import kotlinx.coroutines.Dispatchers;
import kotlinx.coroutines.future.FutureKt;
import androidx.health.connect.client.records.metadata.Metadata;

public class HealthConnectManager {

    private static final String TAG = "HealthConnectManager";
    private final Context context;
    private HealthConnectClient healthConnectClient;
    private final CoroutineScope coroutineScope;

    // REQUIRED gate — steps only, so existing users who granted only steps keep working
    public final Set<String> permissions = Collections.unmodifiableSet(
            Stream.of(
                    HealthPermission.getReadPermission(JvmClassMappingKt.getKotlinClass(StepsRecord.class))
            ).collect(Collectors.toSet())
    );

    // permission strings for the optional activity-ring metrics (distance + active calories)
    public static final String PERM_DISTANCE =
            HealthPermission.getReadPermission(JvmClassMappingKt.getKotlinClass(DistanceRecord.class));
    public static final String PERM_CALORIES =
            HealthPermission.getReadPermission(JvmClassMappingKt.getKotlinClass(ActiveCaloriesBurnedRecord.class));

    // what we ASK for on setup: steps + the optional metrics. The gate above still only needs
    // steps, so granting a subset never breaks Health Connect mode.
    public final Set<String> requestPermissions = Collections.unmodifiableSet(
            Stream.of(
                    HealthPermission.getReadPermission(JvmClassMappingKt.getKotlinClass(StepsRecord.class)),
                    PERM_DISTANCE,
                    PERM_CALORIES
            ).collect(Collectors.toSet())
    );

    public HealthConnectClient getHealthConnectClient() {
        return healthConnectClient;
    }

    public HealthConnectManager(Context context) {
        this.context = context;
        HealthConnectClient client = null;
        try {
            client = HealthConnectClient.getOrCreate(context);
        } catch (Exception e) {
            Log.e(TAG, "Error initializing HealthConnectClient: " + e.getMessage());
        }
        this.healthConnectClient = client;
        this.coroutineScope = CoroutineScopeKt.CoroutineScope(Dispatchers.getDefault().plus(EmptyCoroutineContext.INSTANCE));
    }

    /**
     * Recreates the underlying HealthConnectClient. Used to recover from transient
     * "Binding to service failed" errors, where the provider reports SDK_AVAILABLE but the
     * cached client's service binding is dead. Safe to call before retrying a bind/permission call.
     */
    public void recreateClient() {
        try {
            this.healthConnectClient = HealthConnectClient.getOrCreate(context);
            Log.d(TAG, "HealthConnectClient recreated");
        } catch (Exception e) {
            Log.e(TAG, "Error recreating HealthConnectClient: " + e.getMessage());
            this.healthConnectClient = null;
        }
    }

    public boolean isHealthConnectAvailable() {
        int availabilityStatus = HealthConnectClient.getSdkStatus(context);
        return availabilityStatus == HealthConnectClient.SDK_AVAILABLE;
    }

    public void installHealthConnect() {
        String healthConnectPackage = "com.google.android.apps.healthdata";
        if (HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED) {
            Intent intent = new Intent(Intent.ACTION_VIEW)
                    .setPackage(healthConnectPackage)
                    .setData(Uri.parse("market://details?id=" + healthConnectPackage));
            if (intent.resolveActivity(context.getPackageManager()) != null) {
                context.startActivity(intent);
                return;
            }
        }
        Intent intent = new Intent(Intent.ACTION_VIEW)
                .setData(Uri.parse("market://details?id=" + healthConnectPackage));
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (intent.resolveActivity(context.getPackageManager()) != null) {
            context.startActivity(intent);
        } else {
            Log.w(TAG, "No app found to handle Play Store intent for Health Connect.");
        }
    }

    public CompletableFuture<Boolean> hasAllPermissions() {
        if (healthConnectClient == null) {
            return CompletableFuture.completedFuture(false);
        }
        return FutureKt.future(coroutineScope, Dispatchers.getDefault(), CoroutineStart.DEFAULT, new Function2<CoroutineScope, Continuation<? super Boolean>, Object>() {
            @Override
            public Object invoke(CoroutineScope scope, Continuation<? super Boolean> continuation) {
                try {
                    Continuation<? super Set<String>> setContinuation = (Continuation<? super Set<String>>) continuation;

                    Object result = healthConnectClient.getPermissionController().getGrantedPermissions(setContinuation);

                    if (result == kotlin.coroutines.intrinsics.CoroutineSingletons.COROUTINE_SUSPENDED) {
                        return result;
                    }

                    Set<String> grantedPermissions = (Set<String>) result;

                    return grantedPermissions.containsAll(permissions);
                } catch (Throwable e) {
                    throw new RuntimeException(e);
                }
            }
        });
    }

    private CompletableFuture<Set<String>> getGrantedPermissions() {
        if (healthConnectClient == null) {
            return CompletableFuture.completedFuture(Collections.emptySet());
        }
        return FutureKt.future(coroutineScope, Dispatchers.getDefault(), CoroutineStart.DEFAULT,
                (scope, continuation) -> {
                    try {
                        return healthConnectClient.getPermissionController()
                                .getGrantedPermissions((Continuation<? super Set<String>>) continuation);
                    } catch (Throwable e) {
                        throw new RuntimeException(e);
                    }
                }
        ).thenApply(r -> (Set<String>) r);
    }

    /**
     * Reads today's step / distance / active-calorie totals for the activity rings.
     * Distance and calories are read only when their (optional) permissions are granted;
     * otherwise they come back as -1 so callers can fall back to step-derived estimates.
     * @return {steps, distanceMeters, kilocalories}
     */
    public CompletableFuture<double[]> readTodayMetrics(ZonedDateTime day) {
        if (healthConnectClient == null) {
            return CompletableFuture.completedFuture(new double[]{0, -1, -1});
        }
        Instant startOfDay = day.truncatedTo(ChronoUnit.DAYS).toInstant();
        Instant endOfDay = day.plusDays(1).truncatedTo(ChronoUnit.DAYS).toInstant();
        TimeRangeFilter timeRangeFilter = TimeRangeFilter.between(startOfDay, endOfDay);

        return getGrantedPermissions().thenCompose(granted -> {
            boolean hasDistance = granted.contains(PERM_DISTANCE);
            boolean hasCalories = granted.contains(PERM_CALORIES);

            Set<AggregateMetric<?>> metrics = new HashSet<>();
            metrics.add(StepsRecord.COUNT_TOTAL);
            if (hasDistance) metrics.add(DistanceRecord.DISTANCE_TOTAL);
            if (hasCalories) metrics.add(ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL);
            AggregateRequest request = new AggregateRequest(metrics, timeRangeFilter, Collections.emptySet());

            return FutureKt.future(coroutineScope, Dispatchers.getDefault(), CoroutineStart.DEFAULT,
                    (scope, continuation) -> {
                        try {
                            return healthConnectClient.aggregate(request, continuation);
                        } catch (Throwable e) {
                            throw new RuntimeException(e);
                        }
                    }
            ).thenApply(response -> {
                AggregationResult result = (AggregationResult) response;
                Long total = result.get(StepsRecord.COUNT_TOTAL);
                double steps = (total != null) ? total : 0;
                double distanceMeters = -1;
                double kcal = -1;
                // null aggregate == permission granted but no data source for that metric today;
                // return -1 (not 0) so callers fall back to a step-derived estimate instead of
                // showing a misleading "0 km" / "0 kcal"
                if (hasDistance) {
                    Length len = result.get(DistanceRecord.DISTANCE_TOTAL);
                    distanceMeters = (len != null) ? len.getMeters() : -1;
                }
                if (hasCalories) {
                    Energy en = result.get(ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL);
                    kcal = (en != null) ? en.getKilocalories() : -1;
                }
                return new double[]{steps, distanceMeters, kcal};
            });
        }).exceptionally(e -> {
            Log.e(TAG, "readTodayMetrics failed", e);
            return new double[]{0, -1, -1};
        });
    }

    public CompletableFuture<Long> readStepsData(ZonedDateTime day) {
        if (healthConnectClient == null) {
            return CompletableFuture.completedFuture(0L);
        }
        return hasAllPermissions().thenCompose(hasPermissions -> {
            if (!hasPermissions) {
                Log.d(TAG, "Permissions not granted for reading steps, returning 0.");
                return CompletableFuture.completedFuture(0L);
            }

            Instant startOfDay = day.truncatedTo(ChronoUnit.DAYS).toInstant();
            Instant endOfDay = day.plusDays(1).truncatedTo(ChronoUnit.DAYS).toInstant();
            TimeRangeFilter timeRangeFilter = TimeRangeFilter.between(startOfDay, endOfDay);

            // Use Health Connect aggregation rather than summing raw records. Aggregation
            // de-duplicates overlapping step records across data origins (e.g. Samsung Health
            // plus a system source) via Health Connect's priority list, so we don't double count.
            Set<AggregateMetric<?>> metrics = new HashSet<>();
            metrics.add(StepsRecord.COUNT_TOTAL);
            AggregateRequest request = new AggregateRequest(metrics, timeRangeFilter, Collections.emptySet());

            return FutureKt.future(coroutineScope, Dispatchers.getDefault(), CoroutineStart.DEFAULT,
                    (scope, continuation) -> {
                        try {
                            return healthConnectClient.aggregate(request, continuation);
                        } catch (Throwable e) {
                            throw new RuntimeException(e);
                        }
                    }
            ).thenApply(response -> {
                AggregationResult result = (AggregationResult) response;
                Long total = result.get(StepsRecord.COUNT_TOTAL);
                long totalSteps = (total != null) ? total : 0L;
                Log.d(TAG, "Aggregated " + totalSteps + " de-duplicated steps for " + day.toLocalDate());
                return totalSteps;
            });
        }).exceptionally(e -> {
            Log.e(TAG, "Error in the readStepsData pipeline", e);
            return 0L; 
        });
    }


    public CompletableFuture<Long> readAndPersistStepsData(ZonedDateTime day, StepsDBHelper db) {
        if (healthConnectClient == null) {
            return CompletableFuture.completedFuture(0L);
        }
        final String dateStr = day.format(DateTimeFormatter.ofPattern("yyyyMMdd"));

        return hasAllPermissions().thenCompose(hasPermissions -> {
            if (!hasPermissions) {
                Log.d(TAG, "Permissions not granted for reading steps, returning 0.");
                return CompletableFuture.completedFuture(0L);
            }

            Instant startOfDay = day.truncatedTo(ChronoUnit.DAYS).toInstant();
            Instant endOfDay = day.plusDays(1).truncatedTo(ChronoUnit.DAYS).toInstant();
            TimeRangeFilter timeRangeFilter = TimeRangeFilter.between(startOfDay, endOfDay);

            // The daily total (which drives rewards) is read via aggregation so it stays
            // de-duplicated across overlapping data origins (Samsung Health + a system source).
            // But grouped aggregation (aggregateGroupByDuration) prorates any record that spans
            // multiple buckets EVENLY across every 15-min slice, which flattens the per-slot
            // breakdown into an identical value in every slot when the source writes coarse,
            // long-spanning records. So we read RAW records for the per-slot detail and bucket
            // each record by its real start time — preserving actual activity timing.
            Set<AggregateMetric<?>> metrics = new HashSet<>();
            metrics.add(StepsRecord.COUNT_TOTAL);
            AggregateRequest aggregateRequest =
                    new AggregateRequest(metrics, timeRangeFilter, Collections.emptySet());

            CompletableFuture<Long> totalFuture = FutureKt.future(
                    coroutineScope, Dispatchers.getDefault(), CoroutineStart.DEFAULT,
                    (scope, continuation) -> {
                        try {
                            return healthConnectClient.aggregate(aggregateRequest, continuation);
                        } catch (Throwable e) {
                            throw new RuntimeException(e);
                        }
                    }
            ).thenApply(response -> {
                AggregationResult result = (AggregationResult) response;
                Long total = result.get(StepsRecord.COUNT_TOTAL);
                return (total != null) ? total : 0L;
            });

            ReadRecordsRequest<StepsRecord> recordsRequest = new ReadRecordsRequest<>(
                    JvmClassMappingKt.getKotlinClass(StepsRecord.class),
                    timeRangeFilter,
                    Collections.emptySet(),
                    true,
                    1000,
                    null
            );

            CompletableFuture<List<StepsRecord>> recordsFuture = FutureKt.future(
                    coroutineScope, Dispatchers.getDefault(), CoroutineStart.DEFAULT,
                    (scope, continuation) -> {
                        try {
                            return healthConnectClient.readRecords(recordsRequest, continuation);
                        } catch (Throwable e) {
                            throw new RuntimeException(e);
                        }
                    }
            ).thenApply(response ->
                    ((ReadRecordsResponse<StepsRecord>) response).getRecords());

            return totalFuture.thenCombine(recordsFuture, (total, records) -> {
                // Pick the primary (highest-priority) data origin: the non-manual source that
                // contributed the most steps for the day. Using a single origin for the per-slot
                // breakdown de-duplicates the case where two apps mirror the same steps (which is
                // what previously doubled the raw sum), while still reflecting real per-slot timing.
                Map<String, Long> stepsByOrigin = new HashMap<>();
                for (StepsRecord record : records) {
                    if (record.getMetadata().getRecordingMethod()
                            == Metadata.RECORDING_METHOD_MANUAL_ENTRY) {
                        continue;
                    }
                    String origin = record.getMetadata().getDataOrigin().getPackageName();
                    stepsByOrigin.merge(origin, record.getCount(), Long::sum);
                }

                String primaryOrigin = null;
                long bestOriginTotal = -1;
                for (Map.Entry<String, Long> entry : stepsByOrigin.entrySet()) {
                    if (entry.getValue() > bestOriginTotal) {
                        bestOriginTotal = entry.getValue();
                        primaryOrigin = entry.getKey();
                    }
                }

                // Bucket only the primary origin's records into 15-min slots, keyed by start time.
                Map<Integer, Integer> slotCounts = new HashMap<>();
                if (primaryOrigin != null) {
                    for (StepsRecord record : records) {
                        if (record.getMetadata().getRecordingMethod()
                                == Metadata.RECORDING_METHOD_MANUAL_ENTRY) {
                            continue;
                        }
                        if (!primaryOrigin.equals(
                                record.getMetadata().getDataOrigin().getPackageName())) {
                            continue;
                        }
                        LocalDateTime ldt = LocalDateTime.ofInstant(
                                record.getStartTime(), ZoneId.systemDefault());
                        slotCounts.merge(bucketTimeSlot(ldt), (int) record.getCount(), Integer::sum);
                    }
                }
                // Wipe the day's existing slots first so a re-sync fully replaces any stale
                // (e.g. previously smeared) values instead of leaving orphan slots behind.
                db.clearHCSlots(dateStr);
                for (Map.Entry<Integer, Integer> slot : slotCounts.entrySet()) {
                    db.upsertHCSlot(dateStr, slot.getKey(), slot.getValue());
                }

                long totalSteps = (total != null) ? total : 0L;
                if (records.isEmpty()) {
                    Log.w(TAG, "No Health Connect step data found for " + dateStr
                            + " — source app (e.g. Samsung Health) may not be writing steps to Health Connect.");
                }
                db.upsertHCSummary(dateStr, (int) totalSteps);
                Log.d(TAG, "Persisted de-duplicated total " + totalSteps + " for " + dateStr
                        + "; per-slot detail from primary origin " + primaryOrigin
                        + " across " + slotCounts.size() + " slots");
                return totalSteps;
            });
        }).exceptionally(e -> {
            Log.e(TAG, "Error in the readAndPersistStepsData pipeline", e);
            return 0L;
        });
    }

    public void backfillHCHistory(StepsDBHelper db, int days, Runnable onComplete) {
        new Thread(() -> {
            ZonedDateTime today = ZonedDateTime.now();
            for (int i = days; i >= 1; i--) {
                ZonedDateTime day = today.minusDays(i);
                try {
                    readAndPersistStepsData(day, db).get();
                } catch (Exception e) {
                    Log.e(TAG, "backfillHCHistory error for day -" + i, e);
                }
            }
            if (onComplete != null) onComplete.run();
        }).start();
    }

    private int bucketTimeSlot(LocalDateTime ldt) {
        int hour = ldt.getHour();
        int min = ldt.getMinute();
        if (min >= 45) min = 45;
        else if (min >= 30) min = 30;
        else if (min >= 15) min = 15;
        else min = 0;
        return hour * 100 + min;
    }

    public Intent createPermissionRequestIntent() {
        Intent intent = new Intent("androidx.health.connect.client.action.HEALTH_CONNECT_SETTINGS");
        intent.setPackage("com.google.android.apps.healthdata");
        return intent;
    }
}
