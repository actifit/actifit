package io.actifit.fitnesstracker.actifitfitnesstracker;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.util.Log;
import android.util.SizeF;
import android.widget.RemoteViews;

import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Renders {@link StepsWidgetProvider}. Every render runs on one background thread, so callers on the
 * main thread or the step sensor thread never block on the DB or on AppWidgetManager.
 *
 * Refresh sources:
 *  - device-sensor mode: {@link #onSteps} from ActivityMonitorService, throttled (see WidgetState.shouldPush);
 *  - Health Connect / Fitbit: only when the app syncs (the sync writes the prefs watched by {@link #watchAppPrefs});
 *  - mode / goal / login changes: also through the watched prefs;
 *  - the provider's 30 min updatePeriodMillis (midnight rollover, "Open Actifit to sync" labels).
 */
final class WidgetUpdater {

    private static final String TAG = "ActifitWidget";

    private static final String PREFS = "actifitWidget";
    private static final String KEY_ACTIVE = "widgetActive";
    private static final String KEY_STREAK_DATE = "streakDate";
    private static final String KEY_STREAK_MODE = "streakMode";
    private static final String KEY_STREAK_PAST = "streakPast";

    /** Past days read for the streak; with today that is the dashboard's 31-day window. */
    private static final int STREAK_DAYS_BACK = 30;

    /** App prefs ("actifitSets") whose change should refresh the widget. */
    private static final Set<String> WATCHED_KEYS = new HashSet<>(Arrays.asList(
            "dataTrackingSystem", "dailyStepGoal", "actifitUser",
            "healthConnectSyncCount", "healthConnectLastSyncDate", "healthConnectLastSyncTime",
            "fitbitSyncCount", "fitbitLastSyncDate", "fitbitLastSyncTime"));
    /** Of those, the ones after which past days may differ (sync backfill, other data source). */
    private static final Set<String> STREAK_KEYS = new HashSet<>(Arrays.asList(
            "dataTrackingSystem", "healthConnectLastSyncDate", "healthConnectLastSyncTime",
            "fitbitLastSyncDate", "fitbitLastSyncTime"));

    /** LocaleManager's in-app language key (default shared prefs). */
    private static final String LANGUAGE_KEY = "language_key";

    private static final ScheduledExecutorService EXECUTOR = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "actifit-widget");
        t.setPriority(Thread.NORM_PRIORITY - 1);
        return t;
    });

    private static volatile Boolean active;

    private static final AtomicBoolean prefsRenderQueued = new AtomicBoolean();
    private static final AtomicBoolean streakResetPending = new AtomicBoolean();

    // live-push throttle state, guarded by WidgetUpdater.class
    private static long lastPushMs;
    private static int lastPushCount;
    private static int lastPushDay;
    // latest count seen while throttled, and whether a trailing render is already scheduled for it
    private static int latestCount;
    private static boolean trailingScheduled;

    // SharedPreferences keeps listeners weakly: hold strong references for the process lifetime
    @SuppressWarnings("FieldCanBeLocal")
    private static SharedPreferences.OnSharedPreferenceChangeListener prefsListener;
    @SuppressWarnings("FieldCanBeLocal")
    private static SharedPreferences.OnSharedPreferenceChangeListener languageListener;

    private WidgetUpdater() {
    }

    // ---------------------------------------------------------------- entry points

    /** Called once from ActifitApplication: refresh when sync results, mode, goal or login change. */
    static void watchAppPrefs(Context context) {
        final Context app = context.getApplicationContext();
        // load the widget prefs file here, so the main-thread listener and onEnabled never wait on it
        EXECUTOR.execute(() -> isActive(app));
        prefsListener = (prefs, key) -> {
            // key is null when the prefs were cleared (API 30+)
            if (key != null && !WATCHED_KEYS.contains(key)) return;
            if (!isActive(app)) return;
            if (key == null || STREAK_KEYS.contains(key)) streakResetPending.set(true);
            // one sync writes several watched keys in one apply(): render once for all of them
            if (!prefsRenderQueued.compareAndSet(false, true)) return;
            EXECUTOR.execute(() -> {
                prefsRenderQueued.set(false);
                if (streakResetPending.getAndSet(false)) {
                    app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY_STREAK_DATE).apply();
                }
                render(app, -1);
            });
        };
        app.getSharedPreferences("actifitSets", Context.MODE_PRIVATE)
                .registerOnSharedPreferenceChangeListener(prefsListener);
        // the in-app language lives in the default prefs (LocaleManager)
        languageListener = (prefs, key) -> {
            if (LANGUAGE_KEY.equals(key) && isActive(app)) requestUpdate(app);
        };
        PreferenceManager.getDefaultSharedPreferences(app)
                .registerOnSharedPreferenceChangeListener(languageListener);
    }

    static void requestUpdate(Context context) {
        requestUpdate(context, null);
    }

    /** Re-render every placed widget; {@code pending} (from goAsync) is finished when done. */
    static void requestUpdate(Context context, BroadcastReceiver.PendingResult pending) {
        final Context app = context.getApplicationContext();
        EXECUTOR.execute(() -> {
            try {
                render(app, -1);
            } finally {
                if (pending != null) pending.finish();
            }
        });
    }

    /**
     * Live count from the step service (device-sensor mode), called on its sensor thread for every
     * step. Cheap no-op unless a widget is placed; pushes at most once per minute, or right away when
     * the count crosses the goal or 5,000 steps.
     */
    static void onSteps(Context context, int count) {
        if (!isActive(context)) return;
        Calendar now = Calendar.getInstance();
        int day = now.get(Calendar.YEAR) * 1000 + now.get(Calendar.DAY_OF_YEAR);
        long nowMs = now.getTimeInMillis();
        final Context app = context.getApplicationContext();
        synchronized (WidgetUpdater.class) {
            latestCount = count;
            if (!WidgetState.shouldPush(nowMs, lastPushMs, lastPushCount, count,
                    Utils.getDailyStepGoal(context), day != lastPushDay)) {
                // throttled: make sure the last steps before the user stops walking still show up
                if (count != lastPushCount && !trailingScheduled) {
                    trailingScheduled = true;
                    long delay = Math.max(0L, lastPushMs + WidgetState.PUSH_INTERVAL_MS - nowMs);
                    EXECUTOR.schedule(() -> trailingPush(app), delay, TimeUnit.MILLISECONDS);
                }
                return;
            }
            lastPushMs = nowMs;
            lastPushCount = count;
            lastPushDay = day;
        }
        EXECUTOR.execute(() -> render(app, count));
    }

    /** The one delayed render queued by a throttled {@link #onSteps}; shows the latest count. */
    private static void trailingPush(Context app) {
        int count;
        synchronized (WidgetUpdater.class) {
            trailingScheduled = false;
            if (latestCount == lastPushCount) return; // a regular push already showed it
            count = latestCount;
            Calendar now = Calendar.getInstance();
            lastPushMs = now.getTimeInMillis();
            lastPushCount = count;
            lastPushDay = now.get(Calendar.YEAR) * 1000 + now.get(Calendar.DAY_OF_YEAR);
        }
        render(app, count);
    }

    static void setActive(Context context, boolean isActive) {
        active = isActive;
        SharedPreferences.Editor ed = context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_ACTIVE, isActive);
        // (re)added widget: a streak cached before it was removed may be out of date
        if (isActive) ed.remove(KEY_STREAK_DATE);
        ed.apply();
    }

    private static boolean isActive(Context context) {
        Boolean a = active;
        if (a == null) {
            a = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .getBoolean(KEY_ACTIVE, false);
            active = a;
        }
        return a;
    }

    // ---------------------------------------------------------------- rendering (widget thread only)

    private static void render(Context app, int liveCount) {
        try {
            AppWidgetManager mgr = AppWidgetManager.getInstance(app);
            if (mgr == null) return;
            int[] ids = mgr.getAppWidgetIds(new ComponentName(app, StepsWidgetProvider.class));
            if (ids == null || ids.length == 0) {
                if (isActive(app)) setActive(app, false);
                return;
            }
            if (!isActive(app)) setActive(app, true);

            // strings in the current in-app language (the app context keeps the one from process start)
            Context ui = LocaleManager.setLocale(app);
            Snapshot s = snapshot(app, liveCount);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                Map<SizeF, RemoteViews> sized = new HashMap<>();
                sized.put(new SizeF(110f, 110f), build(ui, s, false));
                sized.put(new SizeF(WidgetState.MEDIUM_MIN_WIDTH_DP, 110f), build(ui, s, true));
                mgr.updateAppWidget(ids, new RemoteViews(sized));
            } else {
                for (int id : ids) {
                    Bundle opts = mgr.getAppWidgetOptions(id);
                    int minWidth = opts != null ? opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0) : 0;
                    mgr.updateAppWidget(id, build(ui, s, WidgetState.useMediumLayout(minWidth)));
                }
            }
        } catch (Exception e) {
            // never let a widget problem reach the step service or the app
            Log.e(TAG, "widget render failed", e);
        }
    }

    /** What the widget shows, read once per render. */
    private static final class Snapshot {
        int steps;
        int goal;
        int streak;
        boolean stale;
        boolean loggedIn;
        long syncTimeMs;
        boolean synced; // Health Connect / Fitbit mode
    }

    private static Snapshot snapshot(Context app, int liveCount) {
        SharedPreferences sp = app.getSharedPreferences("actifitSets", Context.MODE_PRIVATE);
        String deviceMode = app.getString(R.string.device_tracking_ntt);
        String mode = sp.getString("dataTrackingSystem", deviceMode);
        boolean hc = mode.equals(app.getString(R.string.health_connect_tracking_ntt));
        boolean fitbit = mode.equals(app.getString(R.string.fitbit_tracking_ntt));
        String today = new SimpleDateFormat("yyyyMMdd", Locale.ENGLISH).format(new Date());

        Snapshot s = new Snapshot();
        s.goal = Utils.getDailyStepGoal(app);
        s.loggedIn = !TextUtils.isEmpty(sp.getString("actifitUser", ""));
        StepsDBHelper db = null;
        if (hc || fitbit) {
            // same rule as StepsDBHelper.fetchTodayStepCount: a sync from another day says nothing about today
            String prefix = hc ? "healthConnect" : "fitbit";
            s.synced = true;
            // the sync date is written with the default locale (the in-app language, e.g. Arabic-Indic
            // digits), and StepsDBHelper.fetchTodayStepCount compares it that way too
            String syncToday = new SimpleDateFormat("yyyyMMdd").format(new Date());
            s.stale = WidgetState.isStale(sp.getString(prefix + "LastSyncDate", ""), syncToday);
            s.syncTimeMs = sp.getLong(prefix + "LastSyncTime", 0L);
            s.steps = s.stale ? 0 : sp.getInt(prefix + "SyncCount", 0);
        } else if (liveCount >= 0) {
            s.steps = liveCount;
        } else {
            db = new StepsDBHelper(app);
            s.steps = db.fetchTodayStepCount();
        }
        s.steps = Math.max(0, s.steps);

        int pastRun = cachedPastRun(app, db, mode, today);
        s.streak = s.stale ? pastRun : WidgetState.streakWithToday(pastRun, s.steps);
        return s;
    }

    /** Streak of the days before today; read from the DB at most once a day per tracking mode. */
    private static int cachedPastRun(Context app, StepsDBHelper db, String mode, String today) {
        SharedPreferences wp = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (today.equals(wp.getString(KEY_STREAK_DATE, "")) && mode.equals(wp.getString(KEY_STREAK_MODE, ""))) {
            return wp.getInt(KEY_STREAK_PAST, 0);
        }
        if (db == null) db = new StepsDBHelper(app);
        SimpleDateFormat fmt = new SimpleDateFormat("yyyyMMdd", Locale.ENGLISH);
        int[] past = new int[STREAK_DAYS_BACK];
        Arrays.fill(past, -1);
        for (int i = 0; i < past.length; i++) {
            Calendar day = Calendar.getInstance();
            day.add(Calendar.DATE, -(i + 1));
            past[i] = db.fetchStepCountByDate(fmt.format(day.getTime()));
            if (past[i] < WidgetState.STREAK_THRESHOLD) break; // the run ends here, no need to read further
        }
        int run = WidgetState.leadingRun(past);
        wp.edit().putString(KEY_STREAK_DATE, today).putString(KEY_STREAK_MODE, mode)
                .putInt(KEY_STREAK_PAST, run).apply();
        return run;
    }

    private static RemoteViews build(Context app, Snapshot s, boolean medium) {
        RemoteViews v = new RemoteViews(app.getPackageName(),
                medium ? R.layout.widget_steps_medium : R.layout.widget_steps_small);
        Locale locale = app.getResources().getConfiguration().getLocales().get(0);
        NumberFormat nf = NumberFormat.getIntegerInstance(locale);

        String stepsText = s.stale ? "–" : nf.format(s.steps);
        v.setProgressBar(R.id.widget_ring, 100, s.stale ? 0 : WidgetState.progressPercent(s.steps, s.goal), false);
        v.setTextViewText(R.id.widget_steps, stepsText);
        v.setTextViewText(R.id.widget_steps_label, app.getString(R.string.widget_steps_label));

        String status;
        if (s.stale) {
            status = app.getString(R.string.widget_open_to_sync);
        } else if (!s.loggedIn) {
            status = app.getString(R.string.widget_login_hint);
        } else if (s.synced && s.syncTimeMs > 0) {
            status = app.getString(R.string.widget_synced_at,
                    android.text.format.DateFormat.getTimeFormat(app).format(new Date(s.syncTimeMs)));
        } else {
            status = "";
        }
        v.setTextViewText(R.id.widget_status, status);
        v.setViewVisibility(R.id.widget_status, status.isEmpty() ? android.view.View.GONE : android.view.View.VISIBLE);

        String streakText = s.streak > 0
                ? app.getString(R.string.day_streak_label, s.streak)
                : app.getString(R.string.no_streak_yet);
        if (medium) {
            v.setTextViewText(R.id.widget_goal, app.getString(R.string.widget_steps_of_goal,
                    stepsText, nf.format(s.goal)));
            v.setTextViewText(R.id.widget_streak, streakText);
        }

        StringBuilder cd = new StringBuilder(stepsText).append(' ')
                .append(app.getString(R.string.widget_steps_label));
        if (medium) cd.append(", ").append(streakText);
        if (!status.isEmpty()) cd.append(", ").append(status);
        v.setContentDescription(R.id.widget_root, cd.toString());

        Intent open = new Intent(app, MainActivity.class);
        open.setAction("OPEN_MAIN_ACTIVITY");
        PendingIntent pi = PendingIntent.getActivity(app, 0, open,
                PendingIntent.FLAG_IMMUTABLE);
        v.setOnClickPendingIntent(R.id.widget_root, pi);
        return v;
    }
}
