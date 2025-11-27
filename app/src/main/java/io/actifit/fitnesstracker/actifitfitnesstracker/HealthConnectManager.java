package io.actifit.fitnesstracker.actifitfitnesstracker;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;

import androidx.health.connect.client.HealthConnectClient;
import androidx.health.connect.client.aggregate.AggregationResult;
import androidx.health.connect.client.permission.HealthPermission;
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord;
import androidx.health.connect.client.records.DistanceRecord;
import androidx.health.connect.client.records.HeartRateRecord;
import androidx.health.connect.client.records.StepsRecord;
import androidx.health.connect.client.request.AggregateRequest;
import androidx.health.connect.client.time.TimeRangeFilter;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
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

public class HealthConnectManager {

    private static final String TAG = "HealthConnectManager";
    private final Context context;
    private final HealthConnectClient healthConnectClient;
    private final CoroutineScope coroutineScope;

    public final Set<String> permissions = Collections.unmodifiableSet(
            Stream.of(
                    HealthPermission.getReadPermission(JvmClassMappingKt.getKotlinClass(StepsRecord.class)),
                    HealthPermission.getReadPermission(JvmClassMappingKt.getKotlinClass(ActiveCaloriesBurnedRecord.class)),
                    HealthPermission.getReadPermission(JvmClassMappingKt.getKotlinClass(HeartRateRecord.class)),
                    HealthPermission.getReadPermission(JvmClassMappingKt.getKotlinClass(DistanceRecord.class))
            ).collect(Collectors.toSet())
    );

    public HealthConnectClient getHealthConnectClient() {
        return healthConnectClient;
    }

    public HealthConnectManager(Context context) {
        this.context = context;
        this.healthConnectClient = HealthConnectClient.getOrCreate(context);
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
        return FutureKt.future(coroutineScope, Dispatchers.getDefault(), CoroutineStart.DEFAULT, new Function2<CoroutineScope, Continuation<? super Boolean>, Object>() {
            @Override
            public Object invoke(CoroutineScope scope, Continuation<? super Boolean> continuation) {
                try {
                    // *** CRITICAL FIX: Cast the Continuation to the type expected by getGrantedPermissions() ***
                    @SuppressWarnings("unchecked")
                    Continuation<? super Set<String>> setContinuation = (Continuation<? super Set<String>>) continuation;

                    Object result = healthConnectClient.getPermissionController().getGrantedPermissions(setContinuation);

                    // Handle Kotlin suspension marker
                    if (result == kotlin.coroutines.intrinsics.CoroutineSingletons.COROUTINE_SUSPENDED) {
                        return result; // Must return the suspension marker
                    }

                    // After resumption, the result is the granted Set<String>
                    // We can safely cast here because the continuation was correctly typed for the call.
                    Set<String> grantedPermissions = (Set<String>) result;

                    Log.d(TAG, "HC invoke inside HCM hasAllPermissions. Granted: " + grantedPermissions.size() + ", Expected: " + permissions.size());
                    return grantedPermissions.containsAll(permissions); // Returns a Boolean
                } catch (Throwable e) {
                    Log.e(TAG, "Error in hasAllPermissions suspend call", e);
                    throw new RuntimeException(e); // Rethrow to complete exceptionally
                }
            }
        });
    }



    public CompletableFuture<Long> readStepsData(ZonedDateTime day) {
        // First, check for permissions. hasAllPermissions() already returns a CompletableFuture<Boolean>.
        return hasAllPermissions().thenCompose(hasPermissions -> {
            if (!hasPermissions) {
                Log.d(TAG, "Permissions not granted for reading steps, returning 0.");
                // If permissions are not granted, return a completed future with 0.
                return CompletableFuture.completedFuture(0L);
            }

            // If permissions are granted, proceed to aggregate the data.
            Instant startOfDay = day.truncatedTo(ChronoUnit.DAYS).toInstant();
            Instant endOfDay = day.plusDays(1).truncatedTo(ChronoUnit.DAYS).toInstant();

            AggregateRequest request = new AggregateRequest(
                    Set.of(StepsRecord.COUNT_TOTAL),
                    TimeRangeFilter.between(startOfDay, endOfDay),
                    Collections.emptySet()
            );

            // Create a new CompletableFuture for the aggregation call.
            return FutureKt.future(coroutineScope, Dispatchers.getDefault(), CoroutineStart.DEFAULT,
                    (scope, continuation) -> {
                        try {
                            // This is the suspend call to aggregate data.
                            return healthConnectClient.aggregate(request, continuation);
                        } catch (Throwable e) {
                            Log.e(TAG, "Error in aggregate suspend call", e);
                            // Rethrow to fail the future.
                            throw new RuntimeException(e);
                        }
                    }
            ).thenApply(aggregationResult -> {
                // Once the aggregation is complete, extract the step count.
                Long steps = ((AggregationResult) aggregationResult).get(StepsRecord.COUNT_TOTAL);
                Log.d(TAG, "Successfully read steps from Health Connect: " + steps);
                return steps != null ? steps : 0L;
            });

        }).exceptionally(e -> {
            // This will catch exceptions from hasAllPermissions or the aggregation future.
            Log.e(TAG, "Error in readStepsData pipeline", e);
            return 0L;
        });
    }

    public Intent createPermissionRequestIntent() {
        Intent intent = new Intent("androidx.health.connect.client.action.HEALTH_CONNECT_SETTINGS");
        intent.setPackage("com.google.android.apps.healthdata");
        return intent;
    }
}