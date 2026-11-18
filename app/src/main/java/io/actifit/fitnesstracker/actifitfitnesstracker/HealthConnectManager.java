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
import androidx.health.connect.client.records.metadata.DataOrigin;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
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
import kotlinx.coroutines.CoroutineScope;
import kotlinx.coroutines.CoroutineScopeKt;
import kotlinx.coroutines.CoroutineStart;
import kotlinx.coroutines.Dispatchers;
import kotlinx.coroutines.future.FutureKt;
import androidx.health.connect.client.records.metadata.Metadata;

public class HealthConnectManager {

    private static final String TAG = "HealthConnectManager";
    private final Context context;
    // volatile: recreated + read from multiple background (coroutine-completion) threads —
    // recreateClient() and the read retry both write it, the async read lambdas read it.
    private volatile HealthConnectClient healthConnectClient;
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
        // When getGrantedPermissions suspends (always, with the standalone Health Connect app on
        // Android 13), the coroutine resumes with the Set itself, so the containsAll check must run
        // after the future completes rather than inside the coroutine body.
        return getGrantedPermissions().thenApply(granted -> granted.containsAll(permissions));
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

    private static final int HC_MAX_READ_RETRIES = 2;

    public CompletableFuture<Long> readAndPersistStepsData(ZonedDateTime day, StepsDBHelper db) {
        return readAndPersistStepsData(day, db, 0);
    }

    // A dead client binding (HC provider restarted/updated mid-session) surfaces as an exception in
    // the read pipeline, NOT as SDK_UNAVAILABLE — getSdkStatus still says AVAILABLE. Recreate the
    // client and retry a couple of times so a transient provider hiccup self-heals, instead of
    // stranding the user at 0 steps until the app process restarts (the "zero all day, fine next
    // morning" report). On final failure we PROPAGATE the error (never 0) so the caller falls back to
    // device sensors rather than persisting a misleading zero.
    private CompletableFuture<Long> readAndPersistStepsData(ZonedDateTime day, StepsDBHelper db, int attempt) {
        if (healthConnectClient == null) {
            recreateClient();
        }
        if (healthConnectClient == null) {
            CompletableFuture<Long> failed = new CompletableFuture<>();
            failed.completeExceptionally(new IllegalStateException("Health Connect client unavailable"));
            return failed;
        }
        return readStepsOnce(day, db)
                .thenApply(CompletableFuture::completedFuture)
                .exceptionally(e -> {
                    Throwable cause = (e.getCause() != null) ? e.getCause() : e;
                    if (attempt < HC_MAX_READ_RETRIES) {
                        Log.w(TAG, "HC read attempt " + (attempt + 1) + " failed ["
                                + cause.getClass().getSimpleName() + ": " + cause.getMessage()
                                + "], recreating client and retrying with backoff.");
                        // A provider restart/update takes a moment; back off before recreating and
                        // retrying so the retry can actually re-bind, instead of firing every attempt
                        // within milliseconds while the provider is still coming back up.
                        CompletableFuture<Long> delayed = new CompletableFuture<>();
                        boolean posted = new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                            recreateClient();
                            readAndPersistStepsData(day, db, attempt + 1).whenComplete((r, ex2) -> {
                                if (ex2 != null) delayed.completeExceptionally(ex2);
                                else delayed.complete(r);
                            });
                        }, 800L * (attempt + 1));
                        // if the looper is shutting down the runnable never runs; complete the future so
                        // a blocking .get() (backfill) can't be stranded forever.
                        if (!posted) {
                            delayed.completeExceptionally(
                                    new IllegalStateException("Could not schedule HC read retry"));
                        }
                        return delayed;
                    }
                    Log.e(TAG, "HC read failed after " + (HC_MAX_READ_RETRIES + 1) + " attempts", e);
                    CompletableFuture<Long> failed = new CompletableFuture<>();
                    failed.completeExceptionally(e);
                    return failed;
                })
                .thenCompose(f -> f);
    }

    private CompletableFuture<Long> readStepsOnce(ZonedDateTime day, StepsDBHelper db) {
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
            // The raw records also tell us how many steps were typed in by hand; those are
            // taken out of the reward total (see HcRewardSteps).
            CompletableFuture<Long> totalFuture = aggregateSteps(timeRangeFilter);
            CompletableFuture<List<StepsRecord>> recordsFuture = readAllStepRecords(timeRangeFilter);

            // When manual entries exist, a second aggregate limited to the apps that wrote
            // automatic records gives the de-duplicated total without manual-only apps; any
            // failure here propagates to the retry path (never fall back to counting manual steps).
            return totalFuture.thenCompose(total -> recordsFuture.thenCompose(records -> {
                Map<String, Long> auto = nonManualStepsByOrigin(records);
                Map<String, Long> manual = manualStepsByOrigin(records);
                CompletableFuture<Long> filteredFuture = HcRewardSteps.needsFilteredAggregate(auto, manual)
                        ? aggregateSteps(timeRangeFilter, HcRewardSteps.filterOrigins(auto))
                        : CompletableFuture.completedFuture(0L);
                return filteredFuture.thenApply(filtered ->
                        persistDay(dateStr, db, total, records, auto, manual, filtered));
            }));
        });
    }

    /** Writes the day's 15-min slots and reward total; returns the reward total. */
    private long persistDay(String dateStr, StepsDBHelper db, Long total, List<StepsRecord> records,
                            Map<String, Long> stepsByOrigin, Map<String, Long> manualByOrigin,
                            long filteredAggregate) {
        // Pick the primary (highest-priority) data origin: the non-manual source that
        // contributed the most steps for the day. Using a single origin for the per-slot
        // breakdown de-duplicates the case where two apps mirror the same steps (which is
        // what previously doubled the raw sum), while still reflecting real per-slot timing.
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

        long aggregateSteps = (total != null) ? total : 0L;
        long totalSteps = HcRewardSteps.rewardSteps(aggregateSteps, stepsByOrigin,
                manualByOrigin, filteredAggregate);
        long excluded = aggregateSteps - totalSteps;
        if (excluded > 0) {
            Log.d(TAG, "Excluded " + excluded + " manually entered steps from the reward count for "
                    + dateStr + " (aggregate " + aggregateSteps + ", filtered " + filteredAggregate
                    + ", manual by app " + manualByOrigin + ")");
        }
        flagManualStepsExcluded(excluded);
        if (records.isEmpty()) {
            Log.w(TAG, "No Health Connect step data found for " + dateStr
                    + " — source app (e.g. Samsung Health) may not be writing steps to Health Connect.");
        }
        db.upsertHCSummary(dateStr, (int) totalSteps);
        Log.d(TAG, "Persisted reward total " + totalSteps + " for " + dateStr
                + "; per-slot detail from primary origin " + primaryOrigin
                + " across " + slotCounts.size() + " slots");
        return totalSteps;
    }

    private CompletableFuture<Long> aggregateSteps(TimeRangeFilter timeRangeFilter) {
        return aggregateSteps(timeRangeFilter, Collections.emptySet());
    }

    /** COUNT_TOTAL over the range; a non-empty origins set limits it to those apps. */
    private CompletableFuture<Long> aggregateSteps(TimeRangeFilter timeRangeFilter, Set<String> origins) {
        Set<AggregateMetric<?>> metrics = new HashSet<>();
        metrics.add(StepsRecord.COUNT_TOTAL);
        Set<DataOrigin> originFilter = new HashSet<>();
        for (String pkg : origins) originFilter.add(new DataOrigin(pkg));
        AggregateRequest request = new AggregateRequest(metrics, timeRangeFilter, originFilter);
        return FutureKt.future(coroutineScope, Dispatchers.getDefault(), CoroutineStart.DEFAULT,
                (scope, continuation) -> {
                    try {
                        return healthConnectClient.aggregate(request, continuation);
                    } catch (Throwable e) {
                        throw new RuntimeException(e);
                    }
                }
        ).thenApply(response -> {
            Long total = ((AggregationResult) response).get(StepsRecord.COUNT_TOTAL);
            return (total != null) ? total : 0L;
        });
    }

    private static final int HC_RECORDS_PAGE_SIZE = 1000;
    // safety stop: 50 pages x 1000 records is far beyond any real day of step data
    private static final int HC_RECORDS_MAX_PAGES = 50;

    /** Reads every StepsRecord in the range, following pageToken until the last page. */
    private CompletableFuture<List<StepsRecord>> readAllStepRecords(TimeRangeFilter timeRangeFilter) {
        return readStepRecordsPage(timeRangeFilter, null, new ArrayList<>(), 0);
    }

    private CompletableFuture<List<StepsRecord>> readStepRecordsPage(TimeRangeFilter timeRangeFilter,
            String pageToken, List<StepsRecord> acc, int page) {
        ReadRecordsRequest<StepsRecord> request = new ReadRecordsRequest<>(
                JvmClassMappingKt.getKotlinClass(StepsRecord.class),
                timeRangeFilter,
                Collections.emptySet(),
                true,
                HC_RECORDS_PAGE_SIZE,
                pageToken
        );
        return FutureKt.future(coroutineScope, Dispatchers.getDefault(), CoroutineStart.DEFAULT,
                (scope, continuation) -> {
                    try {
                        return healthConnectClient.readRecords(request, continuation);
                    } catch (Throwable e) {
                        throw new RuntimeException(e);
                    }
                }
        ).thenCompose(response -> {
            @SuppressWarnings("unchecked")
            ReadRecordsResponse<StepsRecord> res = (ReadRecordsResponse<StepsRecord>) response;
            acc.addAll(res.getRecords());
            String next = res.getPageToken();
            boolean more = next != null && !next.isEmpty();
            if (!more) {
                return CompletableFuture.completedFuture(acc);
            }
            if (page + 1 >= HC_RECORDS_MAX_PAGES) {
                // fail closed: unseen records could hold manual steps, so never count a partial read
                CompletableFuture<List<StepsRecord>> failed = new CompletableFuture<>();
                failed.completeExceptionally(new IllegalStateException(
                        "Too many step records: stopped after " + (page + 1) + " pages"));
                return failed;
            }
            return readStepRecordsPage(timeRangeFilter, next, acc, page + 1);
        });
    }

    private static boolean isManualEntry(StepsRecord record) {
        return record.getMetadata().getRecordingMethod() == Metadata.RECORDING_METHOD_MANUAL_ENTRY;
    }

    /** Steps the user typed in by hand, per data origin (package name). */
    private static Map<String, Long> manualStepsByOrigin(List<StepsRecord> records) {
        Map<String, Long> byOrigin = new HashMap<>();
        for (StepsRecord record : records) {
            if (!isManualEntry(record)) continue;
            String origin = record.getMetadata().getDataOrigin().getPackageName();
            byOrigin.merge(origin, record.getCount(), Long::sum);
        }
        return byOrigin;
    }

    /** Non-manual step totals per data origin (package name). */
    private static Map<String, Long> nonManualStepsByOrigin(List<StepsRecord> records) {
        Map<String, Long> stepsByOrigin = new HashMap<>();
        for (StepsRecord record : records) {
            if (isManualEntry(record)) continue;
            String origin = record.getMetadata().getDataOrigin().getPackageName();
            stepsByOrigin.merge(origin, record.getCount(), Long::sum);
        }
        return stepsByOrigin;
    }

    // Prefs flags for the one-time "manually entered steps no longer count" note.
    static final String PREF_MANUAL_NOTE_PENDING = "hcManualStepsNotePending";
    static final String PREF_MANUAL_NOTE_SHOWN = "hcManualStepsNoteShown";

    private void flagManualStepsExcluded(long excluded) {
        if (excluded <= 0) return;
        try {
            android.content.SharedPreferences prefs =
                    context.getSharedPreferences("actifitSets", Context.MODE_PRIVATE);
            if (HcRewardSteps.shouldFlagNote(excluded, prefs.getBoolean(PREF_MANUAL_NOTE_SHOWN, false),
                    prefs.getBoolean(PREF_MANUAL_NOTE_PENDING, false))) {
                prefs.edit().putBoolean(PREF_MANUAL_NOTE_PENDING, true).apply();
            }
        } catch (Exception e) {
            Log.w(TAG, "Could not flag manual steps note: " + e.getMessage());
        }
    }

    /**
     * Shows, once ever, a short note that manually entered Health Connect steps no longer count
     * toward rewards. Does nothing until a sync has actually left manual steps out.
     * Must be called on the UI thread.
     */
    public static void maybeShowManualStepsNote(android.app.Activity activity) {
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) return;
        android.content.SharedPreferences prefs =
                activity.getSharedPreferences("actifitSets", Context.MODE_PRIVATE);
        if (!HcRewardSteps.shouldShowNote(prefs.getBoolean(PREF_MANUAL_NOTE_PENDING, false),
                prefs.getBoolean(PREF_MANUAL_NOTE_SHOWN, false))) {
            return;
        }
        prefs.edit().putBoolean(PREF_MANUAL_NOTE_SHOWN, true)
                .putBoolean(PREF_MANUAL_NOTE_PENDING, false).apply();
        try {
            new androidx.appcompat.app.AlertDialog.Builder(activity)
                    .setTitle(R.string.hc_manual_steps_note_title)
                    .setMessage(R.string.hc_manual_steps_note_body)
                    .setPositiveButton(android.R.string.ok, null)
                    .show();
        } catch (Exception e) {
            Log.w(TAG, "Could not show manual steps note: " + e.getMessage());
        }
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
