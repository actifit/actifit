package io.actifit.fitnesstracker.actifitfitnesstracker;

/**
 * Pure decision logic for the home-screen steps widget (no Android imports, unit tested).
 */
final class WidgetState {

    /** A day counts towards the streak at this many steps (same threshold as the dashboard). */
    static final int STREAK_THRESHOLD = 5000;

    /** Device-sensor pushes are throttled to at most one per this interval. */
    static final long PUSH_INTERVAL_MS = 60_000L;

    /** At this launcher-reported width (dp) and above the medium layout is used (pre-Android 12). */
    static final int MEDIUM_MIN_WIDTH_DP = 180;

    private WidgetState() {
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

    /** Pre-Android 12 layout choice from the launcher's reported minimum width. */
    static boolean useMediumLayout(int minWidthDp) {
        return minWidthDp >= MEDIUM_MIN_WIDTH_DP;
    }
}
