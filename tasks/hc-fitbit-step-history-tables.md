# HC & Fitbit Step History Tables + Full Chart Support

## Background

In Health Connect and Fitbit modes, the hourly/daily bar chart (~178dp) and chart switcher
(~48dp) are hidden with `View.INVISIBLE` instead of `View.GONE`, leaving a large blank dead zone
on the dashboard. Beyond the visual bug, HC and Fitbit users have no step history visualisation
at all, the heatmap shows empty cells, and the Post screen has degraded behaviour in these modes.

Writing to the existing `ActifitFitness` / `DailyActivityRecs` tables would corrupt phone sensor
history (date is the primary key). The solution is three dedicated parallel tables in the same
DB, isolated by source. `ActifitFitness` remains the single source of truth for
`StepHistoryActivity` (written only at post time via the unchanged `manualInsertStepsEntry()`).
The new tables serve charts, the heatmap, and post-screen hourly breakdown.

Health Connect `StepsRecord` objects carry `getStartTime()` / `getEndTime()` per segment, so HC
users get both daily and hourly charts and hourly breakdown in their blockchain posts. Fitbit
provides daily totals only.

---

## Changes

### 1 · StepsDBHelper.java — DB version 5

**New tables**

```sql
CREATE TABLE IF NOT EXISTS HCStepsSummary (
  creationdate  INTEGER PRIMARY KEY,   -- yyyyMMdd
  stepscount    INTEGER,
  trackingdevice TEXT                  -- always "Health Connect"
);

CREATE TABLE IF NOT EXISTS HCStepsDetails (
  dateEntry     INTEGER,               -- yyyyMMdd
  timeSlot      INTEGER,               -- HHmm, bucketed to :00/:15/:30/:45
  activityCount INTEGER
);

CREATE TABLE IF NOT EXISTS FitbitStepsSummary (
  creationdate  INTEGER PRIMARY KEY,   -- yyyyMMdd
  stepscount    INTEGER,
  trackingdevice TEXT                  -- always "Fitbit"
);
```

**Migration** — add `CREATE TABLE IF NOT EXISTS` for all three in a new `if (oldVersion < 5)`
block inside `onUpgrade()`. Existing data untouched.

**New write methods** (upsert — same pattern as `createStepsEntry()`)
- `upsertHCSummary(String yyyyMMdd, int steps)`
- `upsertHCSlot(String yyyyMMdd, int timeSlot, int count)` — reuse `getTimeSlot()` for bucketing
- `upsertFitbitSummary(String yyyyMMdd, int steps)`

**New read methods** (mirror existing)
- `readHCStepsEntries()` → `List<DateStepsModel>` from `HCStepsSummary`
- `fetchHCDateTimeSlotActivity(String date)` → `List<ActivitySlot>` from `HCStepsDetails`
- `readFitbitStepsEntries()` → `List<DateStepsModel>` from `FitbitStepsSummary`

**Make `fetchStepCountByDate()` mode-aware** — currently reads only from `ActifitFitness`;
add a mode check at the top to route to the correct table. This automatically fixes the heatmap
for HC/Fitbit users with no heatmap code changes required.

```java
public int fetchStepCountByDate(String dateString) {
    String mode = sharedPreferences.getString("dataTrackingSystem", device_ntt);
    String table = mode.equals(hc_ntt)     ? "HCStepsSummary"     :
                   mode.equals(fitbit_ntt) ? "FitbitStepsSummary" :
                                             TABLE_STEPS_SUMMARY;
    // existing SQL with table name substituted
}
```

**Unchanged** — `fetchYesterdayStepCount()` and `manualInsertStepsEntry()` are left as-is.
Yesterday defaults to the phone sensor DB (acceptable; users can re-sync for a fresh count).
`manualInsertStepsEntry()` keeps writing to `ActifitFitness` so `StepHistoryActivity` continues
to show submitted reports for all modes.

---

### 2 · HealthConnectManager.java

**Add `readAndPersistStepsData(ZonedDateTime day, StepsDBHelper db)`**

Mirrors the existing `readStepsData()` but additionally:
1. For each `StepsRecord`, extracts `record.getStartTime()` / `record.getEndTime()` (`Instant`),
   converts to local `HHmm` via `LocalDateTime.ofInstant()`, calls `getTimeSlot()` for
   bucketing, and calls `db.upsertHCSlot()`.
2. Calls `db.upsertHCSummary()` with the day's total.
3. Both DB writes dispatched on a **background thread** to keep the UI callback non-blocking.
4. Returns `CompletableFuture<Long>` — existing callers need no signature change.

**Add `backfillHCHistory(StepsDBHelper db, int days)`**

Called once when `HCStepsSummary` is empty (first HC launch / fresh install). Loops
`today − days` through `today − 1`, calling `readAndPersistStepsData()` per day. Runs fully on
a background thread. Default: 30 days.

---

### 3 · PostSteemitActivity.java

**Fitbit sync persistence** (lines 1145–1150)
After writing to SharedPreferences, dispatch to background:
```java
new Thread(() -> mStepsDBHelper.upsertFitbitSummary(fitbitDate, fitbitCount)).start();
```

**HC sync** (line 2075 — `checkHealthConnectAndFetchSteps()`)
Replace `readStepsData()` call with `readAndPersistStepsData(day, mStepsDBHelper)`.

**Hourly breakdown for HC posts** (`PostSteemitRequest.doInBackground()`, line ~1446)
Current guard skips slot data for any external sync. Replace with mode-specific branches:
```java
if (fitbitSyncDone == 0 && healthConnectSyncDone == 0) {
    // Device mode: existing fetchDateTimeSlotActivity() path — unchanged
} else if (healthConnectSyncDone == 1) {
    // HC mode: hourly data now available from HCStepsDetails
    List<ActivitySlot> slots = mStepsDBHelper.fetchHCDateTimeSlotActivity(targetDate);
    // build stepDataString exactly as device mode does
}
// Fitbit: no slot data — detailedActivity remains empty string (unchanged)
```
HC posts will now include a `detailedActivity` hourly breakdown on the blockchain.

---

### 4 · TrackingManager.java

**HC sync hookpoint** (`checkPermissionsAndReadData()`, line 248)
Replace `readStepsData()` with `readAndPersistStepsData(day, mStepsDBHelper)`.

**`useDefaultTrackingMethod()`** — split the current `else` block (device + HC lumped together)
into proper mode-specific branches:

*Health Connect branch:*
```java
barChartContainer.setVisibility(View.VISIBLE);
chartSwitcher.setVisibility(View.VISIBLE);   // hourly IS available for HC
new DisplayHCHistoryChartAsyncTask(true).execute();
new DisplayHCDayChartAsyncTask(true).execute();
if (mStepsDBHelper.readHCStepsEntries().isEmpty())
    healthConnectManager.backfillHCHistory(mStepsDBHelper, 30);
```

*Fitbit branch:*
```java
barChartContainer.setVisibility(View.VISIBLE);
// chartSwitcher stays GONE — no intra-day Fitbit data
new DisplayFitbitHistoryChartAsyncTask(true).execute();
```

---

### 5 · ChartManager.java

**Fix `hideCharts()`** (lines 402–404)
Change both `View.INVISIBLE` → `View.GONE` on `chartSwitcher` and `bar_chart_container`.

**Extract shared rendering helpers**
- `renderDailyBars(BarChart chart, List<DateStepsModel> data, boolean animate)`
- `renderHourlyBars(BarChart chart, List<ActivitySlot> data, boolean animate)`

**New display methods**
- `displayChartDataHC(boolean animate)` — `readHCStepsEntries()` → `renderDailyBars()`
- `displayDayChartDataHC(boolean animate)` — `fetchHCDateTimeSlotActivity(today)` → `renderHourlyBars()`
- `displayChartDataFitbit(boolean animate)` — `readFitbitStepsEntries()` → `renderDailyBars()`

---

### 6 · MainActivity.java

Three new inner async tasks following the exact `DisplayChartDataAsyncTask` pattern:
- `DisplayHCHistoryChartAsyncTask` → `chartManager.displayChartDataHC(animate)`
- `DisplayHCDayChartAsyncTask` → `chartManager.displayDayChartDataHC(animate)`
- `DisplayFitbitHistoryChartAsyncTask` → `chartManager.displayChartDataFitbit(animate)`

---

## Files Modified

| File | Changes |
|------|---------|
| `StepsDBHelper.java` | DB v5; 3 tables + migration; 3 write + 3 read methods; `fetchStepCountByDate()` mode-aware |
| `HealthConnectManager.java` | `readAndPersistStepsData()`; `backfillHCHistory()` |
| `PostSteemitActivity.java` | Fitbit upsert on sync; HC → `readAndPersistStepsData()`; HC hourly breakdown in post |
| `TrackingManager.java` | HC/Fitbit branches show chart + trigger async tasks; HC sync hookpoint |
| `ChartManager.java` | `hideCharts()` fix; shared helpers; 3 new display methods |
| `MainActivity.java` | 3 new async tasks |

---

## Verification

1. **Phone Sensors** — charts, switcher, heatmap, post screen, StepHistoryActivity all unchanged
2. **HC — dashboard** — sync populates `HCStepsSummary` + `HCStepsDetails`; daily + hourly charts appear; heatmap shows HC history; `chart_switcher` visible
3. **HC — post screen** — today's count correct; `detailedActivity` hourly breakdown included in blockchain post; `manualInsertStepsEntry()` on submit still writes to `ActifitFitness` so StepHistoryActivity shows the posted report
4. **Fitbit — dashboard** — sync populates `FitbitStepsSummary`; daily chart appears; no switcher shown; heatmap shows Fitbit history
5. **Fitbit — post screen** — today's count correct; no hourly breakdown (unchanged); `manualInsertStepsEntry()` on submit still writes to `ActifitFitness`
6. **StepHistoryActivity** — reads `ActifitFitness` as before; shows posted reports for all modes; unaffected
7. **Mode switch Phone → HC → Phone** — `ActifitFitness` / `DailyActivityRecs` fully intact
8. **DB upgrade** — v4 users get tables created cleanly in `onUpgrade()`; no data loss
9. **First HC launch** — `backfillHCHistory()` populates 30 days from HC API; subsequent starts load from local DB instantly
10. **Threading** — all new DB writes dispatched off the UI thread; no ANR risk