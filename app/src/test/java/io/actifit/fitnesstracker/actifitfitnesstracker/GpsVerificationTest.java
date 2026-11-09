package io.actifit.fitnesstracker.actifitfitnesstracker;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

public class GpsVerificationTest {

    private static final long MIN = 60_000L;
    private static final long LONG_ENOUGH = 30 * MIN;
    private static final TimeZone UTC = TimeZone.getTimeZone("UTC");
    private static final GpsVerification.Status VERIFIED = GpsVerification.Status.VERIFIED;
    private static final GpsVerification.Status MISMATCH = GpsVerification.Status.MISMATCH;
    private static final GpsVerification.Status NOT_ENOUGH = GpsVerification.Status.NOT_ENOUGH_DATA;
    private static final GpsVerification.Status NOT_APPLICABLE = GpsVerification.Status.NOT_APPLICABLE;

    /** UTC epoch ms for 2026-11-09 (day offset) hh:mm:ss. */
    private static long at(int dayOffset, int h, int m, int s) {
        Calendar c = Calendar.getInstance(UTC);
        c.clear();
        c.set(2026, Calendar.NOVEMBER, 9 + dayOffset, h, m, s);
        return c.getTimeInMillis();
    }

    private static Map<String, List<ActivitySlot>> day(String key, ActivitySlot... slots) {
        Map<String, List<ActivitySlot>> map = new HashMap<>();
        map.put(key, Arrays.asList(slots));
        return map;
    }

    // ── evaluate ───────────────────────────────────────────────────────────

    @Test
    public void normalWalkAndRunAreVerified() {
        assertEquals(VERIFIED, GpsVerification.evaluate(3200, 4300, LONG_ENOUGH)); // 0.74 m
        assertEquals(VERIFIED, GpsVerification.evaluate(5000, 4000, LONG_ENOUGH)); // 1.25 m
    }

    @Test
    public void bandBoundsAreInclusive() {
        assertEquals(VERIFIED, GpsVerification.evaluate(800, 2000, LONG_ENOUGH)); // 0.4
        assertEquals(VERIFIED, GpsVerification.evaluate(1600, 1000, LONG_ENOUGH)); // 1.6
        assertEquals(MISMATCH, GpsVerification.evaluate(780, 2000, LONG_ENOUGH)); // 0.39
        assertEquals(MISMATCH, GpsVerification.evaluate(1700, 1000, LONG_ENOUGH)); // 1.7
    }

    @Test
    public void shakingInPlaceIsMismatch() {
        assertEquals(MISMATCH, GpsVerification.evaluate(600, 5000, LONG_ENOUGH)); // 0.12 m
    }

    @Test
    public void shortOrSmallRoutesAreNotEnoughData() {
        assertEquals(NOT_ENOUGH, GpsVerification.evaluate(499, 700, LONG_ENOUGH));
        assertEquals(NOT_ENOUGH, GpsVerification.evaluate(900, 299, LONG_ENOUGH));
        assertEquals(NOT_ENOUGH, GpsVerification.evaluate(0, 0, LONG_ENOUGH));
        assertEquals(VERIFIED, GpsVerification.evaluate(500, 600, LONG_ENOUGH)); // minimums qualify
    }

    @Test
    public void routesUnderFifteenMinutesGetNoVerdict() {
        assertEquals(NOT_ENOUGH, GpsVerification.evaluate(3200, 4300, 15 * MIN - 1));
        assertEquals(NOT_ENOUGH, GpsVerification.evaluate(10000, 0, 14 * MIN)); // even a drive
        assertEquals(VERIFIED, GpsVerification.evaluate(3200, 4300, 15 * MIN));
    }

    @Test
    public void driveRuleLongDistanceWithAlmostNoStepsIsMismatch() {
        assertEquals(MISMATCH, GpsVerification.evaluate(1000, 0, LONG_ENOUGH));
        assertEquals(MISMATCH, GpsVerification.evaluate(10000, 120, LONG_ENOUGH));
        assertEquals(MISMATCH, GpsVerification.evaluate(1000, 299, LONG_ENOUGH));
        // under 1 km with few steps stays "not enough data"
        assertEquals(NOT_ENOUGH, GpsVerification.evaluate(999, 0, LONG_ENOUGH));
    }

    @Test
    public void stepBasedActivityTypes() {
        assertTrue(GpsVerification.isStepBased("Walking"));
        assertTrue(GpsVerification.isStepBased("running"));
        assertTrue(GpsVerification.isStepBased(null));
        assertFalse(GpsVerification.isStepBased("Cycling"));
        assertFalse(GpsVerification.isStepBased("Kayaking"));
    }

    // ── slot weighting ─────────────────────────────────────────────────────

    @Test
    public void partialSlotsAreWeightedByOverlap() {
        List<ActivitySlot> slots = Arrays.asList(
                new ActivitySlot("0945", 1500), // before the route
                new ActivitySlot("1000", 1500), // 10 of 15 min covered -> 1000
                new ActivitySlot("1015", 1500), // fully covered -> 1500
                new ActivitySlot("1030", 1500), // 3 of 15 min covered -> 300
                new ActivitySlot("1045", 1500)); // after
        assertEquals(2800, GpsVerification.stepsInWindow(slots, 10 * 60 + 5, 10 * 60 + 33), 1e-9);
    }

    @Test
    public void stepsInWindowIgnoresBadSlotsAndEmptyWindows() {
        List<ActivitySlot> slots = Arrays.asList(
                new ActivitySlot("945", 150),  // unpadded = 09:45
                new ActivitySlot("bad", 999),
                new ActivitySlot("1200", -5));
        assertEquals(10, GpsVerification.stepsInWindow(slots, 9 * 60 + 59, 10 * 60), 1e-9);
        assertEquals(0, GpsVerification.stepsInWindow(slots, 12 * 60, 12 * 60 + 10), 1e-9);
        assertEquals(0, GpsVerification.stepsInWindow(null, 0, 100), 1e-9);
        assertEquals(0, GpsVerification.stepsInWindow(new ArrayList<>(), 0, 100), 1e-9);
        assertEquals(0, GpsVerification.stepsInWindow(slots, 100, 50), 1e-9);
    }

    @Test
    public void routeEndIsExclusive() {
        Map<String, List<ActivitySlot>> slots = day("20261109",
                new ActivitySlot("1015", 1500),
                new ActivitySlot("1030", 900));
        // ending exactly at 10:30:00 takes nothing from the 10:30 slot
        assertEquals(1500, GpsVerification.stepsForRoute(
                at(0, 10, 15, 0), at(0, 10, 30, 0), slots, UTC));
        // ending at 10:30:20 takes 20 s of 15 min of it: 900 * (1/45) = 20
        assertEquals(1520, GpsVerification.stepsForRoute(
                at(0, 10, 15, 0), at(0, 10, 30, 20), slots, UTC));
    }

    @Test
    public void midnightIsSplitAcrossBothDays() {
        Map<String, List<ActivitySlot>> slots = new HashMap<>();
        slots.put("20261109", Arrays.asList(new ActivitySlot("2345", 1500)));
        slots.put("20261110", Arrays.asList(new ActivitySlot("0", 1500), new ActivitySlot("15", 1500)));
        // 23:50 -> 00:20 : 10/15 of 23:45, all of 00:00, 5/15 of 00:15
        assertEquals(1000 + 1500 + 500, GpsVerification.stepsForRoute(
                at(0, 23, 50, 0), at(1, 0, 20, 0), slots, UTC));
        assertEquals(Arrays.asList("20261109", "20261110"),
                GpsVerification.dayKeys(at(0, 23, 50, 0), at(1, 0, 20, 0), UTC));
        // ending exactly at midnight stays on one day and takes the whole last slot
        assertEquals(Arrays.asList("20261109"),
                GpsVerification.dayKeys(at(0, 23, 30, 0), at(1, 0, 0, 0), UTC));
        assertEquals(1500, GpsVerification.stepsForRoute(
                at(0, 23, 45, 0), at(1, 0, 0, 0), slots, UTC));
        // more than two days is rejected
        assertEquals(-1, GpsVerification.stepsForRoute(
                at(0, 23, 0, 0), at(2, 1, 0, 0), slots, UTC));
    }

    // ── evaluateRoute ──────────────────────────────────────────────────────

    @Test
    public void evaluateRouteVerifiesAWalk() {
        Map<String, List<ActivitySlot>> slots = day("20261109",
                new ActivitySlot("1000", 1500), new ActivitySlot("1015", 1500));
        GpsVerification.Result r = GpsVerification.evaluateRoute("Walking", RouteModel.SOURCE_GPS,
                at(0, 10, 0, 0), at(0, 10, 30, 0), 2200, slots, UTC);
        assertEquals(VERIFIED, r.status);
        assertEquals(3000, r.steps);
        assertEquals(30, r.routeMinutes);
    }

    @Test
    public void evaluateRouteFlagsADriveWithAStillPhone() {
        Map<String, List<ActivitySlot>> slots = day("20261109", new ActivitySlot("1000", 40));
        GpsVerification.Result r = GpsVerification.evaluateRoute("Walking", RouteModel.SOURCE_GPS,
                at(0, 10, 0, 0), at(0, 10, 20, 0), 12000, slots, UTC);
        assertEquals(MISMATCH, r.status);
    }

    @Test
    public void evaluateRouteSkipsNonStepActivitiesAndNonGpsSources() {
        Map<String, List<ActivitySlot>> slots = day("20261109", new ActivitySlot("1000", 40));
        assertEquals(NOT_APPLICABLE, GpsVerification.evaluateRoute("Cycling", RouteModel.SOURCE_GPS,
                at(0, 10, 0, 0), at(0, 11, 0, 0), 20000, slots, UTC).status);
        assertEquals(NOT_APPLICABLE, GpsVerification.evaluateRoute("Walking",
                RouteModel.SOURCE_HEALTH_CONNECT, at(0, 10, 0, 0), at(0, 11, 0, 0), 3000, slots, UTC).status);
    }

    // ── HC staleness + post line ───────────────────────────────────────────

    @Test
    public void hcMismatchIsSuppressedUntilSyncedWellPastRouteEnd() {
        long end = at(0, 10, 30, 0);
        assertEquals(NOT_ENOUGH, GpsVerification.suppressStaleHcMismatch(MISMATCH, true, end, end + 5 * MIN));
        assertEquals(MISMATCH, GpsVerification.suppressStaleHcMismatch(MISMATCH, true, end, end + 30 * MIN));
        assertEquals(MISMATCH, GpsVerification.suppressStaleHcMismatch(MISMATCH, false, end, 0));
        assertEquals(VERIFIED, GpsVerification.suppressStaleHcMismatch(VERIFIED, true, end, 0));
    }

    @Test
    public void postBodyLineIsEnglishWithDotDecimal() {
        assertEquals("📍 GPS-verified route: 3.20 km", GpsVerification.postBodyLine(3200, true));
        assertEquals("📍 GPS-verified route: 750 m", GpsVerification.postBodyLine(750, true));
        assertEquals("📍 GPS-verified route: 2.00 mi", GpsVerification.postBodyLine(3218.688, false));
    }
}
