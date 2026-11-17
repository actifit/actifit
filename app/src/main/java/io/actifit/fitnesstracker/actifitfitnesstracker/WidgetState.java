package io.actifit.fitnesstracker.actifitfitnesstracker;

/**
 * Pure decision logic for the home-screen steps widget (no Android imports, unit tested).
 */
final class WidgetState {

    /** A day counts towards the streak at this many steps (same threshold as the dashboard). */
    static final int STREAK_THRESHOLD = 5000;

    /** Device-sensor pushes are throttled to at most one per this interval. */
    static final long PUSH_INTERVAL_MS = 60_000L;

    // Layouts, smallest first. The size thresholds below are the dp each layout needs, budgeted for
    // the largest padding in use (16dp: Android 12+'s system_app_widget_internal_padding).
    static final int LAYOUT_COMPACT = 0; // rings + animal (+ steps when there is room): 1x1, 2x1
    static final int LAYOUT_NARROW = 1;  // rings over the step count: short or narrow 2x2
    static final int LAYOUT_SMALL = 2;   // 2x2: header, rings + stats column, footer
    static final int LAYOUT_MEDIUM = 3;  // wide: as small, with bigger rings and "x / goal"

    /** Smallest supported size (dp): compact 44dp rings + 8dp padding each side. */
    static final int COMPACT_MIN_DP = 60;
    /** The compact layout shows the step count from this width (dp), e.g. 2x1. */
    static final int COMPACT_STEPS_MIN_WIDTH_DP = 110;
    /** Narrower or shorter than this the compact layout is used (narrow: 56dp rings + steps + 8dp padding). */
    static final int NARROW_MIN_DP = 96;
    /** Small: 16 + 64dp rings + 10 + stats column + 16 wide; 16 + 20dp header + ~73dp stats column + 16 tall (+18 footer). */
    static final int SMALL_MIN_WIDTH_DP = 160;
    static final int SMALL_MIN_HEIGHT_DP = 126;
    static final int SMALL_FOOTER_MIN_HEIGHT_DP = 144;
    /** Medium: 72dp rings; the stats column (~89dp with the "x / goal" line) sets the height (+18 footer). */
    static final int MEDIUM_MIN_WIDTH_DP = 230;
    static final int MEDIUM_MIN_HEIGHT_DP = 142;
    static final int MEDIUM_FOOTER_MIN_HEIGHT_DP = 160;

    private WidgetState() {
    }

    // dashboard activity rings (MainActivity.renderDashboardRings): distance/8 km, calories/500 kcal,
    // step-derived estimates when there is no real source
    static final float DISTANCE_GOAL_M = 8000f;
    static final float KCAL_GOAL = 500f;
    static final float STRIDE_M = 0.762f;
    static final float KCAL_PER_STEP = 0.04f;

    /** Step-derived distance estimate in metres. */
    static float estimateDistanceMeters(int steps) {
        return Math.max(0, steps) * STRIDE_M;
    }

    /** Step-derived calorie estimate. */
    static float estimateKcal(int steps) {
        return Math.max(0, steps) * KCAL_PER_STEP;
    }

    /** Ring progress 0..100 for {@code value} towards {@code goal}. */
    static int ringPercent(float value, float goal) {
        if (!(value > 0f)) return 0; // also NaN
        if (goal <= 0f) return 100;
        return (int) Math.min(100f, value * 100f / goal);
    }

    /**
     * A cached synced value is today's when its stored date equals today in either format the app
     * writes (default-locale digits, or English digits).
     */
    static boolean isToday(String storedDate, String todayDefaultLocale, String todayEnglish) {
        return !isStale(storedDate, todayDefaultLocale) || !isStale(storedDate, todayEnglish);
    }

    /** Ring progress 0..100 for {@code steps} towards {@code goal}; never negative, never above 100. */
    static int progressPercent(int steps, int goal) {
        if (steps <= 0) return 0;
        if (goal <= 0) return 100;
        long pct = (long) steps * 100L / goal;
        return (int) Math.min(100L, pct);
    }

    /**
     * Consecutive days at or above the threshold starting at index 0 of {@code days}
     * (index 0 = the most recent day). An unknown day (-1) or a day under the threshold ends the run.
     */
    static int leadingRun(int[] days) {
        if (days == null) return 0;
        int run = 0;
        for (int d : days) {
            if (d >= STREAK_THRESHOLD) run++;
            else break;
        }
        return run;
    }

    /**
     * The dashboard's streak: {@code stepsBack[0]} is today, {@code stepsBack[i]} is i days ago.
     * Today counts only once it reaches the threshold; otherwise it is skipped (day in progress).
     */
    static int streak(int[] stepsBack) {
        if (stepsBack == null || stepsBack.length == 0) return 0;
        int start = stepsBack[0] >= STREAK_THRESHOLD ? 0 : 1;
        int run = 0;
        for (int i = start; i < stepsBack.length; i++) {
            if (stepsBack[i] >= STREAK_THRESHOLD) run++;
            else break;
        }
        return run;
    }

    /** Streak from a cached past-days run (ending yesterday) plus today's live count. */
    static int streakWithToday(int pastRun, int todaySteps) {
        int past = Math.max(0, pastRun);
        return todaySteps >= STREAK_THRESHOLD ? past + 1 : past;
    }

    /**
     * Synced modes (Health Connect / Fitbit) only know today's steps once the app has synced today.
     * @param lastSyncDate yyyyMMdd of the last sync ("" or null when never synced)
     * @param today        yyyyMMdd of today
     */
    static boolean isStale(String lastSyncDate, String today) {
        return lastSyncDate == null || today == null || !today.equals(lastSyncDate);
    }

    /**
     * Whether a live step count should be pushed to the widget now.
     *
     * @param nowMs     current time
     * @param lastMs    time of the previous push (0 = never pushed in this process)
     * @param lastCount count shown by the previous push
     * @param newCount  current count
     * @param goal      daily step goal
     * @param dayChanged true when the previous push was for another day
     */
    static boolean shouldPush(long nowMs, long lastMs, int lastCount, int newCount, int goal, boolean dayChanged) {
        if (lastMs <= 0 || dayChanged) return true;
        if (newCount == lastCount) return false;
        // a reset (new day in the DB, or a correction) is shown right away
        if (newCount < lastCount) return true;
        if (crossed(lastCount, newCount, goal) || crossed(lastCount, newCount, STREAK_THRESHOLD)) return true;
        // clock moved backwards: don't stall until it catches up
        if (nowMs < lastMs) return true;
        return nowMs - lastMs >= PUSH_INTERVAL_MS;
    }

    private static boolean crossed(int from, int to, int mark) {
        return mark > 0 && from < mark && to >= mark;
    }

    /**
     * Layout for a widget of {@code widthDp} x {@code heightDp} (pre-Android 12: the launcher's
     * reported size; Android 12+ uses the same thresholds as its size map). Unknown (0) sizes get
     * the default 2x2 layout.
     */
    static int layoutFor(int widthDp, int heightDp) {
        if (widthDp <= 0 || heightDp <= 0) return LAYOUT_SMALL;
        if (widthDp < NARROW_MIN_DP || heightDp < NARROW_MIN_DP) return LAYOUT_COMPACT;
        if (widthDp < SMALL_MIN_WIDTH_DP || heightDp < SMALL_MIN_HEIGHT_DP) return LAYOUT_NARROW;
        if (widthDp >= MEDIUM_MIN_WIDTH_DP && heightDp >= MEDIUM_MIN_HEIGHT_DP) return LAYOUT_MEDIUM;
        return LAYOUT_SMALL;
    }

    /**
     * Whether the small / medium layout has room for the streak footer at this height (it is the
     * first thing dropped on a short widget; text is not shrunk). Unknown (0) heights keep it.
     */
    static boolean showsFooter(int layout, int heightDp) {
        if (heightDp <= 0) return true;
        if (layout == LAYOUT_MEDIUM) return heightDp >= MEDIUM_FOOTER_MIN_HEIGHT_DP;
        if (layout == LAYOUT_SMALL) return heightDp >= SMALL_FOOTER_MIN_HEIGHT_DP;
        return false;
    }

    /** Whether the compact layout has room for the step count next to the rings. */
    static boolean compactShowsSteps(int widthDp) {
        return widthDp >= COMPACT_STEPS_MIN_WIDTH_DP;
    }
}
