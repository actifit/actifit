package io.actifit.fitnesstracker.actifitfitnesstracker;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;

import androidx.health.connect.client.HealthConnectClient;
import androidx.health.connect.client.permission.HealthPermission;
//import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord;
//import androidx.health.connect.client.records.DistanceRecord;
//import androidx.health.connect.client.records.HeartRateRecord;
import androidx.health.connect.client.records.StepsRecord;
import androidx.health.connect.client.aggregate.AggregateMetric;
import androidx.health.connect.client.aggregate.AggregationResult;
import androidx.health.connect.client.aggregate.AggregationResultGroupedByDuration;
import androidx.health.connect.client.request.AggregateRequest;
import androidx.health.connect.client.request.AggregateGroupByDurationRequest;
import androidx.health.connect.client.request.ReadRecordsRequest;
import androidx.health.connect.client.response.ReadRecordsResponse;
import androidx.health.connect.client.time.TimeRangeFilter;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
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
    private final HealthConnectClient healthConnectClient;
    private final CoroutineScope coroutineScope;

    public final Set<String> permissions = Collections.unmodifiableSet(
            Stream.of(
                    HealthPermission.getReadPermission(JvmClassMappingKt.getKotlinClass(StepsRecord.class))/*,
                    HealthPermission.getReadPermission(JvmClassMappingKt.getKotlinClass(ActiveCaloriesBurnedRecord.class)),
                    HealthPermission.getReadPermission(JvmClassMappingKt.getKotlinClass(HeartRateRecord.class)),
                    HealthPermission.getReadPermission(JvmClassMappingKt.getKotlinClass(DistanceRecord.class))*/
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

            // Aggregate grouped by 15-minute slices (matching bucketTimeSlot granularity).
            // Aggregation de-duplicates overlapping records across data origins per slice, so
            // both the per-slot chart values and the daily total avoid double counting.
            Set<AggregateMetric<?>> metrics = new HashSet<>();
            metrics.add(StepsRecord.COUNT_TOTAL);
            AggregateGroupByDurationRequest request = new AggregateGroupByDurationRequest(
                    metrics, timeRangeFilter, Duration.ofMinutes(15), Collections.emptySet());

            return FutureKt.future(coroutineScope, Dispatchers.getDefault(), CoroutineStart.DEFAULT,
                    (scope, continuation) -> {
                        try {
                            return healthConnectClient.aggregateGroupByDuration(request, continuation);
                        } catch (Throwable e) {
                            throw new RuntimeException(e);
                        }
                    }
            ).thenApply(response -> {
                List<AggregationResultGroupedByDuration> groups =
                        (List<AggregationResultGroupedByDuration>) response;

                long totalSteps = 0;
                for (AggregationResultGroupedByDuration group : groups) {
                    Long count = group.getResult().get(StepsRecord.COUNT_TOTAL);
                    if (count == null || count == 0) continue;
                    totalSteps += count;
                    LocalDateTime ldt = LocalDateTime.ofInstant(group.getStartTime(), ZoneId.systemDefault());
                    db.upsertHCSlot(dateStr, bucketTimeSlot(ldt), count.intValue());
                }
                if (groups.isEmpty()) {
                    Log.w(TAG, "No Health Connect step data found for " + dateStr
                            + " — source app (e.g. Samsung Health) may not be writing steps to Health Connect.");
                }
                db.upsertHCSummary(dateStr, (int) totalSteps);
                Log.d(TAG, "Persisted " + totalSteps + " de-duplicated steps for " + dateStr
                        + " across " + groups.size() + " slots");
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
