package io.actifit.fitnesstracker.actifitfitnesstracker;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;

import androidx.health.connect.client.HealthConnectClient;
import androidx.health.connect.client.permission.HealthPermission;
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord;
import androidx.health.connect.client.records.DistanceRecord;
import androidx.health.connect.client.records.HeartRateRecord;
import androidx.health.connect.client.records.StepsRecord;
import androidx.health.connect.client.request.ReadRecordsRequest;
import androidx.health.connect.client.response.ReadRecordsResponse;
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
import androidx.health.connect.client.records.metadata.Metadata;

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
        return hasAllPermissions().thenCompose(hasPermissions -> {
            if (!hasPermissions) {
                Log.d(TAG, "Permissions not granted for reading steps, returning 0.");
                return CompletableFuture.completedFuture(0L);
            }

            Instant startOfDay = day.truncatedTo(ChronoUnit.DAYS).toInstant();
            Instant endOfDay = day.plusDays(1).truncatedTo(ChronoUnit.DAYS).toInstant();
            TimeRangeFilter timeRangeFilter = TimeRangeFilter.between(startOfDay, endOfDay);

            ReadRecordsRequest<StepsRecord> request = new ReadRecordsRequest<>(
                    JvmClassMappingKt.getKotlinClass(StepsRecord.class),
                    timeRangeFilter,
                    Collections.emptySet(), 
                    true, 
                    1000, 
                    null
            );


            return FutureKt.future(coroutineScope, Dispatchers.getDefault(), CoroutineStart.DEFAULT,
                    (scope, continuation) -> {
                        try {
                            return healthConnectClient.readRecords(request, continuation);
                        } catch (Throwable e) {
                            throw new RuntimeException(e);
                        }
                    }
            ).thenApply(response -> {
                long totalSteps = 0;
                ReadRecordsResponse<StepsRecord> recordsResponse = (ReadRecordsResponse<StepsRecord>) response;

                for (StepsRecord record : recordsResponse.getRecords()) {
                    String sourceApp = record.getMetadata().getDataOrigin().getPackageName();
                    int recordingMethod = record.getMetadata().getRecordingMethod();

                    if (recordingMethod != Metadata.RECORDING_METHOD_MANUAL_ENTRY) { // 1 == Metadata.RECORDING_METHOD_MANUALLY_ENTERED
                        totalSteps += record.getCount();
                        Log.d(TAG, "Found " + record.getCount() + " steps from app: " + sourceApp + " (Method: " + recordingMethod + ")");
                    } else {
                        Log.w(TAG, "Skipping " + record.getCount() + " manually entered steps from app: " + sourceApp);
                    }
                }

                return totalSteps;
            });
        }).exceptionally(e -> {
            Log.e(TAG, "Error in the readStepsData pipeline", e);
            return 0L; 
        });
    }


    public Intent createPermissionRequestIntent() {
        Intent intent = new Intent("androidx.health.connect.client.action.HEALTH_CONNECT_SETTINGS");
        intent.setPackage("com.google.android.apps.healthdata");
        return intent;
    }
}
