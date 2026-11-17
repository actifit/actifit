package io.actifit.fitnesstracker.actifitfitnesstracker;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class WidgetStateTest {

    private static final long T0 = 1_800_000_000_000L;
    private static final int GOAL = 10000;

    // ---- progress ring

    @Test
    public void progressIsClampedToZeroAndHundred() {
        assertEquals(0, WidgetState.progressPercent(0, GOAL));
        assertEquals(0, WidgetState.progressPercent(-1, GOAL));
        assertEquals(62, WidgetState.progressPercent(6240, GOAL));
        assertEquals(100, WidgetState.progressPercent(10000, GOAL));
        assertEquals(100, WidgetState.progressPercent(25000, GOAL));
    }

    @Test
    public void progressSurvivesBadGoalAndHugeCounts() {
        assertEquals(100, WidgetState.progressPercent(10, 0));
        assertEquals(100, WidgetState.progressPercent(Integer.MAX_VALUE, 1));
    }

    // ---- distance / calorie rings (dashboard: 8 km and 500 kcal goals, step-derived estimates)

    @Test
    public void estimatesMatchTheDashboard() {
        assertEquals(7620f, WidgetState.estimateDistanceMeters(10000), 0.01f);
        assertEquals(400f, WidgetState.estimateKcal(10000), 0.01f);
        assertEquals(0f, WidgetState.estimateDistanceMeters(-5), 0f);
        assertEquals(0f, WidgetState.estimateKcal(-5), 0f);
    }

    @Test
    public void ringPercentIsClamped() {
        assertEquals(0, WidgetState.ringPercent(0f, WidgetState.DISTANCE_GOAL_M));
        assertEquals(0, WidgetState.ringPercent(-1f, WidgetState.DISTANCE_GOAL_M));
        assertEquals(0, WidgetState.ringPercent(Float.NaN, WidgetState.KCAL_GOAL));
        assertEquals(50, WidgetState.ringPercent(4000f, WidgetState.DISTANCE_GOAL_M));
        assertEquals(80, WidgetState.ringPercent(400f, WidgetState.KCAL_GOAL));
        assertEquals(100, WidgetState.ringPercent(9000f, WidgetState.DISTANCE_GOAL_M));
        assertEquals(100, WidgetState.ringPercent(1f, 0f));
    }

    @Test
    public void tenThousandEstimatedStepsFillTheRingsLikeTheDashboard() {
        int steps = 10000;
        assertEquals(95, WidgetState.ringPercent(WidgetState.estimateDistanceMeters(steps), WidgetState.DISTANCE_GOAL_M));
        assertEquals(80, WidgetState.ringPercent(WidgetState.estimateKcal(steps), WidgetState.KCAL_GOAL));
    }

    @Test
    public void cachedMetricsDateMatchesEitherFormat() {
        assertTrue(WidgetState.isToday("20261117", "٢٠٢٦١١١٧", "20261117"));
        assertTrue(WidgetState.isToday("٢٠٢٦١١١٧", "٢٠٢٦١١١٧", "20261117"));
        assertFalse(WidgetState.isToday("20261116", "20261117", "20261117"));
        assertFalse(WidgetState.isToday("", "20261117", "20261117"));
    }

    // ---- streak (dashboard rule: today counts only once it reaches 5,000)

    @Test
    public void streakSkipsTodayWhileUnderThreshold() {
        assertEquals(2, WidgetState.streak(new int[]{1200, 6000, 5000, 4999, 9000}));
    }

    @Test
    public void streakCountsTodayOnceOverThreshold() {
        assertEquals(3, WidgetState.streak(new int[]{5000, 6000, 5000, 4999, 9000}));
    }

    @Test
    public void unknownDayEndsTheStreak() {
        assertEquals(1, WidgetState.streak(new int[]{0, 7000, -1, 8000}));
        assertEquals(0, WidgetState.streak(new int[]{-1, -1}));
    }

    @Test
    public void streakOfEmptyOrNullIsZero() {
        assertEquals(0, WidgetState.streak(null));
        assertEquals(0, WidgetState.streak(new int[0]));
    }

    @Test
    public void singleQualifyingTodayIsOneDayStreak() {
        assertEquals(1, WidgetState.streak(new int[]{9000, 100}));
    }

    @Test
    public void cachedPastRunPlusTodayMatchesFullStreak() {
        int[][] cases = {
                {1200, 6000, 5000, 4999, 9000},
                {5000, 6000, 5000, 4999, 9000},
                {7000, -1, 8000},
                {100, 100},
                {5000, 5000, 5000, 5000},
        };
        for (int[] stepsBack : cases) {
            int[] past = new int[stepsBack.length - 1];
            System.arraycopy(stepsBack, 1, past, 0, past.length);
            assertEquals(WidgetState.streak(stepsBack),
                    WidgetState.streakWithToday(WidgetState.leadingRun(past), stepsBack[0]));
        }
    }

    @Test
    public void leadingRunHandlesNull() {
        assertEquals(0, WidgetState.leadingRun(null));
        assertEquals(0, WidgetState.streakWithToday(-3, 0));
    }

    // ---- stale synced data

    @Test
    public void syncFromAnotherDayIsStale() {
        assertFalse(WidgetState.isStale("20261117", "20261117"));
        assertTrue(WidgetState.isStale("20261116", "20261117"));
        assertTrue(WidgetState.isStale("", "20261117"));
        assertTrue(WidgetState.isStale(null, "20261117"));
    }

    @Test
    public void syncDateWrittenInDefaultLocaleMatchesTodayInThatLocale() {
        // TrackingManager writes the date with the default locale (e.g. Arabic-Indic digits);
        // the widget formats "today" the same way, so the two compare equal
        java.util.Locale saved = java.util.Locale.getDefault();
        try {
            java.util.Locale.setDefault(new java.util.Locale("ar"));
            java.util.Date now = new java.util.Date();
            String written = new java.text.SimpleDateFormat("yyyyMMdd").format(now);
            String today = new java.text.SimpleDateFormat("yyyyMMdd").format(now);
            assertFalse(WidgetState.isStale(written, today));
        } finally {
            java.util.Locale.setDefault(saved);
        }
    }

    // ---- live push throttle

    @Test
    public void firstPushAndNewDayAlwaysGoThrough() {
        assertTrue(WidgetState.shouldPush(T0, 0, 0, 10, GOAL, false));
        assertTrue(WidgetState.shouldPush(T0 + 1000, T0, 10, 10, GOAL, true));
    }

    @Test
    public void pushesAreThrottledToOnePerMinute() {
        assertFalse(WidgetState.shouldPush(T0 + 59_999, T0, 100, 180, GOAL, false));
        assertTrue(WidgetState.shouldPush(T0 + 60_000, T0, 100, 180, GOAL, false));
    }

    @Test
    public void unchangedCountIsNeverPushed() {
        assertFalse(WidgetState.shouldPush(T0 + 600_000, T0, 100, 100, GOAL, false));
    }

    @Test
    public void crossingGoalOrFiveThousandPushesImmediately() {
        assertTrue(WidgetState.shouldPush(T0 + 1000, T0, 4999, 5000, GOAL, false));
        assertTrue(WidgetState.shouldPush(T0 + 1000, T0, 9990, 10001, GOAL, false));
        assertTrue(WidgetState.shouldPush(T0 + 1000, T0, 2990, 3000, 3000, false));
        // already past both marks: throttled again
        assertFalse(WidgetState.shouldPush(T0 + 1000, T0, 10001, 10050, GOAL, false));
        assertFalse(WidgetState.shouldPush(T0 + 1000, T0, 5000, 5001, GOAL, false));
    }

    @Test
    public void resetOrClockJumpBackPushesImmediately() {
        assertTrue(WidgetState.shouldPush(T0 + 1000, T0, 8000, 3, GOAL, false));
        assertTrue(WidgetState.shouldPush(T0 - 5000, T0, 100, 120, GOAL, false));
    }

    // ---- pre-Android 12 layout choice

    @Test
    public void mediumLayoutFromWidth() {
        assertFalse(WidgetState.useMediumLayout(0));
        assertFalse(WidgetState.useMediumLayout(110));
        assertFalse(WidgetState.useMediumLayout(179));
        assertTrue(WidgetState.useMediumLayout(180));
        assertTrue(WidgetState.useMediumLayout(320));
    }
}
