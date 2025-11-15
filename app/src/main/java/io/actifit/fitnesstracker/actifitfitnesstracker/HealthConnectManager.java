package io.actifit.fitnesstracker.actifitfitnesstracker;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;
// import android.widget.Toast; // Toast should ideally not be in a manager class

import androidx.health.connect.client.HealthConnectClient;
import androidx.health.connect.client.records.HeartRateRecord;
import androidx.health.connect.client.request.AggregateRequest;
import androidx.health.connect.client.aggregate.AggregationResult;
import androidx.health.connect.client.PermissionController; // Keep this import
import androidx.health.connect.client.permission.HealthPermission;
import androidx.health.connect.client.records.StepsRecord;
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord;
import androidx.health.connect.client.records.DistanceRecord;
import androidx.health.connect.client.time.TimeRangeFilter;
// import androidx.lifecycle.LifecycleOwnerKt; // This import is likely not needed here

import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.Set;
// import java.util.ArrayList; // Not needed if createRequestPermissionIntent is used directly
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import kotlin.Unit;
import kotlin.coroutines.Continuation;
import kotlin.jvm.JvmClassMappingKt;
import kotlin.jvm.functions.Function2;
import kotlinx.coroutines.BuildersKt;
import kotlinx.coroutines.CoroutineScope;
import kotlinx.coroutines.CoroutineScopeKt;
import kotlinx.coroutines.CoroutineStart;
import kotlinx.coroutines.Dispatchers;
import kotlin.coroutines.EmptyCoroutineContext;
// import kotlinx.coroutines.Job;
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
        // We use FutureKt.future to create a CompletableFuture from a coroutine block.
        return FutureKt.future(coroutineScope, Dispatchers.getDefault(), CoroutineStart.DEFAULT,
                new Function2<CoroutineScope, Continuation<? super Long>, Object>() {
                    @Override
                    public Object invoke(CoroutineScope scope, Continuation<? super Long> continuation) {
                        try {
                            // --- 1. Fix for getGrantedPermissions() call ---
                            // Cast the final Continuation<? super Long> to Continuation<? super Set<String>>
                            @SuppressWarnings("unchecked")
                            Continuation<? super Set<String>> setContinuation = (Continuation<? super Set<String>>) continuation;

                            Object hasPermResult = healthConnectClient.getPermissionController().getGrantedPermissions(setContinuation);

                            // If the call suspends, we MUST return the suspension marker immediately.
                            if (hasPermResult == kotlin.coroutines.intrinsics.CoroutineSingletons.COROUTINE_SUSPENDED) {
                                return hasPermResult;
                            }

                            // Safe cast after potential suspension/resumption
                            Set<String> grantedPermissions = (Set<String>) hasPermResult;

                            if (!grantedPermissions.containsAll(permissions)) {
                                Log.d(TAG, "Permissions not granted for reading steps, returning 0.");
                                return 0L;
                            }

                            // --- 2. Fix for aggregate() call ---
                            Instant startOfDay = day.truncatedTo(ChronoUnit.DAYS).toInstant();
                            Instant endOfDay = day.plusDays(1).truncatedTo(ChronoUnit.DAYS).toInstant();

                            AggregateRequest request = new AggregateRequest(
                                    Set.of(StepsRecord.COUNT_TOTAL),
                                    TimeRangeFilter.between(startOfDay, endOfDay),
                                    Collections.emptySet()
                            );

                            // Cast the final Continuation<? super Long> to Continuation<? super AggregationResult>
                            @SuppressWarnings("unchecked")
                            Continuation<? super AggregationResult> aggContinuation = (Continuation<? super AggregationResult>) continuation;

                            Object aggResult = healthConnectClient.aggregate(
                                    request, aggContinuation
                            );

                            // If the call suspends, we MUST return the suspension marker immediately.
                            if (aggResult == kotlin.coroutines.intrinsics.CoroutineSingletons.COROUTINE_SUSPENDED) {
                                return aggResult;
                            }

                            // Safe cast after potential suspension/resumption
                            AggregationResult response = (AggregationResult) aggResult;

                            Long steps = response.get(StepsRecord.COUNT_TOTAL);
                            return steps != null ? steps : 0L;

                        } catch (Throwable e) {
                            Log.e(TAG, "Error in readStepsData suspend call", e);
                            throw new RuntimeException(e);
                        }
                    }
                }
        ).exceptionally(e -> {
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