package io.actifit.fitnesstracker.actifitfitnesstracker;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.util.Log;
import android.util.SizeF;
import android.view.View;
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
import java.util.concurrent.ExecutorService;
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
 *  - mode / goal / login / companion / unit changes: also through the watched prefs;
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
            "fitbitSyncCount", "fitbitLastSyncDate", "fitbitLastSyncTime",
            // distance / calories for the rings, the companion, the distance unit
            "hcMetricsDate", "hcTodayDistanceM", "hcTodayKcal",
            "fitbitMetricsDate", "fitbitDistanceM", "fitbitCalories",
            CompanionUtil.PREF_COMPANION, CompanionUtil.PREF_COMPANION_AUTO, "activeSystem"));
    /** Of those, the ones after which past days may differ (sync backfill, other data source). */
    private static final Set<String> STREAK_KEYS = new HashSet<>(Arrays.asList(
            "dataTrackingSystem", "healthConnectLastSyncDate", "healthConnectLastSyncTime",
            "fitbitLastSyncDate", "fitbitLastSyncTime"));

    /** Avatar downloads only (network), kept off EXECUTOR. */
    private static final ExecutorService DOWNLOADER = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "actifit-widget-avatar");
        t.setPriority(Thread.MIN_PRIORITY);
        return t;
    });

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
            Bitmap animal = WidgetAnimal.get(app, s.companion, s.wilting);
            // the cached avatar only: a download never delays this render (see below)
            WidgetAvatar.pruneOtherUsers(app, s.loggedIn ? s.user : null);
            Bitmap avatar = s.loggedIn ? WidgetAvatar.cached(app, s.user) : null;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                // the launcher picks the closest fitting entry; WidgetState.sizeEntry mirrors that rule
                Map<SizeF, RemoteViews> sized = new HashMap<>();
                for (int[] entry : WidgetState.SIZES) {
                    sized.put(new SizeF(entry[0], entry[1]),
                            build(ui, s, animal, avatar, entry[2], entry[3] == 1));
                }
                mgr.updateAppWidget(ids, new RemoteViews(sized));
            } else {
                for (int id : ids) {
                    Bundle opts = mgr.getAppWidgetOptions(id);
                    int w = opts != null ? opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0) : 0;
                    // portrait size: MIN_WIDTH is the portrait width, MAX_HEIGHT the portrait height
                    int h = opts != null ? opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0) : 0;
                    int[] entry = WidgetState.sizeEntry(w, h);
                    mgr.updateAppWidget(id, build(ui, s, animal, avatar, entry[2], entry[3] == 1));
                }
            }

            // avatar: fetched at most once a day (or for a new user) on its own thread, so a slow network
            // never holds up renders or goAsync broadcasts; only the re-render comes back here
            final String user = s.user;
            if (s.loggedIn && WidgetAvatar.needsRefresh(app, user)) {
                DOWNLOADER.execute(() -> {
                    if (WidgetAvatar.refresh(app, user)) EXECUTOR.execute(() -> render(app, -1));
                });
            }
        } catch (Exception e) {
            // never let a widget problem reach the step service or the app
            Log.e(TAG, "widget render failed", e);
        }
    }

    /** What the widget shows, read once per render. */
    private static final class Snapshot {
        String user;
        int steps;
        int goal;
        int streak;
        boolean stale;
        boolean loggedIn;
        long syncTimeMs;
        boolean synced; // Health Connect / Fitbit mode
        float distanceM;
        boolean distanceEstimated;
        float kcal;
        boolean kcalEstimated;
        int companion;
        int level;
        boolean wilting;
    }

    private static Snapshot snapshot(Context app, int liveCount) {
        SharedPreferences sp = app.getSharedPreferences("actifitSets", Context.MODE_PRIVATE);
        String deviceMode = app.getString(R.string.device_tracking_ntt);
        String mode = sp.getString("dataTrackingSystem", deviceMode);
        boolean hc = mode.equals(app.getString(R.string.health_connect_tracking_ntt));
        boolean fitbit = mode.equals(app.getString(R.string.fitbit_tracking_ntt));
        Date now = new Date();
        String today = new SimpleDateFormat("yyyyMMdd", Locale.ENGLISH).format(now);
        // the app writes sync dates with the default locale (the in-app language, e.g. Arabic-Indic
        // digits), and StepsDBHelper.fetchTodayStepCount compares them that way too
        String todayDefault = new SimpleDateFormat("yyyyMMdd").format(now);

        Snapshot s = new Snapshot();
        s.goal = Utils.getDailyStepGoal(app);
        String user = sp.getString("actifitUser", "");
        s.user = user;
        s.loggedIn = !TextUtils.isEmpty(user);
        float realDistanceM = -1f;
        float realKcal = -1f;
        StepsDBHelper db = null;
        if (hc || fitbit) {
            // same rule as StepsDBHelper.fetchTodayStepCount: a sync from another day says nothing about today
            String prefix = hc ? "healthConnect" : "fitbit";
            s.synced = true;
            s.stale = WidgetState.isStale(sp.getString(prefix + "LastSyncDate", ""), todayDefault);
            s.syncTimeMs = sp.getLong(prefix + "LastSyncTime", 0L);
            s.steps = s.stale ? 0 : sp.getInt(prefix + "SyncCount", 0);
            // real distance / calories cached at sync time (TrackingManager / Fitbit sync), today's only
            String metricsDate = sp.getString(hc ? "hcMetricsDate" : "fitbitMetricsDate", "");
            if (!s.stale && WidgetState.isToday(metricsDate, todayDefault, today)) {
                realDistanceM = sp.getFloat(hc ? "hcTodayDistanceM" : "fitbitDistanceM", -1f);
                realKcal = sp.getFloat(hc ? "hcTodayKcal" : "fitbitCalories", -1f);
            }
        } else if (liveCount >= 0) {
            s.steps = liveCount;
        } else {
            db = new StepsDBHelper(app);
            s.steps = db.fetchTodayStepCount();
        }
        s.steps = Math.max(0, s.steps);

        // like MainActivity.renderDashboardRings: real values when known, else step-derived "≈" estimates
        s.distanceEstimated = realDistanceM < 0;
        s.distanceM = s.distanceEstimated ? WidgetState.estimateDistanceMeters(s.steps) : realDistanceM;
        s.kcalEstimated = realKcal < 0;
        s.kcal = s.kcalEstimated ? WidgetState.estimateKcal(s.steps) : realKcal;

        int pastRun = cachedPastRun(app, db, mode, today);
        s.streak = s.stale ? pastRun : WidgetState.streakWithToday(pastRun, s.steps);

        s.companion = CompanionUtil.resolveCompanion(sp, user, true);
        s.level = CompanionUtil.levelFromStreak(s.streak);
        s.wilting = CompanionUtil.isWilting(s.streak, s.steps, Calendar.getInstance().get(Calendar.HOUR_OF_DAY));
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

    /**
     * @param extra compact: show the step count beside the rings; small / medium: show the footer
     */
    private static RemoteViews build(Context ui, Snapshot s, Bitmap animal, Bitmap avatar,
                                     int layout, boolean extra) {
        boolean compact = layout == WidgetState.LAYOUT_COMPACT;
        boolean narrow = layout == WidgetState.LAYOUT_NARROW;
        boolean medium = layout == WidgetState.LAYOUT_MEDIUM;
        int res = compact ? R.layout.widget_steps_compact
                : narrow ? R.layout.widget_steps_narrow
                : medium ? R.layout.widget_steps_medium : R.layout.widget_steps_small;
        RemoteViews v = new RemoteViews(ui.getPackageName(), res);
        Locale locale = ui.getResources().getConfiguration().getLocales().get(0);
        NumberFormat nf = NumberFormat.getIntegerInstance(locale);

        // the dashboard's three rings: steps / goal, distance / 8 km, calories / 500 kcal
        int stepsPct = s.stale ? 0 : WidgetState.progressPercent(s.steps, s.goal);
        v.setProgressBar(R.id.widget_ring_steps, 100, stepsPct, false);
        v.setProgressBar(R.id.widget_ring_distance, 100, WidgetState.ringPercent(s.distanceM, WidgetState.DISTANCE_GOAL_M), false);
        v.setProgressBar(R.id.widget_ring_kcal, 100, WidgetState.ringPercent(s.kcal, WidgetState.KCAL_GOAL), false);
        ColorStateList companionTint = null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // outer ring in the companion's colour, as on the dashboard (static Actifit red before 12)
            boolean night = Utils.isDarkModeActive(ui);
            int color = visibleRingColor(AuraView.auraColor(s.companion, s.level, s.wilting), night);
            companionTint = ColorStateList.valueOf(color);
            // only the progress layer: the track comes from values / values-night, which the launcher
            // resolves itself, so a later light/dark switch never shows a track baked for the other mode
            v.setColorStateList(R.id.widget_ring_steps, "setProgressTintList", companionTint);
        }

        if (animal != null) {
            v.setImageViewBitmap(R.id.widget_animal, animal);
            v.setViewVisibility(R.id.widget_animal, View.VISIBLE);
            v.setViewVisibility(R.id.widget_animal_emoji, View.GONE);
        } else {
            v.setTextViewText(R.id.widget_animal_emoji, AuraView.companionEmoji(s.companion));
            v.setViewVisibility(R.id.widget_animal, View.GONE);
            v.setViewVisibility(R.id.widget_animal_emoji, View.VISIBLE);
        }

        String stepsText = s.stale ? "–" : nf.format(s.steps);
        String stepsLabel = ui.getString(R.string.widget_steps_label);
        v.setTextViewText(R.id.widget_steps, stepsText);
        v.setOnClickPendingIntent(R.id.widget_root, openAppIntent(ui));

        String status;
        if (s.stale) {
            status = ui.getString(R.string.widget_open_to_sync);
        } else if (!s.loggedIn) {
            status = ui.getString(R.string.widget_login_hint);
        } else if (s.synced && s.syncTimeMs > 0) {
            status = ui.getString(R.string.widget_synced_at,
                    android.text.format.DateFormat.getTimeFormat(ui).format(new Date(s.syncTimeMs)));
        } else {
            status = "";
        }

        if (compact) {
            // rings + animal only; the step count beside them when there is room (2x1)
            v.setViewVisibility(R.id.widget_steps, extra ? View.VISIBLE : View.GONE);
            v.setContentDescription(R.id.widget_root, join(stepsText + " " + stepsLabel, status));
            return v;
        }
        if (narrow) {
            // when today's synced steps are unknown the (short) hint takes the count's place
            if (s.stale) v.setTextViewText(R.id.widget_steps, ui.getString(R.string.widget_open_to_sync_short));
            v.setContentDescription(R.id.widget_root, join(stepsText + " " + stepsLabel, status));
            return v;
        }

        if (avatar != null) {
            v.setImageViewBitmap(R.id.widget_avatar, avatar);
            v.setViewVisibility(R.id.widget_avatar, View.VISIBLE);
        } else {
            v.setViewVisibility(R.id.widget_avatar, View.GONE);
        }

        // stats column: one dot row per ring, coloured like it (steps dot = companion colour on 12+)
        if (companionTint != null) {
            v.setColorStateList(R.id.widget_dot_steps, "setImageTintList", companionTint);
        }
        // stale: the percent is unknown like the count, so it shows the same dash
        String stepsRow = ui.getString(R.string.widget_steps_percent, s.stale ? "\u2013" : nf.format(stepsPct));
        String distText = (s.distanceEstimated ? "≈" : "") + Utils.formatDistance(ui, s.distanceM);
        String kcalText = (s.kcalEstimated ? "≈" : "") + Math.round(s.kcal) + " " + ui.getString(R.string.kcal_unit);
        v.setTextViewText(R.id.widget_row_steps, stepsRow);
        v.setTextViewText(R.id.widget_row_distance, distText);
        v.setTextViewText(R.id.widget_row_kcal, kcalText);
        if (medium) {
            v.setTextViewText(R.id.widget_goal, ui.getString(R.string.widget_steps_of_goal,
                    stepsText, nf.format(s.goal)) + " " + stepsLabel);
        }

        // footer: the action the user must take first, otherwise the streak with the sync time. It is
        // the first thing dropped on a short widget, except when the user must act.
        String streakText = "🔥 " + (s.streak > 0
                ? ui.getString(R.string.day_streak_label, s.streak)
                : ui.getString(R.string.no_streak_yet));
        boolean mustAct = s.stale || !s.loggedIn;
        // the footer's height is only budgeted when the size entry has room for it, and not with
        // enlarged system fonts
        boolean footer = extra && ui.getResources().getConfiguration().fontScale <= WidgetState.LARGE_FONT_SCALE;
        String footerText = mustAct ? join(status, streakText) : join(streakText, status);
        v.setTextViewText(R.id.widget_footer, footerText);
        v.setViewVisibility(R.id.widget_footer, footer ? View.VISIBLE : View.GONE);
        // no room for the footer but the user must act: the hint takes the distance + kcal rows' place
        boolean hint = mustAct && !footer;
        v.setTextViewText(R.id.widget_hint, hint ? status : "");
        v.setViewVisibility(R.id.widget_hint, hint ? View.VISIBLE : View.GONE);
        v.setViewVisibility(R.id.widget_row_distance_line, hint ? View.GONE : View.VISIBLE);
        v.setViewVisibility(R.id.widget_row_kcal_line, hint ? View.GONE : View.VISIBLE);

        v.setContentDescription(R.id.widget_root, stepsText + " " + stepsLabel + ", " + stepsRow + ", "
                + distText + ", " + kcalText + ", " + footerText);
        return v;
    }

    private static PendingIntent openAppIntent(Context ui) {
        Intent open = new Intent(ui, MainActivity.class);
        open.setAction("OPEN_MAIN_ACTIVITY");
        return PendingIntent.getActivity(ui, 0, open, PendingIntent.FLAG_IMMUTABLE);
    }

    private static String join(String first, String second) {
        if (first.isEmpty()) return second;
        if (second.isEmpty()) return first;
        return first + " · " + second;
    }

    /** AuraView's dark-mode lift: dark companion colours get a minimum brightness so they stay visible. */
    private static int visibleRingColor(int color, boolean night) {
        if (!night) return color;
        float[] hsv = new float[3];
        Color.colorToHSV(color, hsv);
        if (hsv[2] < 0.72f) hsv[2] = 0.72f;
        return Color.HSVToColor(hsv);
    }
}
