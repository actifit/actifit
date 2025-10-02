package io.actifit.fitnesstracker.actifitfitnesstracker;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;

import androidx.health.connect.client.HealthConnectClient;
import androidx.health.connect.client.request.AggregateRequest;
import androidx.health.connect.client.aggregate.AggregationResult;
import androidx.health.connect.client.PermissionController;
import androidx.health.connect.client.permission.HealthPermission;
import androidx.health.connect.client.records.StepsRecord;
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord;
import androidx.health.connect.client.records.DistanceRecord;
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
import kotlin.jvm.JvmClassMappingKt;
import kotlin.jvm.functions.Function2;
import kotlinx.coroutines.CoroutineScope;
import kotlinx.coroutines.CoroutineScopeKt;
import kotlinx.coroutines.CoroutineStart;
import kotlinx.coroutines.Dispatchers;
import kotlin.coroutines.EmptyCoroutineContext;
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
                    HealthPermission.getReadPermission(JvmClassMappingKt.getKotlinClass(DistanceRecord.class))
            ).collect(Collectors.toSet())
    );

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
                    // Manually cast the result of the suspend function to the expected type
                    Object result = healthConnectClient.getPermissionController().getGrantedPermissions((Continuation<? super Set<String>>) continuation);
                    Set<String> grantedPermissions = (Set<String>) result;
                    Log.d(TAG, "HC invoke inside HCM hasallpermissions");
                    return grantedPermissions.containsAll(permissions);
                } catch (Throwable e) {
                    // The CompletableFuture handles the exception when the suspend function throws
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
            return FutureKt.future(coroutineScope, Dispatchers.getDefault(), CoroutineStart.DEFAULT, new Function2<CoroutineScope, Continuation<? super Long>, Object>() {
                @Override
                public Object invoke(CoroutineScope scope, Continuation<? super Long> continuation) {
                    try {
                        Instant startOfDay = day.truncatedTo(ChronoUnit.DAYS).toInstant();
                        Instant endOfDay = day.plusDays(1).truncatedTo(ChronoUnit.DAYS).toInstant();
                        AggregateRequest request = new AggregateRequest(
                                Collections.singleton(StepsRecord.COUNT_TOTAL),
                                TimeRangeFilter.between(startOfDay, endOfDay),
                                Collections.emptySet()
                        );

                        // Cast continuation for the intermediate suspend call
                        Object result = healthConnectClient.aggregate(request, (Continuation<? super AggregationResult>) continuation);

                        AggregationResult response = (AggregationResult) result;
                        Long steps = response.get(StepsRecord.COUNT_TOTAL);
                        return steps != null ? steps : 0L;
                    } catch (Throwable e) {
                        throw new RuntimeException(e);
                    }
                }
            });
        }).exceptionally(e -> {
            Log.e(TAG, "Error in readStepsData or permission check", e);
            return 0L;
        });
    }

    // Permission request must be handled in an Activity/Fragment using ActivityResultLauncher.
    // The HealthConnectManager should not have this method.
}
